// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.component.Version;

/**
 * Monitors a Vespa controller and its system.
 *
 * @author jonmv
 */
public interface SystemMonitor {

    /** Notifies the monitor of the current system version and its confidence. */
    void reportSystemVersion(Version systemVersion, Confidence confidence);

    enum Confidence {
        aborted, broken, low, legacy, normal, high;
    }

}
