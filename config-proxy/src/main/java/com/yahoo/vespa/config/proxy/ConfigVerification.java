// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool to verify that configs across multiple config servers are the same.
 *
 * @author Ulf Lilleengen
 */
public class ConfigVerification {

    private final static int port = 19071;
    private final static String prefix = "http://";

    public static void main(String [] args) throws IOException {
        List<String> configservers = new ArrayList<>();
        String tenant = "default";
        String appName = "default";
        String environment = "prod";
        String region = "default";
        String instance= "default";
        for (String arg : args) {
            configservers.add(prefix + arg + ":" + port + "/config/v2/tenant/" + tenant + "/application/" + appName + "/environment/" + environment + "/region/" + region + "/instance/" + instance + "/?recursive=true");
        }
        try (CloseableHttpClient httpClient = VespaHttpClientBuilder.custom().buildClient()) {
            System.exit(compareConfigs(listConfigs(configservers, httpClient), httpClient));
        }
    }

    private static Map<String, Deque<String>> listConfigs(List<String> urls, CloseableHttpClient httpClient) throws IOException {
        Map<String, String> outputs = performRequests(urls, httpClient);

        Map<String, Deque<String>> recurseMappings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : outputs.entrySet()) {
            Slime slime = SlimeUtils.jsonToSlime(entry.getValue());
            final List<String> list = new ArrayList<>();
            slime.get().field("configs").traverse((ArrayTraverser) (idx, inspector) -> list.add(inspector.asString()));
            Collections.sort(list);
            Deque<String> stack = new ArrayDeque<>(list);
            recurseMappings.put(entry.getKey(), stack);
        }
        return recurseMappings;
    }

    private static Map<String, String> performRequests(List<String> urls, CloseableHttpClient httpClient) throws IOException {
        Map<String, String> outputs = new LinkedHashMap<>();
        for (String url : urls) {
            outputs.put(url, performRequest(url, httpClient));
        }
        return outputs;
    }

    private static int compareConfigs(Map<String, Deque<String>> mappings, CloseableHttpClient httpClient) throws IOException {
        for (int n = 0; n < mappings.values().iterator().next().size(); n++) {
            List<String> recurseUrls = new ArrayList<>();
            for (Map.Entry<String, Deque<String>> entry : mappings.entrySet()) {
                recurseUrls.add(entry.getValue().pop());
            }
            if ( ! equalOutputs(performRequests(recurseUrls, httpClient)))
                return -1;
        }
        return 0;
    }

    private static boolean equalOutputs(Map<String, String> outputs) {
        Map.Entry<String, String> firstEntry = outputs.entrySet().iterator().next();
        for (Map.Entry<String, String> entry : outputs.entrySet()) {
            if (!entry.getValue().equals(firstEntry.getValue())) {
                System.out.println("output from '" + entry.getKey() + "': '" + entry.getValue() +
                                   "' did not equal output from '" + firstEntry.getKey() + "': '" + firstEntry.getValue() + "'");
                return false;
            }
        }
        return true;
    }

    private static String performRequest(String url, CloseableHttpClient httpClient) throws IOException {
        return httpClient.execute(new HttpGet(url), new BasicHttpClientResponseHandler());
    }
}
