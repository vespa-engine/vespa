// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.statuspage;

import org.junit.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author mpolden
 */
public class StatusPageClientTest {

    @Test
    public void test_url_building() {
        {
            URI apiUrl = URI.create("https://statuspage.io");
            String secret = "testpage:testkey";
            assertEquals("https://testpage.statuspage.io/api/v2/incidents.json?api_key=testkey",
                         StatusPageClient.create(apiUrl, secret).pageUrl("incidents", Optional.empty()).toString());
            assertEquals("https://testpage.statuspage.io/api/v2/scheduled-maintenances.json?api_key=testkey&since=2015-01-01T00%3A00%2B00%3A00",
                         StatusPageClient.create(apiUrl, secret).pageUrl("scheduled-maintenances",
                                                                         Optional.of("2015-01-01T00:00+00:00")).toString());
        }

        {
            URI apiUrl = URI.create("http://foo.bar");
            assertEquals("http://foo.bar/api/v2/incidents.json?api_key=testkey",
                         StatusPageClient.create(apiUrl, "testpage:testkey").pageUrl("incidents", Optional.empty()).toString());
        }

        {
            try {
                URI apiUrl = URI.create("http://foo.bar");
                assertEquals("http://foo.bar/api/v2/incidents.json?api_key=testkey",
                             StatusPageClient.create(apiUrl, "").pageUrl("incidents", Optional.empty()).toString());
                fail("Expected exception");
            } catch (IllegalArgumentException ignored) {}
        }
    }

}
