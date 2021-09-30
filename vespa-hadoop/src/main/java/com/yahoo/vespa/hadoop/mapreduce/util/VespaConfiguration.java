// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce.util;

import com.yahoo.vespa.http.client.config.FeedParams;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

public class VespaConfiguration {

    public static final String ENDPOINT = "vespa.feed.endpoint";
    public static final String DEFAULT_PORT = "vespa.feed.defaultport";
    public static final String USE_SSL = "vespa.feed.ssl";
    public static final String PROXY_HOST = "vespa.feed.proxy.host";
    public static final String PROXY_PORT = "vespa.feed.proxy.port";
    public static final String DRYRUN = "vespa.feed.dryrun";
    public static final String USE_COMPRESSION = "vespa.feed.usecompression";
    public static final String DATA_FORMAT = "vespa.feed.data.format";
    public static final String PROGRESS_REPORT = "vespa.feed.progress.interval";
    public static final String CONNECTIONS = "vespa.feed.connections";
    public static final String THROTTLER_MIN_SIZE = "vespa.feed.throttler.min.size";
    public static final String QUERY_CONNECTION_TIMEOUT = "vespa.query.connection.timeout";
    public static final String ROUTE = "vespa.feed.route";
    public static final String MAX_SLEEP_TIME_MS = "vespa.feed.max.sleep.time.ms";
    public static final String MAX_IN_FLIGHT_REQUESTS = "vespa.feed.max.in.flight.requests";
    public static final String RANDOM_STARTUP_SLEEP = "vespa.feed.random.startup.sleep.ms";
    public static final String NUM_RETRIES = "vespa.feed.num.retries";
    public static final String USE_LEGACY_CLIENT = "vespa.feed.uselegacyclient";

    private final Configuration conf;
    private final Properties override;

    private VespaConfiguration(Configuration conf, Properties override) {
        this.conf = conf;
        this.override = override;
    }


    public static VespaConfiguration get(Configuration conf, Properties override) {
        return new VespaConfiguration(conf, override);
    }


    public String endpoint() {
        return getString(ENDPOINT);
    }


    public int defaultPort() {
        return getInt(DEFAULT_PORT, 4080);
    }


    public boolean useSSL() {
        return getBoolean(USE_SSL, false);
    }


    public String proxyHost() {
        return getString(PROXY_HOST);
    }


    public int proxyPort() {
        return getInt(PROXY_PORT, 4080);
    }


    public boolean dryrun() {
        return getBoolean(DRYRUN, false);
    }


    public boolean useCompression() {
        return getBoolean(USE_COMPRESSION, true);
    }


    public int numConnections() {
        return getInt(CONNECTIONS, 1);
    }


    public int throttlerMinSize() {
        return getInt(THROTTLER_MIN_SIZE, 0);
    }


    public int queryConnectionTimeout() {
        return getInt(QUERY_CONNECTION_TIMEOUT, 10000);
    }


    public String route() {
        return getString(ROUTE);
    }


    public int maxSleepTimeMs() {
        return getInt(MAX_SLEEP_TIME_MS, 10000);
    }


    public int maxInFlightRequests() {
        return getInt(MAX_IN_FLIGHT_REQUESTS, 500);
    }


    public int randomStartupSleepMs() {
        return getInt(RANDOM_STARTUP_SLEEP, 30000);
    }


    public int numRetries() {
        return getInt(NUM_RETRIES, 100);
    }


    public FeedParams.DataFormat dataFormat() {
        String format = getString(DATA_FORMAT);
        if ("xml".equalsIgnoreCase(format)) {
            return FeedParams.DataFormat.XML_UTF8;
        }
        return FeedParams.DataFormat.JSON_UTF8;
    }


    public int progressInterval() {
        return getInt(PROGRESS_REPORT, 1000);
    }

    public Optional<Boolean> useLegacyClient() {
        String raw = getString(USE_LEGACY_CLIENT);
        if (raw == null || raw.trim().isEmpty()) return Optional.empty();
        return Optional.of(Boolean.parseBoolean(raw));
    }

    public String getString(String name) {
        if (override != null && override.containsKey(name)) {
            return override.getProperty(name);
        }
        return conf != null ? conf.get(name) : null;
    }


    public int getInt(String name, int defaultValue) {
        if (override != null && override.containsKey(name)) {
            return Integer.parseInt(override.getProperty(name));
        }
        return conf != null ? conf.getInt(name, defaultValue) : defaultValue;
    }


    public boolean getBoolean(String name, boolean defaultValue) {
        if (override != null && override.containsKey(name)) {
            return Boolean.parseBoolean(override.getProperty(name));
        }
        return conf != null ? conf.getBoolean(name, defaultValue) : defaultValue;

    }

    public static Properties loadProperties(String... params) {
        Properties properties = new Properties();
        if (params != null) {
            for (String s : params) {
                try {
                    properties.load(new StringReader(s));
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return properties;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ENDPOINT + ": " + endpoint() + "\n");
        sb.append(DEFAULT_PORT + ": " + defaultPort() + "\n");
        sb.append(USE_SSL + ": " + useSSL() + "\n");
        sb.append(PROXY_HOST + ": " + proxyHost() + "\n");
        sb.append(PROXY_PORT + ": " + proxyPort() + "\n");
        sb.append(DRYRUN + ": " +  dryrun() +"\n");
        sb.append(USE_COMPRESSION + ": " +  useCompression() +"\n");
        sb.append(DATA_FORMAT + ": " +  dataFormat() +"\n");
        sb.append(PROGRESS_REPORT + ": " +  progressInterval() +"\n");
        sb.append(CONNECTIONS + ": " +  numConnections() +"\n");
        sb.append(THROTTLER_MIN_SIZE + ": " +  throttlerMinSize() +"\n");
        sb.append(QUERY_CONNECTION_TIMEOUT + ": " +  queryConnectionTimeout() +"\n");
        sb.append(ROUTE + ": " +  route() +"\n");
        sb.append(MAX_SLEEP_TIME_MS + ": " +  maxSleepTimeMs() +"\n");
        sb.append(MAX_IN_FLIGHT_REQUESTS + ": " +  maxInFlightRequests() +"\n");
        sb.append(RANDOM_STARTUP_SLEEP + ": " +  randomStartupSleepMs() +"\n");
        sb.append(NUM_RETRIES + ": " +  numRetries() +"\n");
        sb.append(USE_LEGACY_CLIENT + ": " +  useLegacyClient().map(Object::toString).orElse("<empty>") +"\n");
        return sb.toString();
    }

}
