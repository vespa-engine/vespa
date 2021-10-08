package ai.vespa.metricsproxy.http.yamas;

import ai.vespa.metricsproxy.metric.model.json.JacksonUtil;
import ai.vespa.metricsproxy.metric.model.json.YamasArrayJsonModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author olaa
 */
public class YamasResponse extends HttpResponse {

    private final YamasArrayJsonModel data;

    public YamasResponse(int code, YamasArrayJsonModel data) {
        super(code);
        this.data = data;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        ObjectMapper mapper = JacksonUtil.createObjectMapper();
        mapper.writeValue(outputStream, data);
    }

}
