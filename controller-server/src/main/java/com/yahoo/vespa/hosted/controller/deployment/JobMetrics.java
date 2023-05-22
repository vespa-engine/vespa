// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;

import java.util.Map;

/**
 * Records metrics related to deployment jobs.
 *
 * @author jonmv
 */
public class JobMetrics {

    public static final String start = "deployment.start";
    public static final String nodeAllocationFailure = "deployment.nodeAllocationFailure";
    public static final String endpointCertificateTimeout = "deployment.endpointCertificateTimeout";
    public static final String deploymentFailure = "deployment.deploymentFailure";
    public static final String invalidApplication = "deployment.invalidApplication";
    public static final String convergenceFailure = "deployment.convergenceFailure";
    public static final String testFailure = "deployment.testFailure";
    public static final String noTests = "deployment.noTests";
    public static final String error = "deployment.error";
    public static final String abort = "deployment.abort";
    public static final String cancel = "deployment.cancel";
    public static final String success = "deployment.success";
    public static final String quotaExceeded = "deployment.quotaExceeded";

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
