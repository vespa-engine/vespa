// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class VespaHttpClient {

    private final HttpClient httpClient;

    public VespaHttpClient() {
        this(null);
    }

    public VespaHttpClient(VespaConfiguration configuration) {
        httpClient = createClient(configuration);
    }

    public String get(String url) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        HttpResponse httpResponse = httpClient.execute(httpGet);

        HttpEntity entity = httpResponse.getEntity();
        InputStream is = entity.getContent();

        String result = "";
        Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
        if (scanner.hasNext()) {
            result = scanner.next();
        }
        EntityUtils.consume(entity);

        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            return null;
        }

        return result;
    }

    public JsonNode parseResultJson(String json, String rootNode) throws IOException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        if (rootNode == null || rootNode.isEmpty()) {
            return null;
        }

        ObjectMapper m = new ObjectMapper();
        JsonNode node = m.readTree(json);
        if (node != null) {
            String[] path = rootNode.split("/");
            for (String p : path) {
                node = node.get(p);

                if (node == null) {
                    return null;
                }

                // if node is an array, return the first node that has the correct path
                if (node.isArray()) {
                    for (int i = 0; i < node.size(); ++i) {
                        JsonNode n = node.get(i);
                        if (n.has(p)) {
                            node = n;
                            break;
                        }
                    }
                }

            }
        }
        return node;
    }

    private HttpClient createClient(VespaConfiguration configuration) {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        if (configuration != null) {
            requestConfigBuilder.setSocketTimeout(configuration.queryConnectionTimeout());
            requestConfigBuilder.setConnectTimeout(configuration.queryConnectionTimeout());
            if (configuration.proxyHost() != null) {
                requestConfigBuilder.setProxy(new HttpHost(configuration.proxyHost(), configuration.proxyPort()));
            }
        }
        clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
        return clientBuilder.build();
    }

}
