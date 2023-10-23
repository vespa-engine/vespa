// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Generates URLs to various views in the Console. Prefer to create new methods and return
 * String instead of URI to make it easier to track which views are linked from where.
 *
 * @author freva
 */
public class ConsoleUrls {
    private final String root;
    public ConsoleUrls(URI root) {
        this.root = root.toString().replaceFirst("/$", ""); // Remove trailing slash
    }

    public String root() {
        return root;
    }

    public String tenantOverview(TenantName tenantName) {
        return "%s/tenant/%s".formatted(root, tenantName.value());
    }

    /** Returns URL to notification settings view for the given tenant */
    public String tenantNotifications(TenantName tenantName) {
        return "%s/tenant/%s/account/notifications".formatted(root, tenantName.value());
    }

    public String prodApplicationOverview(TenantName tenantName, ApplicationName applicationName) {
        return "%s/tenant/%s/application/%s/prod/instance".formatted(root, tenantName.value(), applicationName.value());
    }

    public String instanceOverview(ApplicationId application, Environment environment) {
        return "%s/tenant/%s/application/%s/%s/instance/%s".formatted(root,
                application.tenant().value(),
                application.application().value(),
                environment.isManuallyDeployed() ? environment.value() : "prod",
                application.instance().value());
    }

    public String clusterOverview(ApplicationId application, ZoneId zone, ClusterSpec.Id clusterId) {
        return cluster(application, zone, clusterId, null);
    }

    public String clusterReindexing(ApplicationId application, ZoneId zone, ClusterSpec.Id clusterId) {
        return cluster(application, zone, clusterId, "reindexing");
    }

    public String deploymentRun(RunId id) {
        return "%s/job/%s/run/%s".formatted(
                instanceOverview(id.application(), id.type().environment()), id.type().jobName(), id.number());
    }

    /** Returns URL used to request support from the Vespa team. */
    public String support() {
        return root + "/support";
    }

    /** Returns URL to verify an email address with the given verification code */
    public String verifyEmail(String verifyCode) {
        return "%s/verify?%s".formatted(root, queryParam("code", verifyCode));
    }

    public String termsOfService() { return root + "/terms-of-service-trial.html"; }

    private String cluster(ApplicationId application, ZoneId zone, ClusterSpec.Id clusterId, String viewOrNull) {
        return instanceOverview(application, zone.environment()) + '?' +
                queryParam("%s.%s.%s".formatted(application.instance().value(), zone.environment().value(), zone.region().value()),
                        "clusters," + clusterId.value() + (viewOrNull == null ? "" : '=' + viewOrNull));
    }

    private static String queryParam(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + '=' + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
