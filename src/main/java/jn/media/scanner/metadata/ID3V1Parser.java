package jn.media.scanner.metadata;

import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

@Slf4j
class ID3V1Parser {
	ID3V1Parser() {
	}

	ID3V1 searchAndRead(ByteBuffer buffer) {
		if (buffer.remaining() < 128) {
			return null;
		}
		buffer.position(buffer.position() + buffer.remaining() - 128);

		if (buffer.get() != 'T' || buffer.get() != 'A' || buffer.get() != 'G') {
			return null;
		}
		ID3V1 id3V1 = new ID3V1();
		id3V1.title = readString(buffer, 30);
		id3V1.artist = readString(buffer, 30);
		id3V1.album = readString(buffer, 30);
		String year = readString(buffer, 4);
		if (!year.matches("\\s*")) {
			try {
				id3V1.year = Integer.parseInt(year.trim());
			} catch (NumberFormatException e) {
				log.error("invalid year encountered: {}", year);
			}
		}
		id3V1.comment = readString(buffer, 30);
		if (id3V1.comment.length() < 29) {
			id3V1.track = buffer.get(buffer.position() - 1);
		}
		id3V1.genre = ID3V1Genre.byId(buffer.get());
		return id3V1;
	}

	private static String readString(ByteBuffer buffer, int max) {
		byte[] bytes = new byte[max];
		int lastNonBlank = -1;

		for (int i = 0; i < max; i++) {
			bytes[i] = buffer.get();
			if (bytes[i] == 0) {
				// fast forward to end
				buffer.position(buffer.position() + max - i - 1);
				break;
			}
			if (bytes[i] != 32) {
				lastNonBlank = i;
			}
		}
		return lastNonBlank > 0 ? new String(bytes, 0, lastNonBlank + 1, Charsets.ISO_8859_1) : "";
	}

}
