package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.metrics.ClusterInfo;
import com.yahoo.vespa.config.server.metrics.ProtonMetricsAggregator;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class ProtonMetricsResponse extends HttpResponse {

    private final Slime slime = new Slime();

    /**
     * @author akvalsvik
     */
    public ProtonMetricsResponse(int status, ApplicationId applicationId, List<ProtonMetricsAggregator> aggregatedProtonMetrics) {
        super(status);

        Cursor application = slime.setObject();
        application.setString("applicationId", applicationId.serializedForm());
        ProtonMetricsAggregator finalAggregator = new ProtonMetricsAggregator();

        for (var aggregator : aggregatedProtonMetrics) {
            finalAggregator.addAll(aggregator);
        }
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        new JsonFormat(false).encode(outputStream, slime);
    }

    @Override
    public String getContentType() { return HttpConfigResponse.JSON_CONTENT_TYPE; }
}
