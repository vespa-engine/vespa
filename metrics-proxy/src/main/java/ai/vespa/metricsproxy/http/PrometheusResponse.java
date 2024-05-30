package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.metric.model.prometheus.PrometheusModel;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * @author jonmv
 */
public class PrometheusResponse extends HttpResponse {

    private final PrometheusModel model;

    public PrometheusResponse(int status, PrometheusModel model) {
        super(status);
        this.model = model;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        Writer writer = new OutputStreamWriter(outputStream);
        model.serialize(writer);
        writer.flush();
    }

    @Override
    public long maxPendingBytes() {
        return 1 << 20;
    }

}
