package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.organization.SystemMonitor;

/**
 * @author jonmv
 */
public class DummySystemMonitor implements SystemMonitor {

    @Override
    public void reportSystemVersion(Version systemVersion, Confidence confidence) { }

}
