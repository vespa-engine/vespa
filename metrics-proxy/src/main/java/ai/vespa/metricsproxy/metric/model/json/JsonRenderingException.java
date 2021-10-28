// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

/**
 * An unchecked exception to be thrown upon errors in json rendering.
 *
 * @author gjoranv
 */
public class JsonRenderingException extends RuntimeException {

    JsonRenderingException(String message, Throwable cause) {
        super(message, cause);
    }

}
