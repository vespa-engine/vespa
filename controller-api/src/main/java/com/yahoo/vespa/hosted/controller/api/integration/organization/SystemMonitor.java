package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.component.Version;

/**
 * Montitors a Vespa controller and its system.
 *
 * @author jonmv
 */
public interface SystemMonitor {

    /** Notifies the monitor of the current system version and its confidence. */
    void reportSystemVersion(Version systemVersion, Confidence confidence);

    enum Confidence {
        broken, low, normal, high;
    }

}
