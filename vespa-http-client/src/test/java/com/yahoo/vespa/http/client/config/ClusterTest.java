// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.22
 */
public class ClusterTest {

    @Test
    public void testSimple() {
        Cluster cluster = new Cluster.Builder().build();

        assertThat(cluster.getEndpoints().size(), is(0));
        assertThat(cluster.getRoute(), nullValue());
    }

    @Test
    public void testConfig() {
        Cluster cluster = new Cluster.Builder()
                .addEndpoint(Endpoint.create("a"))
                .addEndpoint(Endpoint.create("b"))
                .setRoute("blah")
                .build();

        assertThat(cluster.getEndpoints().size(), is(2));
        assertThat(cluster.getEndpoints().get(0).getHostname(), equalTo("a"));
        assertThat(cluster.getEndpoints().get(1).getHostname(), equalTo("b"));
        assertThat(cluster.getRoute(), equalTo("blah"));
    }
}
