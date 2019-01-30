// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 */
public class HostRegistryTest {
    @Test
    public void old_hosts_are_removed() {
        HostRegistry<String> reg = new HostRegistry<>();
        assertNull(reg.getKeyForHost("foo.com"));
        reg.update("fookey", Arrays.asList("foo.com", "bar.com", "baz.com"));
        assertGetKey(reg, "foo.com", "fookey");
        assertGetKey(reg, "bar.com", "fookey");
        assertGetKey(reg, "baz.com", "fookey");
        assertThat(reg.getAllHosts().size(), is(3));
        reg.update("fookey", Arrays.asList("bar.com", "baz.com"));
        assertNull(reg.getKeyForHost("foo.com"));
        assertGetKey(reg, "bar.com", "fookey");
        assertGetKey(reg, "baz.com", "fookey");

        assertThat(reg.getAllHosts().size(), is(2));
        assertThat(reg.getAllHosts(), contains("bar.com", "baz.com"));
        reg.removeHostsForKey("fookey");
        assertThat(reg.getAllHosts().size(), is(0));
        assertNull(reg.getKeyForHost("foo.com"));
        assertNull(reg.getKeyForHost("bar.com"));
    }

    @Test
    public void multiple_keys_are_handled() {
        HostRegistry<String> reg = new HostRegistry<>();
        reg.update("fookey", Arrays.asList("foo.com", "bar.com"));
        reg.update("barkey", Arrays.asList("baz.com", "quux.com"));
        assertGetKey(reg, "foo.com", "fookey");
        assertGetKey(reg, "bar.com", "fookey");
        assertGetKey(reg, "baz.com", "barkey");
        assertGetKey(reg, "quux.com", "barkey");
    }

    @Test(expected = IllegalArgumentException.class)
    public void keys_cannot_overlap() {
        HostRegistry<String> reg = new HostRegistry<>();
        reg.update("fookey", Arrays.asList("foo.com", "bar.com"));
        reg.update("barkey", Arrays.asList("bar.com", "baz.com"));
    }

    @Test
    public void all_hosts_are_returned() {
        HostRegistry<String> reg = new HostRegistry<>();
        reg.update("fookey", Arrays.asList("foo.com", "bar.com"));
        reg.update("barkey", Arrays.asList("baz.com", "quux.com"));
        assertThat(reg.getAllHosts().size(), is(4));
    }

    @Test
    public void ensure_that_collection_is_copied() {
        HostRegistry<String> reg = new HostRegistry<>();
        List<String> hosts = new ArrayList<>(Arrays.asList("foo.com", "bar.com", "baz.com"));
        reg.update("fookey", hosts);
        assertThat(reg.getHostsForKey("fookey").size(), is(3));
        hosts.remove(2);
        assertThat(reg.getHostsForKey("fookey").size(), is(3));
    }

    @Test
    public void ensure_that_underlying_hosts_do_not_change() {
        HostRegistry<String> reg = new HostRegistry<>();
        reg.update("fookey", new ArrayList<>(Arrays.asList("foo.com", "bar.com", "baz.com")));
        Collection<String> hosts = reg.getAllHosts();
        assertThat(hosts.size(), is(3));
        reg.update("fookey", new ArrayList<>(Arrays.asList("foo.com")));
        assertThat(hosts.size(), is(3));
    }

    private void assertGetKey(HostRegistry<String> reg, String host, String expectedKey) {
        assertNotNull(reg.getKeyForHost(host));
        assertThat(reg.getKeyForHost(host), is(expectedKey));
    }

}
