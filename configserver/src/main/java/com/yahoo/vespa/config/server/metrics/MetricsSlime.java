package com.yahoo.vespa.config.server.metrics;

import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

public class MetricsSlime {

    static Slime doMetricsRequest(URI hostURI, CloseableHttpClient httpClient) throws IOException {
        HttpGet get = new HttpGet(hostURI);
        CloseableHttpResponse response = httpClient.execute(get);
        InputStream is = response.getEntity().getContent();
        Slime slime = SlimeUtils.jsonToSlime(is.readAllBytes());
        is.close();
        return slime;
    }

    static ClusterInfo getClusterInfoFromDimensions(Inspector dimensions) {
        return new ClusterInfo(dimensions.field("clusterid").asString(), dimensions.field("clustertype").asString());
    }
}
