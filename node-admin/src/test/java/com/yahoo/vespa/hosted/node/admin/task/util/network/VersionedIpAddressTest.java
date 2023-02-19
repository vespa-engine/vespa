package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.google.common.testing.EqualsTester;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class VersionedIpAddressTest {

    @Test
    void ip4_address_can_be_generated_from_string() {
        var ip4 = VersionedIpAddress.from("10.0.0.1");
        assertEquals(IPVersion.IPv4, ip4.version());
        assertEquals("10.0.0.1", ip4.asString());
    }

    @Test
    void ip6_address_can_be_generated_from_string() {
        var ip6 = VersionedIpAddress.from("::1");
        assertEquals(IPVersion.IPv6, ip6.version());
        assertEquals("::1", ip6.asString());
    }

    @Test
    void they_are_sorted_by_version_then_by_address() {
        var ip4 = VersionedIpAddress.from("10.0.0.1");
        var ip4_2 = VersionedIpAddress.from("127.0.0.1");
        var ip6 = VersionedIpAddress.from("::1");
        var ip6_2 = VersionedIpAddress.from("::2");

        var sorted = Stream.of(ip4_2, ip6, ip4, ip6_2)
                .sorted()
                .toList();
        assertEquals(List.of(ip6, ip6_2, ip4, ip4_2), sorted);
    }

    @Test
    void endpoint_with_port_is_generated_correctly_for_both_versions() {
        var ip4 = VersionedIpAddress.from("10.0.0.1");
        var ip6 = VersionedIpAddress.from("::1");

        assertEquals("10.0.0.1:8080", ip4.asEndpoint(8080));
        assertEquals("[::1]:8080", ip6.asEndpoint(8080));
    }

    @Test
    void equals_and_hashCode_are_implemented() {
        new EqualsTester()
                .addEqualityGroup(VersionedIpAddress.from("::1"), VersionedIpAddress.from("::1"))
                .addEqualityGroup(VersionedIpAddress.from("::2"))
                .addEqualityGroup(VersionedIpAddress.from("127.0.0.1"), VersionedIpAddress.from("127.0.0.1"))
                .addEqualityGroup(VersionedIpAddress.from("10.0.0.1"))
                .testEquals();
    }

}
