package com.yahoo.vespa.hadoop.mapreduce.util;

import com.yahoo.vespa.http.client.config.FeedParams;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public class VespaConfiguration {

    public static final String ENDPOINT = "vespa.feed.endpoint";
    public static final String PROXY_HOST = "vespa.feed.proxy.host";
    public static final String PROXY_PORT = "vespa.feed.proxy.port";
    public static final String DRYRUN = "vespa.feed.dryrun";
    public static final String USE_COMPRESSION = "vespa.feed.usecompression";
    public static final String DATA_FORMAT = "vespa.feed.data.format";
    public static final String PROGRESS_REPORT = "vespa.feed.progress.interval";
    public static final String V3_PROTOCOL = "vespa.feed.v3.protocol";
    public static final String CONNECTIONS = "vespa.feed.connections";
    public static final String THROTTLER_MIN_SIZE = "vespa.feed.throttler.min.size";
    public static final String QUERY_CONNECTION_TIMEOUT = "vespa.query.connection.timeout";
    public static final String ROUTE = "vespa.feed.route";

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


    public boolean useV3Protocol() {
        return getBoolean(V3_PROTOCOL, false);
    }


    public int numConnections() {
        return getInt(CONNECTIONS, 8);
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


    private String getString(String name) {
        if (override != null && override.containsKey(name)) {
            return override.getProperty(name);
        }
        return conf != null ? conf.get(name) : null;
    }


    private int getInt(String name, int defaultValue) {
        if (override != null && override.containsKey(name)) {
            return Integer.parseInt(override.getProperty(name));
        }
        return conf != null ? conf.getInt(name, defaultValue) : defaultValue;
    }


    private boolean getBoolean(String name, boolean defaultValue) {
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

}
