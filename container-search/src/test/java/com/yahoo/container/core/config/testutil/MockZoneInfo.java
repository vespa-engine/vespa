// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config.testutil;

import ai.vespa.cloud.ZoneInfo;

/**
 * A ZoneInfo subclass which can be created (for injection) with an emopty constructor
 *
 * @author bratseth
 */
public class MockZoneInfo extends ZoneInfo {

    public MockZoneInfo() {
        super(ZoneInfo.defaultInfo().application(), ZoneInfo.defaultInfo().zone());
    }

}
