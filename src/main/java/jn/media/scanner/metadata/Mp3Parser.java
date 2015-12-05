package jn.media.scanner.metadata;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

@Slf4j
public class Mp3Parser {
	private final ID3V1Parser v1Parser = new ID3V1Parser();
	private final ID3V2Parser v2Parser = new ID3V2Parser();

	public Metadata read(File f) throws IOException {
		Metadata meta = new Metadata();
		meta.length = f.length();
		if (f.getName().matches("(?i).*\\.mp3")) {
			try (FileChannel fc = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
				ByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
				meta.id3V1 = v1Parser.searchAndRead(buffer);
				buffer.position(0);
				meta.id3V2 = v2Parser.searchAndRead(buffer);
			}
		}
		return meta;
	}

}
