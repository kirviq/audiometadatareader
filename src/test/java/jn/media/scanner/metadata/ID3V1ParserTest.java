package jn.media.scanner.metadata;

import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ID3V1ParserTest {

	private ID3V1Parser parser = new ID3V1Parser();

	@Test
	public void testSearchAndRead() throws Exception {
		ID3V1 tag = parser.searchAndRead(ByteBuffer.wrap(ByteStreams.toByteArray(this.getClass().getResourceAsStream("/silence.mp3"))));
		assertThat(tag.album, is("album"));
		assertThat(tag.artist, is("\u00c4rtist"));
		assertThat(tag.comment, is("comment"));
		assertThat(tag.title, is("title"));
		assertThat(tag.track, is(7));
		assertThat(tag.year, is(1234));
		assertThat(tag.genre, is(ID3V1Genre._02));
	}
}