package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
class EndpointCertificateTest {

    @Test
    public void san_matches() {
        List<String> sans = List.of("*.a.example.com", "b.example.com", "c.example.com");
        assertTrue(EndpointCertificate.sanMatches("b.example.com", sans));
        assertTrue(EndpointCertificate.sanMatches("c.example.com", sans));
        assertTrue(EndpointCertificate.sanMatches("foo.a.example.com", sans));
        assertFalse(EndpointCertificate.sanMatches("", List.of()));
        assertFalse(EndpointCertificate.sanMatches("example.com", List.of()));
        assertFalse(EndpointCertificate.sanMatches("example.com", sans));
        assertFalse(EndpointCertificate.sanMatches("d.example.com", sans));
        assertFalse(EndpointCertificate.sanMatches("a.example.com", sans));
        assertFalse(EndpointCertificate.sanMatches("aa.example.com", sans));
        assertFalse(EndpointCertificate.sanMatches("c.c.example.com", sans));
        assertFalse(EndpointCertificate.sanMatches("a.a.a.example.com", sans));
    }

}
