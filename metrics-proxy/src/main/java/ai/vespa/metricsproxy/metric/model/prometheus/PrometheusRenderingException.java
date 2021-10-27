// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

/**
 * @author gjoranv
 */
public class PrometheusRenderingException extends RuntimeException {

    PrometheusRenderingException(String message, Throwable cause) {
        super(message, cause);
    }

}
