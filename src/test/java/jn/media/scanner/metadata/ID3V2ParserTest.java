package jn.media.scanner.metadata;

import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ID3V2ParserTest {

	private ID3V2Parser parser = new ID3V2Parser();

	@Test
	public void testSearchAndRead() throws Exception {
		ID3V2 id3V2 = parser.searchAndRead(ByteBuffer.wrap(ByteStreams.toByteArray(this.getClass().getResourceAsStream("/silence.mp3"))));
		assertThat(id3V2.album, is("album"));
		assertThat(id3V2.title, is("title"));
		assertThat(id3V2.albumArtist, is("album artist"));
		assertThat(id3V2.artist, is("\u00c4rtist"));
		assertThat(id3V2.comment, is("comment"));
		assertThat(id3V2.composer, is("componist"));
		assertThat(id3V2.disc, is(1));
		assertThat(id3V2.year, is(1234));
		assertThat(id3V2.discsInSet, is(2));
		assertThat(id3V2.track, is(7));
		assertThat(id3V2.tracksOnDisc, is(9));
		assertThat(id3V2.genre, is("Country"));
		assertThat(id3V2.albumArtist, is("album artist"));
		assertThat(id3V2.originalArtist, is("original artist"));
		assertThat(id3V2.copyright, is("\u00a9 me"));
		assertThat(id3V2.url, is("http://example.test"));
	}
}