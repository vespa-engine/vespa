// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.22
 */
public class EndpointTest {

    @Test
    public void testBasic() {
        Endpoint endpoint = Endpoint.create("foo");

        assertThat(endpoint.getHostname(), equalTo("foo"));
        assertThat(endpoint.getPort(), equalTo(4080));
        assertThat(endpoint.isUseSsl(), is(false));
    }

    @Test
    public void testBasicWithHttpProtocolPrefix() {
        Endpoint endpoint = Endpoint.create("http://foo");
        assertThat(endpoint.getHostname(), equalTo("foo"));
    }

    @Test(expected = RuntimeException.class)
    public void testBasicWithHttpsProtocolPrefix() {
        Endpoint.create("https://foo");
    }

    @Test
    public void testAdvanced() {
        Endpoint endpoint = Endpoint.create("bar", 1234, true);

        assertThat(endpoint.getHostname(), equalTo("bar"));
        assertThat(endpoint.getPort(), equalTo(1234));
        assertThat(endpoint.isUseSsl(), is(true));
    }

    @Test
    public void testMethods() {
        Endpoint a = Endpoint.create("a");
        Endpoint b = Endpoint.create("b");

        assertThat(a, not(equalTo(b)));
        assertThat(a.hashCode(), not(equalTo(b.hashCode())));

        Endpoint a2 = Endpoint.create("a");

        assertThat(a, equalTo(a2));
        assertThat(a.hashCode(), equalTo(a2.hashCode()));

        a.toString();
    }

}
