// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.defaults;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author arnej27959
 * @author bratseth
 */
public class DefaultsTestCase {

    @Test
    public void testUnderVespaHome() {
        assertEquals("/opt/yahoo/vespa/my/relative/path", Defaults.getDefaults().underVespaHome("my/relative/path"));
        assertEquals("/my/absolute/path", Defaults.getDefaults().underVespaHome("/my/absolute/path"));
        assertEquals("./my/explicit/relative/path", Defaults.getDefaults().underVespaHome("./my/explicit/relative/path"));
    }

    @Test
    public void testFindVespaUser() {
        assertEquals("yahoo", Defaults.getDefaults().vespaUser());
    }

}
