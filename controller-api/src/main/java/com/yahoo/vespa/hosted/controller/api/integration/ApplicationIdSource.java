package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;

import java.util.List;

public interface ApplicationIdSource {

    /** Returns a list of all known application instance IDs. */
    List<ApplicationId> listApplications();

    /** Returns a list of all known application instance IDs for the given tenant. */
    List<ApplicationId> listApplications(TenantName tenant);

}
