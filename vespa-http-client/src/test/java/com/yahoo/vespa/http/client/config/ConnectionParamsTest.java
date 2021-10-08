// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.22
 */
public class ConnectionParamsTest {

    @Test
    public void testDefaults() {
        ConnectionParams params = new ConnectionParams.Builder().build();

        assertThat(params.getHeaders().isEmpty(), is(true));
        assertThat(params.getNumPersistentConnectionsPerEndpoint(), is(1));
        assertThat(params.getSslContext(), nullValue());
    }

    @Test
    public void testSetters() throws NoSuchAlgorithmException {
        ConnectionParams params = new ConnectionParams.Builder()
                .addHeader("Foo", "Bar")
                .addHeader("Foo", "Baz")
                .addHeader("Banana", "Apple")
                .setNumPersistentConnectionsPerEndpoint(2)
                .setSslContext(SSLContext.getDefault())
                .build();

        assertThat(params.getNumPersistentConnectionsPerEndpoint(), is(2));

        assertThat(params.getHeaders().isEmpty(), is(false));
        assertThat(params.getHeaders().size(), is(3));
        //Iteration order seems stable, let's keep it like this for now
        Iterator<Map.Entry<String, String>> headers = params.getHeaders().iterator();
        Map.Entry<String, String> header1 = headers.next();
        assertThat(header1.getKey(), equalTo("Foo"));
        assertThat(header1.getValue(), equalTo("Bar"));
        Map.Entry<String, String> header2 = headers.next();
        assertThat(header2.getKey(), equalTo("Foo"));
        assertThat(header2.getValue(), equalTo("Baz"));
        Map.Entry<String, String> header3 = headers.next();
        assertThat(header3.getKey(), equalTo("Banana"));
        assertThat(header3.getValue(), equalTo("Apple"));
    }

    @Test
    public void header_providers_are_registered() {
        ConnectionParams.HeaderProvider dummyProvider1 = () -> "fooValue";
        ConnectionParams.HeaderProvider dummyProvider2 = () -> "barValue";
        ConnectionParams params = new ConnectionParams.Builder()
                .addDynamicHeader("foo", dummyProvider1)
                .addDynamicHeader("bar", dummyProvider2)
                .build();
        Map<String, ConnectionParams.HeaderProvider> headerProviders = params.getDynamicHeaders();
        assertEquals(2, headerProviders.size());
        assertEquals(dummyProvider1, headerProviders.get("foo"));
        assertEquals(dummyProvider2, headerProviders.get("bar"));
    }

}
