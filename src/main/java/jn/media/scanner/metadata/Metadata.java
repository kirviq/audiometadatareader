package jn.media.scanner.metadata;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Metadata {
	long length;
	ID3V1 id3V1;
	ID3V2 id3V2;

}
