/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * An exception to be thrown upon errors in json rendering, and that can deliver its message wrapped
 * in an "error" json object.
 *
 * @author gjoranv
 */
public class JsonRenderingException extends RuntimeException {

    private static Logger log = Logger.getLogger(JsonRenderingException.class.getName());

    JsonRenderingException(String message) {
        super(message);
    }

    /**
     * Returns the message wrapped in an "error" json object. In the unlikely case that json rendering of the
     * error message fails, a plain text string will be returned instead.
     */
    public String getMessageAsJson() {
        return wrap(getMessage());
    }

    private static String wrap(String message) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException e) {
            log.log(WARNING, "Could not encode error message to json:", e);
            return "Could not encode error message to json, check the log for details.";
        }
    }

}
