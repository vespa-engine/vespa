// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.yamas;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


/**
 * @author olaa
 */
public class YamasResponse extends HttpResponse {

    private final List<MetricsPacket> metrics;
    private boolean useJsonl;

    public YamasResponse(int code, List<MetricsPacket> metrics, boolean useJsonl) {
        super(code);
        this.metrics = metrics;
        this.useJsonl = useJsonl;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        if (useJsonl)
            YamasJsonUtil.toJsonl(metrics, outputStream, false);
        else
            YamasJsonUtil.toJson(metrics, outputStream, false);
    }

}
