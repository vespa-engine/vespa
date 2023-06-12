// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.net;

import com.google.common.net.InetAddresses;
import ai.vespa.net.CidrBlock;
import org.junit.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author valerijf
 */
public class CidrBlockTest {

    @Test(expected = IllegalArgumentException.class)
    public void negative_prefix_size_fails() {
        new CidrBlock(InetAddresses.forString("10.0.0.1"), -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void too_large_ipv4_prefix_size_fails() {
        new CidrBlock(InetAddresses.forString("10.0.0.1"), 35);
    }

    @Test(expected = IllegalArgumentException.class)
    public void too_large_ipv6_prefix_size_fails() {
        new CidrBlock(InetAddresses.forString("::1"), 130);
    }

    @Test
    public void bits_after_prefix_are_ignored() {
        withHostIdentifierCleared("10.3.0.0/16", "10.3.12.180/16", "10.3.128.3/16");
        withHostIdentifierCleared("1234:5600::/25", "1234:5678::123/25", "1234:5600::725/25");
    }

    private void withHostIdentifierCleared(String canonical, String... aliases) {
        CidrBlock canonicalBlock = CidrBlock.fromString(canonical);
        assertEquals(canonical, canonicalBlock.toString());
        for (String alias : aliases) {
            CidrBlock aliasBlock = CidrBlock.fromString(alias).clearHostIdentifier();
            assertEquals(canonicalBlock, aliasBlock);
            assertEquals(canonical, aliasBlock.toString());
        }
    }

    @Test
    public void parse_from_string_test() {
        assertEquals(new CidrBlock(InetAddresses.forString("10.0.0.1"), 12), CidrBlock.fromString("10.0.0.1/12"));
        assertEquals(new CidrBlock(InetAddresses.forString("1234:5678::1"), 64), CidrBlock.fromString("1234:5678:0000::1/64"));
    }

    @Test
    public void ipv4_overlap_test() {
        CidrBlock base = CidrBlock.fromString("10.3.0.0/16");

        assertTrue(base.overlapsWith(CidrBlock.fromString("10.3.0.0/16")));
        assertTrue(base.overlapsWith(CidrBlock.fromString("10.0.0.0/8")));
        assertTrue(base.overlapsWith(CidrBlock.fromString("10.3.128.0/17")));
        assertFalse(base.overlapsWith(CidrBlock.fromString("10.4.0.0/16")));
        assertFalse(base.overlapsWith(CidrBlock.fromString("11.0.0.0/8")));
    }

    @Test
    public void ipv6_overlap_test() {
        CidrBlock base = CidrBlock.fromString("1234:5678:abcd::/48");

        assertTrue(base.overlapsWith(CidrBlock.fromString("1234:5678:abcd::/48")));
        assertTrue(base.overlapsWith(CidrBlock.fromString("1234:5678::/32")));
        assertTrue(base.overlapsWith(CidrBlock.fromString("1234:5678:abcd:8000::/49")));
        assertFalse(base.overlapsWith(CidrBlock.fromString("1234:5678:abce::/48")));
        assertFalse(base.overlapsWith(CidrBlock.fromString("1234:5679::/32")));
    }

    @Test
    public void domain_name_test() {
        assertEquals("3.10.in-addr.arpa.", CidrBlock.fromString("10.3.0.0/16").getDomainName());
        assertEquals("128.192.in-addr.arpa.", CidrBlock.fromString("192.128.0.0/9").getDomainName());

        assertEquals("d.c.b.a.8.7.6.5.4.3.2.1.ip6.arpa.", CidrBlock.fromString("1234:5678:abcd::/48").getDomainName());
        assertEquals("8.c.b.a.8.7.6.5.4.3.2.1.ip6.arpa.", CidrBlock.fromString("1234:5678:abc8::/45").getDomainName());
    }

    @Test
    public void iterableCidrs() {
        CidrBlock superBlock = CidrBlock.fromString("10.12.14.0/24");
        assertEquals(List.of("10.12.14.200/29", "10.12.14.208/29", "10.12.14.216/29", "10.12.14.224/29", "10.12.14.232/29", "10.12.14.240/29", "10.12.14.248/29"),
                StreamSupport.stream(CidrBlock.fromString("10.12.14.200/29").iterableCidrs().spliterator(), false)
                        .takeWhile(superBlock::overlapsWith)
                        .map(CidrBlock::asString)
                        .collect(Collectors.toList()));

        assertEquals(StreamSupport.stream(superBlock.iterableIps().spliterator(), false)
                        .skip(24)
                        .map(ip -> InetAddresses.toAddrString(ip) + "/32")
                        .collect(Collectors.toList()),
                StreamSupport.stream(CidrBlock.fromString("10.12.14.24/32").iterableCidrs().spliterator(), false)
                        .takeWhile(superBlock::overlapsWith)
                        .map(CidrBlock::asString)
                        .collect(Collectors.toList()));
    }

    @Test
    public void iterableIps() {
        assertEquals(List.of("10.12.14.24", "10.12.14.25", "10.12.14.26", "10.12.14.27", "10.12.14.28", "10.12.14.29", "10.12.14.30", "10.12.14.31"),
                StreamSupport.stream(CidrBlock.fromString("10.12.14.24/29").iterableIps().spliterator(), false)
                        .map(InetAddresses::toAddrString)
                        .collect(Collectors.toList()));

        assertEquals(List.of("10.12.14.24"),
                StreamSupport.stream(CidrBlock.fromString("10.12.14.24/32").iterableIps().spliterator(), false)
                        .map(InetAddresses::toAddrString)
                        .collect(Collectors.toList()));
    }

    @Test
    public void testByteAccessors() {
        CidrBlock ipv4Block = CidrBlock.fromString("11.22.33.44/24");
        assertEquals(11, ipv4Block.getByte(0));
        assertEquals(22, ipv4Block.getByte(1));
        assertEquals(33, ipv4Block.getByte(2));
        assertEquals(44, ipv4Block.getByte(3));
        assertEquals(24, ipv4Block.prefixLength());

        assertEquals("12.22.33.44/24", ipv4Block.addByte(0, 1).asString());
        assertEquals("11.22.33.45/24", ipv4Block.addByte(3, 1).asString());
        assertEquals("55.22.33.44/24", ipv4Block.setByte(0, 55).asString());
        assertEquals("11.22.33.55/24", ipv4Block.setByte(3, 55).asString());

        CidrBlock ipv6Block = CidrBlock.fromString("de63:aca:dcdc:e2a8:1fb4:542:b80d:f7c3/24");
        assertEquals(0xde, ipv6Block.getByte(0));
        assertEquals(0x63, ipv6Block.getByte(1));
        assertEquals(0x0a, ipv6Block.getByte(2));
        assertEquals(0xc3, ipv6Block.getByte(15));
        assertEquals(24, ipv6Block.prefixLength());

        assertEquals("df63:aca:dcdc:e2a8:1fb4:542:b80d:f7c3/24", ipv6Block.addByte(0, 1).asString());
        assertEquals("ab63:aca:dcdc:e2a8:1fb4:542:b80d:f7c3/24", ipv6Block.setByte(0, 0xab).asString());
        assertEquals("de63:aca:dcdc:e2a8:1fb4:542:b80d:f7c4/24", ipv6Block.addByte(15, 1).asString());
        assertEquals("de63:aca:dcdc:e2a8:1fb4:542:b80d:f7fe/24", ipv6Block.setByte(15, 0xfe).asString());
    }

    @Test
    public void testContainment() {
        CidrBlock block = CidrBlock.fromString("1234:5678::/32");
        assertFalse(block.contains(toInetAddress("1234:5677:ffff:ffff:ffff:ffff:ffff:ffff")));
        assertTrue( block.contains(toInetAddress("1234:5678:0000:0000:0000:0000:0000:0000")));
        assertTrue( block.contains(toInetAddress("1234:5678:0000:0000:0000:0000:0000:0001")));
        assertTrue( block.contains(toInetAddress("1234:5678:ffff:ffff:ffff:ffff:ffff:ffff")));
        assertFalse(block.contains(toInetAddress("1234:5679:0000:0000:0000:0000:0000:0001")));
    }

    private InetAddress toInetAddress(String address) {
        return InetAddresses.forString(address);
    }
}