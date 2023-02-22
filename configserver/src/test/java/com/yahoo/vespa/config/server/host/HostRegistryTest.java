// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import com.yahoo.config.provision.ApplicationId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class HostRegistryTest {

    private final ApplicationId foo = ApplicationId.from("foo", "app1", "default");
    private final ApplicationId bar = ApplicationId.from("bar", "app2", "default");

    @Test
    public void old_hosts_are_removed() {
        HostRegistry reg = new HostRegistry();
        assertNull(reg.getKeyForHost("foo.com"));
        reg.update(foo, List.of("foo.com", "bar.com", "baz.com"));
        assertGetKey(reg, "foo.com", foo);
        assertGetKey(reg, "bar.com", foo);
        assertGetKey(reg, "baz.com", foo);
        assertEquals(3, reg.getAllHosts().size());
        reg.update(foo, List.of("bar.com", "baz.com"));
        assertNull(reg.getKeyForHost("foo.com"));
        assertGetKey(reg, "bar.com", foo);
        assertGetKey(reg, "baz.com", foo);

        assertEquals(2, reg.getAllHosts().size());
        assertTrue(reg.getAllHosts().containsAll(List.of("bar.com", "baz.com")));
        reg.removeHostsForKey(foo);
        assertTrue(reg.getAllHosts().isEmpty());
        assertNull(reg.getKeyForHost("foo.com"));
        assertNull(reg.getKeyForHost("bar.com"));
    }

    @Test
    public void multiple_keys_are_handled() {
        HostRegistry reg = new HostRegistry();
        reg.update(foo, List.of("foo.com", "bar.com"));
        reg.update(bar, List.of("baz.com", "quux.com"));
        assertGetKey(reg, "foo.com", foo);
        assertGetKey(reg, "bar.com", foo);
        assertGetKey(reg, "baz.com", bar);
        assertGetKey(reg, "quux.com", bar);
    }

    @Test(expected = IllegalArgumentException.class)
    public void keys_cannot_overlap() {
        HostRegistry reg = new HostRegistry();
        reg.update(foo, List.of("foo.com", "bar.com"));
        reg.update(bar, List.of("bar.com", "baz.com"));
    }

    @Test
    public void all_hosts_are_returned() {
        HostRegistry reg = new HostRegistry();
        reg.update(foo, List.of("foo.com", "bar.com"));
        reg.update(bar, List.of("baz.com", "quux.com"));
        assertEquals(4, reg.getAllHosts().size());
    }

    @Test
    public void ensure_that_collection_is_copied() {
        HostRegistry reg = new HostRegistry();
        List<String> hosts = new ArrayList<>(List.of("foo.com", "bar.com", "baz.com"));
        reg.update(foo, hosts);
        assertEquals(3, reg.getHostsForKey(foo).size());
        hosts.remove(2);
        assertEquals(3, reg.getHostsForKey(foo).size());
    }

    @Test
    public void ensure_that_underlying_hosts_do_not_change() {
        HostRegistry reg = new HostRegistry();
        reg.update(foo, List.of("foo.com", "bar.com", "baz.com"));
        Collection<String> hosts = reg.getAllHosts();
        assertEquals(3, hosts.size());
        reg.update(foo, List.of("foo.com"));
        assertEquals(3, hosts.size());
    }

    private void assertGetKey(HostRegistry reg, String host, ApplicationId expectedKey) {
        assertNotNull(reg.getKeyForHost(host));
        assertEquals(expectedKey, reg.getKeyForHost(host));
    }

}
