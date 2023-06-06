// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
