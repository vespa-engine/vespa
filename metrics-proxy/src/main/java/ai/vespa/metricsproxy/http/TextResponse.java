package ai.vespa.metricsproxy.http;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class TextResponse extends HttpResponse {
  private final byte[] data;

  TextResponse(int code, String data) {
      super(code);
      this.data = data.getBytes(Charset.forName(DEFAULT_CHARACTER_ENCODING));
  }

  @Override
  public void render(OutputStream outputStream) throws IOException {
      outputStream.write(data);
  }
}
