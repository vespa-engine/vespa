// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.net;

import com.google.common.net.InetAddresses;

import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a single IPv4 or IPv6 CIDR block.
 *
 * @author valerijf
 */
public class CidrBlock {

    private final BigInteger addressInteger;
    private final int prefixLength;
    private final int addressLength;

    /** Creates a CIDR block that only contains the provided IP address (/32 if IPv4, /128 if IPv6) */
    public CidrBlock(InetAddress inetAddress) {
        this(inetAddress, 8 * inetAddress.getAddress().length);
    }

    public CidrBlock(InetAddress inetAddress, int prefixLength) {
        this(inetAddress.getAddress(), prefixLength);
    }

    public CidrBlock(byte[] address, int prefixLength) {
        if (prefixLength < 0) throw new IllegalArgumentException(
                "Prefix size cannot be negative, but was " + prefixLength);

        this.prefixLength = prefixLength;
        this.addressLength = 8 * address.length;
        if (prefixLength > addressLength) throw new IllegalArgumentException(
                String.format("Prefix size (%s) cannot be longer than address length (%s)", prefixLength, addressLength));

        this.addressInteger = inetAddressToBigInteger(address);
    }

    /** For internal use only, does not validate */
    private CidrBlock(BigInteger addressInteger, int prefixLength, int addressLength) {
        this.addressInteger = addressInteger;
        this.prefixLength = prefixLength;
        this.addressLength = addressLength;
    }

    /** @return The first IP address in this CIDR block */
    public InetAddress getInetAddress() {
        return bitsToInetAddress(addressInteger, addressLength);
    }

    /** @return the number of bits in the network mask */
    public int prefixLength() {
        return prefixLength;
    }

    private int suffixLength() { return addressLength - prefixLength; }

    public boolean isIpv6() {
        return addressLength == 128;
    }

    /** @return true iff the address is in this CIDR network. */
    public boolean contains(InetAddress address) {
        BigInteger addressInteger = new CidrBlock(address).addressInteger;
        return firstAddressInteger().compareTo(addressInteger) <= 0 &&
               addressInteger.compareTo(lastAddressInteger()) <= 0;
    }

    private BigInteger firstAddressInteger() {
        return addressInteger.shiftRight(suffixLength()).shiftLeft(suffixLength());
    }

    private BigInteger lastAddressInteger() {
        return addressInteger.or(suffixMask());
    }

    private BigInteger suffixMask() {
        return BigInteger.ONE.shiftLeft(suffixLength()).subtract(BigInteger.ONE);
    }

    /** Returns a copy of this resized to the given newPrefixLength */
    public CidrBlock resize(int newPrefixLength) {
        return new CidrBlock(addressInteger, newPrefixLength, addressLength);
    }

    public CidrBlock clearLeastSignificantBits(int bits) {
        return new CidrBlock(firstAddressInteger(), prefixLength, addressLength);
    }

    /** @return a copy of this CIDR block with the host identifier bits cleared */
    public CidrBlock clearHostIdentifier() {
        return clearLeastSignificantBits(suffixLength());
    }

    /** Return the byte at the given offset.  0 refers to the most significant byte of the address. */
    public int getByte(int byteOffset) {
        return addressInteger.shiftRight(addressLength - 8 * (byteOffset + 1)).and(BigInteger.valueOf(0xFF)).intValueExact();
    }

    /** Set the byte at the given offset to 'n'.  0 refers to the most significant byte of the address. */
    public CidrBlock setByte(int byteOffset, int n) {
        if (n < 0 || n > 0xFF) throw new IllegalArgumentException("Byte value must be between 0 and 255, but was " + n);
        int byteDiff = n - getByte(byteOffset);
        return addByteRaw(byteOffset, byteDiff);
    }

    /** Add 'n' to the byte at the given offset, truncating overflow bits.  0 refers to the most significant byte of the address. */
    public CidrBlock addByte(int byteOffset, int n) {
        int oldByte = getByte(byteOffset);
        int newByte = 0xFF & (oldByte + n);
        return addByteRaw(byteOffset, newByte - oldByte);
    }

    private CidrBlock addByteRaw(int byteOffset, int n) {
        BigInteger bit = addressInteger.add(BigInteger.valueOf(n).shiftLeft(addressLength - 8 * (byteOffset + 1)));
        return new CidrBlock(bit, prefixLength, addressLength);
    }

    public boolean overlapsWith(CidrBlock other) {
        if (this.isIpv6() != other.isIpv6()) return false;

        int ignoreLastNBits = addressLength - Math.min(this.prefixLength(), other.prefixLength());
        return this.addressInteger.shiftRight(ignoreLastNBits).equals(other.addressInteger.shiftRight(ignoreLastNBits));
    }

    /** @return the .arpa address for this CIDR block, does not include bit outside the prefix */
    public String getDomainName() {
        StringBuilder recordPtr = new StringBuilder(75);
        int segmentWidth = isIpv6() ? 4 : 8;

        int start = suffixLength() - (segmentWidth - (prefixLength % segmentWidth)) % segmentWidth;
        for (int i = start; i < addressLength; i += segmentWidth) {
            int segment = addressInteger.shiftRight(i)
                                        .and(BigInteger.ONE.shiftLeft(segmentWidth).subtract(BigInteger.ONE))
                                        .intValueExact();

            recordPtr.append(isIpv6() ? Integer.toHexString(segment) : segment).append(".");
        }

        return recordPtr.append(isIpv6() ? "ip6" : "in-addr").append(".arpa.").toString();
    }

    /** @return iterable over all CIDR blocks of the same prefix size, from the current one and up */
    public Iterable<CidrBlock> iterableCidrs() {
        return () -> new Iterator<>() {
            private final BigInteger increment = BigInteger.ONE.shiftLeft(suffixLength());
            private final BigInteger maxValue = BigInteger.ONE.shiftLeft(addressLength).subtract(increment);
            private BigInteger current = addressInteger;

            public boolean hasNext() {
                return current.compareTo(maxValue) < 0;
            }

            public CidrBlock next() {
                if (!hasNext()) throw new NoSuchElementException();
                CidrBlock cidrBlock = new CidrBlock(current, prefixLength, addressLength);
                current = current.add(increment);
                return cidrBlock;
            }
        };
    }

    public Iterable<InetAddress> iterableIps() {
        return () -> new Iterator<>() {
            private final BigInteger maxValue = lastAddressInteger();
            private BigInteger current = addressInteger;

            public boolean hasNext() {
                return current.compareTo(maxValue) <= 0;
            }

            public InetAddress next() {
                if (!hasNext()) throw new NoSuchElementException();
                InetAddress inetAddress = bitsToInetAddress(current, addressLength);
                current = current.add(BigInteger.ONE);
                return inetAddress;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CidrBlock cidrBlock = (CidrBlock) o;
        return prefixLength == cidrBlock.prefixLength &&
               Objects.equals(addressInteger, cidrBlock.addressInteger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addressInteger, prefixLength);
    }

    @Override
    public String toString() {
        return asString();
    }

    public String asString() {
        return InetAddresses.toAddrString(getInetAddress()) + "/" + prefixLength;
    }

    public static CidrBlock fromString(String cidr) {
        String[] cidrParts = cidr.split("/");
        if (cidrParts.length != 2)
            throw new IllegalArgumentException("Invalid CIDR block, expected format to be " +
                    "'<ip address>/<prefix size>', but was '" + cidr + "'");

        InetAddress inetAddress = InetAddresses.forString(cidrParts[0]);
        int prefixSize = Integer.parseInt(cidrParts[1]);

        return new CidrBlock(inetAddress, prefixSize);
    }

    private static BigInteger inetAddressToBigInteger(byte[] address) {
        BigInteger bit = BigInteger.ZERO;
        for (byte b : address)
            bit = bit.shiftLeft(8).add(BigInteger.valueOf(b & 0xFF));
        return bit;
    }

    private static InetAddress bitsToInetAddress(BigInteger ipAddressBits, int addressLength) {
        try {
            byte[] addr = ipAddressBits.toByteArray();
            int addressBytes = addressLength / 8;
            if (addr.length != addressBytes) {
                byte[] temp = new byte[addressBytes];
                System.arraycopy(
                        addr, Math.max(addr.length - addressBytes, 0),
                        temp, Math.max(addressBytes - addr.length, 0), Math.min(addr.length, addressBytes));
                addr = temp;
            }
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e);
        }
    }
}
