package ai.vespa.metricsproxy.http;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * @author jonmv
 */
public class MetricsJsonResponse extends HttpResponse {

    private final Consumer<OutputStream> modelWriter;

    public MetricsJsonResponse(int status, Consumer<OutputStream> modelWriter) {
        super(status);
        this.modelWriter = modelWriter;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        modelWriter.accept(outputStream);
    }

    @Override
    public long maxPendingBytes() {
        return 1 << 20;
    }

}
