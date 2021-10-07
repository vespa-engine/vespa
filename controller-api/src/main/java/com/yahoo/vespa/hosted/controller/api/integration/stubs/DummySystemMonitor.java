// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
