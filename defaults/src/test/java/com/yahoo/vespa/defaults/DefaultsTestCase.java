// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.defaults;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej27959
 * @author bratseth
 */
public class DefaultsTestCase {

    @Test
    public void testUnderVespaHome() {
        assertEquals("/opt/vespa/my/relative/path", Defaults.getDefaults().underVespaHome("my/relative/path"));
        assertEquals("/my/absolute/path", Defaults.getDefaults().underVespaHome("/my/absolute/path"));
        assertEquals("./my/explicit/relative/path", Defaults.getDefaults().underVespaHome("./my/explicit/relative/path"));
    }

    @Test
    public void testFindVespaUser() {
        assertEquals("vespa", Defaults.getDefaults().vespaUser());
    }

    @Test
    public void testPortsArePositive() {
        Defaults d = Defaults.getDefaults();
        assertTrue(d.vespaPortBase() > 0);
        assertTrue(d.vespaWebServicePort() > 0);
        assertTrue(d.vespaConfigServerRpcPort() > 0);
        assertTrue(d.vespaConfigServerHttpPort() > 0);
        assertTrue(d.vespaConfigProxyRpcPort() > 0);
    }

    @Test
    public void testTemporaryApplicationStorage() {
        assertEquals("/opt/vespa/var/vespa/application", Defaults.getDefaults().temporaryApplicationStorage());
    }

    @Test
    @Ignore // This is run manually for human inspection. Contains no assertions
    public void dumpAllVars() {
        Defaults d = Defaults.getDefaults();
        System.out.println("vespa user = '" + d.vespaUser() + "'");
        System.out.println("vespa hostname = '" + d.vespaHostname() + "'");
        System.out.println("vespa home = '" + d.vespaHome() + "'");
        System.out.println("underVespaHome(foo) = '" + d.underVespaHome("foo") + "'");

        System.out.println("web service port = '" + d.vespaWebServicePort() + "'");
        System.out.println("vespa port base = '" + d.vespaPortBase() + "'");
        System.out.println("config server RPC port = '" + d.vespaConfigServerRpcPort() + "'");
        System.out.println("config server HTTP port = '" + d.vespaConfigServerHttpPort() + "'");
        System.out.println("config proxy RPC port = '" + d.vespaConfigProxyRpcPort() + "'");
    }

}
