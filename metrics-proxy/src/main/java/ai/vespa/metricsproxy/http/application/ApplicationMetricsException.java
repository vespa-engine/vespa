/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

/**
 * @author gjoranv
 */
class ApplicationMetricsException extends RuntimeException {

    ApplicationMetricsException(String message, Throwable cause) {
        super(message, cause);
    }

}
