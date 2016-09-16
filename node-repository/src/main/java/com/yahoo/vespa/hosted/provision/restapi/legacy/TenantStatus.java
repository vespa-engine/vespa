// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

import java.util.List;

/**
 * Value class used to convert to/from JSON.
 *
 * @author Oyvind Gronnesby
 */
class TenantStatus {

    public String tenantId;
    public int allocated;
    public int reserved;
    public List<ApplicationUsage> applications;

    public static class ApplicationUsage {
        public String application;
        public String instance;
        public int usage;

        public static ApplicationUsage create(String applicationId, String instanceId, int usage) {
            ApplicationUsage appUsage = new ApplicationUsage();
            appUsage.application = applicationId;
            appUsage.instance = instanceId;
            appUsage.usage = usage;
            return appUsage;
        }
    }
}
