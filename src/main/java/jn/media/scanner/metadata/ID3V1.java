package jn.media.scanner.metadata;

import lombok.Data;

@Data
public class ID3V1 {
	String artist;
	String album;
	String title;
	int track;
	int year;
	ID3V1Genre genre;
	String comment;
}
