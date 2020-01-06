// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

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
        try (CloseableHttpClient httpClient = VespaHttpClientBuilder.createWithBasicConnectionManager().build()) {
            System.exit(compareConfigs(listConfigs(configservers, httpClient), httpClient));
        }
    }

    private static Map<String, Stack<String>> listConfigs(List<String> urls, CloseableHttpClient httpClient) throws IOException {
        Map<String, String> outputs = performRequests(urls, httpClient);

        Map<String, Stack<String>> recurseMappings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : outputs.entrySet()) {
            Slime slime = new JsonDecoder().decode(new Slime(), Utf8.toBytes(entry.getValue()));
            final List<String> list = new ArrayList<>();
            slime.get().field("configs").traverse(new ArrayTraverser() {
                @Override
                public void entry(int idx, Inspector inspector) {
                    list.add(inspector.asString());
                }
            });
            Stack<String> stack = new Stack<>();
            Collections.sort(list);
            stack.addAll(list);
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

    private static int compareConfigs(Map<String, Stack<String>> mappings, CloseableHttpClient httpClient) throws IOException {
        for (int n = 0; n < mappings.values().iterator().next().size(); n++) {
            List<String> recurseUrls = new ArrayList<>();
            for (Map.Entry<String, Stack<String>> entry : mappings.entrySet()) {
                recurseUrls.add(entry.getValue().pop());
            }
            int ret = compareOutputs(performRequests(recurseUrls, httpClient));
            if (ret != 0) {
                return ret;
            }
        }
        return 0;
    }

    private static int compareOutputs(Map<String, String> outputs) {
        Map.Entry<String, String> firstEntry = outputs.entrySet().iterator().next();
        for (Map.Entry<String, String> entry : outputs.entrySet()) {
            if (!entry.getValue().equals(firstEntry.getValue())) {
                System.out.println("output from '" + entry.getKey() + "' did not equal output from '" + firstEntry.getKey() + "'");
                return -1;
            }
        }
        return 0;
    }

    private static String performRequest(String url, CloseableHttpClient httpClient) throws IOException {
        return httpClient.execute(new HttpGet(url), new BasicResponseHandler());
    }
}
