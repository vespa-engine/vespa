package ai.vespa.metricsproxy.metric.model;

import java.util.logging.Logger;

public enum ResponseFormat {
    JSON,
    PROMETHEUS;

    private static Logger logger = Logger.getLogger(ResponseFormat.class.getName());

    public static ResponseFormat getResponseFormat(String val) {
        if (val == null) {
            return JSON;
        }
        try {
            return valueOf(val.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("unknown format " + val + " requested.");
            return JSON;
        }
    }
}