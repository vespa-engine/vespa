// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.22
 */
public class FeedParamsTest {

    @Test
    public void testDefaults() {
        FeedParams params = new FeedParams.Builder().build();

        assertThat(params.getDataFormat(), equalTo(FeedParams.DataFormat.JSON_UTF8));
        assertThat(params.getMaxChunkSizeBytes(), is(50 * 1024));
        assertThat(params.getRoute(), nullValue());
        assertThat(params.getServerTimeout(TimeUnit.SECONDS), is(180L));
        assertThat(params.getClientTimeout(TimeUnit.SECONDS), is(20L));
    }

    @Test
    public void testConfig() {
        FeedParams params = new FeedParams.Builder()
                .setDataFormat(FeedParams.DataFormat.XML_UTF8)
                .setMaxChunkSizeBytes(123)
                .setRoute("abc")
                .setClientTimeout(321, TimeUnit.SECONDS)
                .build();

        assertThat(params.getDataFormat(), equalTo(FeedParams.DataFormat.XML_UTF8));
        assertThat(params.getMaxChunkSizeBytes(), is(123));
        assertThat(params.getRoute(), equalTo("abc"));
        assertThat(params.getServerTimeout(TimeUnit.SECONDS), is(180L));
        assertThat(params.getClientTimeout(TimeUnit.SECONDS), is(321L));

        params = new FeedParams.Builder()
                .setServerTimeout(333L, TimeUnit.SECONDS)
                .setClientTimeout(222L, TimeUnit.SECONDS)
                .build();

        assertThat(params.getServerTimeout(TimeUnit.SECONDS), is(333L));
        assertThat(params.getClientTimeout(TimeUnit.SECONDS), is(222L));
    }

}
