package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

public interface LogStore {

    /** Returns the log of the given deployment job. */
    String getTestLog(ApplicationId applicationId, ZoneId zoneId, int buildId);

    /** Stores the given log for the given deployment job. */
    void setTestLog(ApplicationId applicationId, ZoneId zoneId, int buildId, String log);

    /** Deletes the log for the given deployment job. */
    void deleteTestLog(ApplicationId applicationId, ZoneId zoneId, int buildId);

}
