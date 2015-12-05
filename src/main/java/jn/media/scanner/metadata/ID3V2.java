package jn.media.scanner.metadata;

import lombok.Data;

import java.util.Set;

@Data
public class ID3V2 {

	int majorVersion;
	int revision;
	int length;
	Set<TagFlags> flags;
	int millis;
	String composer;
	UID uid;
	String artist;
	String albumArtist;
	String album;
	String title;
	int track;
	int tracksOnDisc;
	int disc;
	int discsInSet;
	int year;
	String genre;
	String comment;
	String originalArtist;
	String copyright;
	String url;

	@Data
	public static class UID {
		String owner;
		byte[] id;
	}

	public enum TagFlags {
		UNUSED1,
		UNUSED2,
		UNUSED3,
		UNUSED4,
		FOOTER,
		EXPERIMENTAL,
		EXT_HEADER,
		UNSYNC
	}
}
