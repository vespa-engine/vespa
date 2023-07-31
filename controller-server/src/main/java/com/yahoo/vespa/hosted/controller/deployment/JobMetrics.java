// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import ai.vespa.metrics.ConfigServerMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;

import java.util.Map;

/**
 * Records metrics related to deployment jobs.
 *
 * @author jonmv
 */
public class JobMetrics {

    public static final String start = ConfigServerMetrics.DEPLOYMENT_START.baseName();
    public static final String nodeAllocationFailure = ConfigServerMetrics.DEPLOYMENT_NODE_ALLOCATION_FAILURE.baseName();
    public static final String endpointCertificateTimeout = ConfigServerMetrics.DEPLOYMENT_ENDPOINT_CERTIFICATE_TIMEOUT.baseName();
    public static final String deploymentFailure = ConfigServerMetrics.DEPLOYMENT_DEPLOYMENT_FAILURE.baseName();
    public static final String invalidApplication = ConfigServerMetrics.DEPLOYMENT_INVALID_APPLICATION.baseName();
    public static final String convergenceFailure = ConfigServerMetrics.DEPLOYMENT_CONVERGENCE_FAILURE.baseName();
    public static final String testFailure = ConfigServerMetrics.DEPLOYMENT_TEST_FAILURE.baseName();
    public static final String noTests = ConfigServerMetrics.DEPLOYMENT_NO_TESTS.baseName();
    public static final String error = ConfigServerMetrics.DEPLOYMENT_ERROR.baseName();
    public static final String abort = ConfigServerMetrics.DEPLOYMENT_ABORT.baseName();
    public static final String cancel = ConfigServerMetrics.DEPLOYMENT_CANCEL.baseName();
    public static final String success = ConfigServerMetrics.DEPLOYMENT_SUCCESS.baseName();
    public static final String quotaExceeded = ConfigServerMetrics.DEPLOYMENT_QUOTA_EXCEEDED.baseName();

    private final Metric metric;

    public JobMetrics(Metric metric) {
        this.metric = metric;
    }

    public void jobStarted(JobId id) {
        metric.add(start, 1, metric.createContext(contextOf(id)));
    }

    public void jobFinished(JobId id, RunStatus status) {
        metric.add(valueOf(status), 1, metric.createContext(contextOf(id)));
    }

    Map<String, String> contextOf(JobId id) {
        return Map.of("applicationId", id.application().toFullString(),
                      "tenantName", id.application().tenant().value(),
                      "app", id.application().application().value() + "." + id.application().instance().value(),
                      "test", Boolean.toString(id.type().isTest()),
                      "zone", id.type().zone().value());
    }

    static String valueOf(RunStatus status) {
        return switch (status) {
            case nodeAllocationFailure -> nodeAllocationFailure;
            case endpointCertificateTimeout -> endpointCertificateTimeout;
            case invalidApplication -> invalidApplication;
            case deploymentFailed -> deploymentFailure;
            case installationFailed -> convergenceFailure;
            case testFailure -> testFailure;
            case noTests -> noTests;
            case error -> error;
            case cancelled -> cancel;
            case aborted -> abort;
            case success -> success;
            case quotaExceeded -> quotaExceeded;
            default -> throw new IllegalArgumentException("Unexpected run status '" + status + "'");
        };
    }

}
