// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
class CorsLogicTest {

    @Test
    void wildcard_matches_everything() {
        CorsLogic logic = CorsLogic.forAllowedOrigins(List.of("*"));
        assertMatches(logic, true, "http://any.origin", "https://any.origin", "http://any.origin:8080");
    }

    @Test
    void matches_verbatim_and_pattern() {
        CorsLogic logic = CorsLogic.forAllowedOrigins(List.of("http://my.origin", "http://*.domain.origin", "*://do.main", "*.tld"));
        assertMatches(logic, true,
                "http://my.origin", // Matches verbatim
                "http://any.domain.origin", // Matches first pattern
                "http://any.sub.domain.origin", // Matches first pattern
                "http://do.main", "https://do.main", // Matches second pattern
                "https://any.thing.tld"); // Matches third pattern
        assertMatches(logic, false,
                "https://my.origin", // Different scheme from verbatim
                "http://domain.origin", // Missing subdomain to match the first pattern
                "https://sub.do.main"); // Second pattern, but with subdomain
    }

    private static void assertMatches(CorsLogic logic, boolean expected, String... origins) {
        for (String origin : origins)
            assertEquals(expected, logic.originMatches(origin), origin);
    }

    @Test
    void additional_headers_are_included() {
        Map<String, String> additionalHeaders = Map.of(
                "Access-Control-Allow-Headers", "X-Custom-Header,X-Another-Custom-Header",
                "Access-Control-Expose-Headers", "X-Custom-Header"
        );
        CorsLogic logic = CorsLogic.forAllowedOriginsWithHeaders(List.of("*"), additionalHeaders);
        Map<String, String> headers = logic.createCorsResponseHeaders("http://any.origin");

        // Verify that default headers are still present
        for (Map.Entry<String, String> entry : CorsLogic.DEFAULT_ACCESS_CONTROL_HEADERS.entrySet()) {
            assertTrue(headers.containsKey(entry.getKey()), "Missing default header: " + entry.getKey());
            if ("Access-Control-Allow-Headers".equals(entry.getKey())) {
                assertTrue(headers.get(entry.getKey()).contains(entry.getValue()), "Incorrect value for default header: " + entry.getKey());
            } else {
                assertEquals(entry.getValue(), headers.get(entry.getKey()), "Incorrect value for default header: " + entry.getKey());
            }
        }

        // Verify that additional headers are present
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            assertTrue(headers.containsKey(entry.getKey()), "Missing additional header: " + entry.getKey());
            if ("Access-Control-Allow-Headers".equals(entry.getKey())) {
                assertTrue(headers.get(entry.getKey()).contains(entry.getValue()), "Incorrect value for additional header: " + entry.getKey());
            } else {
                assertEquals(entry.getValue(), headers.get(entry.getKey()), "Incorrect value for additional header: " + entry.getKey());
            }
        }
    }

    @Test
    void addtional_headers_take_precedence() {
        Map<String, String> additionalHeaders = Map.of(
                "Access-Control-Allow-Credentials", "veryuniquevalue"
        );

        CorsLogic origLogic = CorsLogic.forAllowedOrigins(List.of("*"));
        Map<String, String> origHeaders = origLogic.createCorsResponseHeaders("http://any.origin");

        CorsLogic newLogic = CorsLogic.forAllowedOriginsWithHeaders(List.of("*"), additionalHeaders);
        Map<String, String> newHeaders = newLogic.createCorsResponseHeaders("http://any.origin");

        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            assertTrue(origHeaders.containsKey(entry.getKey()), "No default value exists for key: " + entry.getKey());
            assertTrue(newHeaders.containsKey(entry.getKey()), "No override value exists for key: " + entry.getKey());
            assertNotEquals(origHeaders.get(entry.getKey()), newHeaders.get(entry.getKey()), "No real override value for key: " + entry.getKey());
            assertEquals(entry.getValue(), newHeaders.get(entry.getKey()), "Incorrect value for key: " + entry.getKey());
        }
    }

    @Test
    void no_additional_headers_works_correctly() {
        CorsLogic logic = CorsLogic.forAllowedOrigins(List.of("*"));
        Map<String, String> headers = logic.createCorsResponseHeaders("http://any.origin");

        // Verify that default headers are still present
        for (Map.Entry<String, String> entry : CorsLogic.DEFAULT_ACCESS_CONTROL_HEADERS.entrySet()) {
            assertTrue(headers.containsKey(entry.getKey()), "Missing default header: " + entry.getKey());
            assertEquals(entry.getValue(), headers.get(entry.getKey()), "Incorrect value for default header: " + entry.getKey());
        }

        // Verify that only the expected headers are present (default headers + ALLOW_ORIGIN_HEADER)
        assertEquals(CorsLogic.DEFAULT_ACCESS_CONTROL_HEADERS.size() + 1, headers.size(),
                     "Unexpected number of headers");
    }
}
