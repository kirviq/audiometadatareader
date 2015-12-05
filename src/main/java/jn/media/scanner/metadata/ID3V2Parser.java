package jn.media.scanner.metadata;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

@Slf4j
class ID3V2Parser {
	ID3V2Parser() {
	}

	private static final int ID3V2_SEARCH_LIMIT = 10000;

	private static final Pattern TWO_NUMBERS = Pattern.compile("(\\d+)(?:/(\\d+))?");
	private static final Set<String> IGNORED_FIELDS = ImmutableSet.of("POPM", "TSSE", "TSIZ", "TCMP", "TENC", "APIC", "TFLT", "TMED");
	private static final Map<String, BiConsumer<ID3V2, byte[]>> KNOWN_FIELDS = ImmutableMap.<String, BiConsumer<ID3V2, byte[]>>builder()
			.put("COMM", (tag, bytes) -> {
				Reader r = new Reader(bytes);
				r.readInit();
				r.skip(3); // ignore language
				r.readBom();
				r.readText(); // ignore description
				r.readBom();
				tag.comment = r.readText();
				//TODO handle language and description
			})
			.put("TXXX", (tag, bytes) -> tag.comment = new Reader(bytes).normalRead())
			.put("TALB", (tag, bytes) -> tag.album = new Reader(bytes).normalRead())
			.put("TIT2", (tag, bytes) -> tag.title = new Reader(bytes).normalRead())
			.put("TPE2", (tag, bytes) -> tag.albumArtist = new Reader(bytes).normalRead())
			.put("TOPE", (tag, bytes) -> tag.originalArtist = new Reader(bytes).normalRead())
			.put("TCOP", (tag, bytes) -> tag.copyright = new Reader(bytes).normalRead())
			.put("WXXX", (tag, bytes) -> {
				Reader reader = new Reader(bytes);
				reader.normalRead(); // ignore description
				//TODO handle description
				tag.url = reader.normalRead();
			})
			.put("TRCK", (tag, bytes) -> {
				String string = new Reader(bytes).normalRead();
				Numbers nums = Numbers.fromString(string);
				if (nums != null) {
					tag.track = nums.num1;
					if (nums.num2 != null) {
						tag.tracksOnDisc = nums.num2;
					}
				} else {
					log.warn("invalid track number {}", string);
				}
			})
			.put("TYER", (tag, bytes) -> tag.year = readInt(bytes))
			.put("UFID", (tag, bytes) -> {
				int pos = -1;
				for (int i = 0; i < bytes.length; i++) if (bytes[i] == 0) {
					pos = i;
					break;
				}
				tag.uid = new ID3V2.UID();
				if (pos == -1) {
					tag.uid.owner = "";
					tag.uid.id = bytes;
				} else {
					tag.uid.owner = new String(bytes, 0, pos);
					tag.uid.id = new byte[bytes.length - pos];
					System.arraycopy(bytes, pos + 1, tag.uid.id, 0, bytes.length - pos - 1);
				}
			})
			.put("TLEN", (tag, bytes) -> tag.millis = readInt(bytes))
			.put("TDRC", (tag, bytes) -> tag.year = readInt(bytes))
			.put("TPE1", (tag, bytes) -> tag.artist = new Reader(bytes).normalRead())
			.put("TCOM", (tag, bytes) -> tag.composer = new Reader(bytes).normalRead())
			.put("TPOS", (tag, bytes) -> {
				String string = new Reader(bytes).normalRead();
				Numbers nums = Numbers.fromString(string);
				if (nums != null) {
					tag.disc = nums.num1;
					if (nums.num2 != null) {
						tag.discsInSet = nums.num2;
					}
				} else {
					log.warn("invalid disc number {}", string);
				}
			})
			.put("TCON", (tag, bytes) -> {
				String genre = new Reader(bytes).normalRead();
				if (genre.matches("\\(\\d+\\)")) {
					int id = Integer.parseInt(genre.substring(1, genre.length() - 1));
					ID3V1Genre v1Genre = ID3V1Genre.byId(id);
					if (v1Genre == null) {
						log.warn("genre {} not found", id);
						tag.genre = genre;
					} else {
						tag.genre = v1Genre.getName();
					}
				} else {
					tag.genre = genre;
				}
			})
			.build();

	private static int readInt(byte[] bytes) {
		int i;
		String string = new Reader(bytes).normalRead();
		try {
			i = string.trim().isEmpty() ? 0 : Integer.parseInt(string);
		} catch (NumberFormatException e) {
			log.warn("invalid year {}", string);
			i = 0;
		}
		return i;
	}

	private static class Reader {
		private final byte[] bytes;
		private int pos;
		private boolean needBom;
		private boolean needTwoNulls;
		private boolean endOnNextNull;
		private Charset charset;


		public Reader(byte[] bytes) {
			this.bytes = bytes;
		}
		public String normalRead() {
			if (pos >= bytes.length) {
				return "";
			}
			readInit();
			readBom();
			return readText();
		}

		void skip(int num) {
			if (bytes.length <= pos + num) {
				throw new IllegalArgumentException("expected bom but got end of string");
			}
			pos += num;
		}
		void readInit() {
			switch (bytes[pos]) {
				case 0:
					pos++;
				default: // if > 3 then this is not the mode, but the first char
					charset = Charsets.ISO_8859_1;
					needTwoNulls = false;
					endOnNextNull = true;
					needBom = false;
					break;
				case 1:
					pos++;
					needTwoNulls = true;
					endOnNextNull = false;
					needBom = true;
					break;
				case 2:
					pos++;
					charset = Charsets.UTF_16BE;
					needTwoNulls = true;
					endOnNextNull = false;
					needBom = false;
					break;
				case 3:
					pos++;
					charset = Charsets.UTF_8;
					needTwoNulls = false;
					endOnNextNull = true;
					needBom = false;
					break;
			}
		}

		private void readBom() {
			if (!needBom) return;
			if (bytes.length <= pos + 1) {
				throw new IllegalArgumentException("expected bom but got end of string");
			}
			if (bytes[pos] == -2 && bytes[pos + 1] == -1) {
				charset = Charsets.UTF_16BE;
			} else if (bytes[pos] == -1 && bytes[pos + 1] == -2) {
				charset = Charsets.UTF_16LE;
			} else {
				throw new IllegalArgumentException("invalid BOM 0x" + Integer.toHexString(bytes[pos] & 0xff) + Integer.toHexString(bytes[pos + 1] & 0xff));
			}
			pos  = pos + 2;
		}

		private String readText() {
			int start = pos;
			int end = start;
			while (pos < bytes.length) {
				byte current = bytes[pos++];
				if (current == 0) {
					if (endOnNextNull) {
						return new String(bytes, start, end - start, charset);
					} else {
						endOnNextNull = true;
					}
				} else {
					end = pos;
					endOnNextNull = !needTwoNulls;
				}
			}
			return new String(bytes, start, pos - start, charset);
		}
	}

	ID3V2 searchAndRead(ByteBuffer buffer) throws IOException {
		int searchPos = 0;
		ID3V2 tag = new ID3V2();
LOOP:
		while (buffer.hasRemaining() && buffer.position() < ID3V2_SEARCH_LIMIT) {
			switch (searchPos) {
				case 0:
					searchPos = buffer.get() == 0x49 ? 1 : 0;
					break;
				case 1:
					searchPos = buffer.get() == 0x44 ? 2 : 0;
					break;
				case 2:
					searchPos = buffer.get() == 0x33 ? 3 : 0;
					break;
				case 3:
					tag.majorVersion = buffer.get();
					searchPos = tag.majorVersion != -128 ? 4 : 0;
					break;
				case 4:
					tag.revision = buffer.get();
					searchPos = tag.revision != -128 ? 5 : 0;
					break;
				case 5:
					tag.flags = map(BitSet.valueOf(new byte[]{buffer.get()}), ID3V2.TagFlags.values());
					searchPos = 6;
					break;
				case 6:
				case 7:
				case 8:
				case 9:
					byte temp = buffer.get();
					if (temp < 0) {
						searchPos = 0;
					} else {
						tag.length = (tag.length << 7) | temp;
						searchPos++;
					}
					break;
				case 10:
					break LOOP;
				default:
					throw new IllegalStateException("tag search failed horribly");
			}
		}
		if (searchPos != 10) {
			return null;
		}
		if (tag.flags.contains(ID3V2.TagFlags.EXT_HEADER)) {
			log.warn("ignoring extended header");
			Integer extHeaderSize = readInt(buffer, 4);
			if (extHeaderSize == null) {
				log.error("illegal ext header size");
				return null;
			}
			buffer.position(buffer.position() + extHeaderSize - 4);
		}
		if (tag.flags.contains(ID3V2.TagFlags.EXPERIMENTAL)) {
			log.info("tag is flagged as experimental");
		}
		// if (tag.flags.contains(ID3V2.TagFlags.FOOTER)) -> don't care
		// if (tag.flags.contains(ID3V2.TagFlags.UNSYNC)) -> handled at frame level
		if (tag.majorVersion != 3 && tag.majorVersion != 4){
			log.warn("unknown id3 tag version encountered: {}", tag.majorVersion);
			return null;
		}
		if (buffer.remaining() < tag.length) {
			log.warn("tag extends beyond read-limit");
		}

		int frameEnd = buffer.position() + tag.length;
		while (buffer.position() < frameEnd) {
			byte[] id = new byte[] {buffer.get(), buffer.get(), buffer.get(), buffer.get()};
			if (id[0] == 0 || id[1] == 0 || id[2] == 0 || id[3] == 0) {
				// looks like we reached the padding
				break;
			}
			Integer rawLength = readInt(buffer, 4);
			if (rawLength == null) {
				log.error("invalid framesize");
				return null;
			}
			Set<FrameFlag> flags = map(BitSet.valueOf(new byte[]{buffer.get(), buffer.get()}), FrameFlag.values());
			String idString = new String(id, Charsets.ISO_8859_1);
			Integer dataLength = rawLength;

			if (IGNORED_FIELDS.contains(idString)) {
				log.debug("ignoring field {}", idString);
				buffer.position(buffer.position() + rawLength);
				continue;
			}
			BiConsumer<ID3V2, byte[]> handler = KNOWN_FIELDS.get(idString);
			if (handler != null) {
				if (flags.contains(FrameFlag.ENCRYPTION) || flags.contains(FrameFlag.GROUPING)) {
					log.warn("can't handle flags {} for frame {}", flags, idString);
					buffer.position(buffer.position() + rawLength);
					continue;
				}
				if (flags.contains(FrameFlag.DATA_LENGTH)) {
					dataLength = readInt(buffer, 4);
					if (dataLength == null) {
						log.error("illegal frame size");
						return null;
					}
					log.debug("read data length {} for frame {} (raw length {})", dataLength, idString, rawLength);
				}
				byte[] data = new byte[rawLength];
				buffer.get(data);
				if (flags.contains(FrameFlag.UNSYNC)) {
					log.debug("undoing desyncronization for frame {}", idString);
					data = deUnSync(data);
				}
				// decrypting would go here
				if (flags.contains(FrameFlag.COMPRESSION)) {
					log.debug("uncompressing frame {}", idString);
					data = uncompress(dataLength, data);
				}
				// grouping?
				handler.accept(tag, data);
			} else {
				log.info("ignoring unknown frame {}", idString);
				buffer.position(buffer.position() + rawLength);
			}
		}
		return tag;
	}

	private static byte[] uncompress(Integer dataLength, byte[] compressed) {
		byte[] deflated = new byte[dataLength];
		Deflater deflater = new Deflater();
		deflater.setInput(compressed);
		deflater.finish();
		deflater.deflate(deflated);
		deflater.end();
		return deflated;
	}

	private static byte[] deUnSync(byte[] data) {
		byte[] realBytes = new byte[data.length];
		int size = 0;
		byte last = 0;
		for (byte b : data) {
			if (last != -128 || b != 0) {
				realBytes[size] = b;
				size++;
			}
			last = b;
		}
		if (size == data.length) {
			return data;
		} else {
			return Arrays.copyOf(realBytes, size);
		}
	}

	private <T extends Enum> Set<T> map(BitSet bitSet, T[] values) {
		Collection<T> mapped = new ArrayList<>(bitSet.cardinality());
		bitSet.stream().forEach(i -> mapped.add(values[i]));
		return Sets.immutableEnumSet(mapped);
	}

	private static Integer readInt(ByteBuffer buffer, int bytes) {
		assert bytes >= 0 && bytes <= 4;

		int value = 0;
		while (bytes-- > 0) {
			value = value << 7;
			byte nextBits = buffer.get();
			if (nextBits < 0) {
				return null;
			}
			value = value | nextBits;
		}
		return value;

	}

	private static class Numbers {
		private Integer num1;
		private Integer num2;

		static Numbers fromString(String s) {
			Matcher m = TWO_NUMBERS.matcher(s);
			if (m.matches()) {
				Numbers n = new Numbers();
				n.num1 = Integer.valueOf(m.group(1));
				String of = m.group(2);
				if (of != null) {
					n.num2 = Integer.valueOf(of);
				}
				return n;
			} else {
				return null;
			}
		}
	}

	private enum FrameFlag {
		DATA_LENGTH,
		UNSYNC,
		ENCRYPTION,
		COMPRESSION,
		UNUSED4,
		UNUSED5,
		GROUPING,
		UNUSED7,
		UNUSED8,
		UNUSED9,
		UNUSED10,
		UNUSED11,
		READ_ONLY,
		FILE_ALTERPRESERVATION,
		TAG_ALTERPRESERVATION,
		UNUSED15,
	}
}
