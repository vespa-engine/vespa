// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 14/07/2017.
 * Verifies that the IP addresses of a node points to the correct hostname
 * 
 * @author olaaun
 * @author sgrostad
 */
public class IPAddressVerifier {

    private static final Logger logger = Logger.getLogger(IPAddressVerifier.class.getName());

    private final String expectedHostname;
    private final boolean skipReverseLookup;

    public IPAddressVerifier(String expectedHostname, boolean skipReverseLookup) {
        this.expectedHostname = expectedHostname;
        this.skipReverseLookup = skipReverseLookup;
    }

    public void reportFaultyIpAddresses(NodeSpec nodeSpec, SpecVerificationReport specVerificationReport) {
        String[] faultyIpAddresses = getFaultyIpAddresses(nodeSpec);
        if (faultyIpAddresses.length > 0) {
            specVerificationReport.setFaultyIpAddresses(faultyIpAddresses);
        }
    }

    public String[] getFaultyIpAddresses(NodeSpec nodeSpec) {
        List<String> faultyIpAddresses = new ArrayList<>();
        if (expectedHostname == null || expectedHostname.equals(""))
            return new String[0];

        if (!isValidIpv4(nodeSpec.getIpv4Address())) {
            faultyIpAddresses.add(nodeSpec.getIpv4Address());
        }
        if (!isValidIpv6(nodeSpec.getIpv6Address())) {
            faultyIpAddresses.add(nodeSpec.getIpv6Address());
        }
        return faultyIpAddresses.toArray(new String[0]);
    }

    private boolean hostnameResolvesToIpAddress(String ipAddress) {
        InetAddress addressFromIpAddress;
        try {
            addressFromIpAddress = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, "Failed to parse IP address " + ipAddress, e);
            return false;
        }

        List<InetAddress> addressesFromHostname;
        try {
            addressesFromHostname = mockableGetAllByName(expectedHostname);
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, "Failed to get IP addresses of hostname " + expectedHostname, e);
            return false;
        }

        if (addressesFromHostname.stream().noneMatch(addressFromIpAddress::equals)) {
            logger.log(Level.WARNING, "Hostname " + expectedHostname + " resolved to " + addressesFromHostname +
                            " which does not contain the IP address " + ipAddress);
            return false;
        }

        return true;
    }

    private boolean ipAddressResolvesToHostname(String ipAddressLookupFormat) {
        if (skipReverseLookup) {
            return true;
        }

        try {
            String hostnameFromIpAddress = reverseLookUp(ipAddressLookupFormat);
            if (hostnameFromIpAddress.equals(expectedHostname)) {
                return true;
            }

            logger.log(Level.WARNING, "IP address " + ipAddressLookupFormat + " resolved to " +
                    hostnameFromIpAddress + ", not " + expectedHostname);
        } catch (NamingException e) {
            logger.log(Level.WARNING, "Could not get hostname of IP address " + ipAddressLookupFormat, e);
        }

        return false;
    }

    private boolean isValidIpv4(String ipv4Address) {
        if (ipv4Address == null) {
            return true;
        }

        return hostnameResolvesToIpAddress(ipv4Address) &&
                ipAddressResolvesToHostname(convertIpv4ToLookupFormat(ipv4Address));
    }

    private boolean isValidIpv6(String ipv6Address) {
        if (ipv6Address == null) {
            return true;
        }

        return hostnameResolvesToIpAddress(ipv6Address) &&
                ipAddressResolvesToHostname(convertIpv6ToLookupFormat(ipv6Address));
    }

    String reverseLookUp(String ipAddress) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        DirContext ctx = new InitialDirContext(env);
        Attributes attrs = ctx.getAttributes(ipAddress, new String[]{"PTR"});
        for (NamingEnumeration<? extends Attribute> ae = attrs.getAll(); ae.hasMoreElements(); ) {
            Attribute attr = ae.next();
            Enumeration<?> vals = attr.getAll();
            if (vals.hasMoreElements()) {
                String hostname = vals.nextElement().toString();
                ctx.close();
                return hostname.substring(0, hostname.length() - 1);
            }
        }
        ctx.close();
        return "";
    }

    String convertIpv6ToLookupFormat(String ipAddress) {
        StringBuilder newIpAddress = new StringBuilder();
        String doubleColonReplacement = "0.0.0.0.0.0.0.0.0.0.0.0.";
        String domain = "ip6.arpa";
        String[] hextets = ipAddress.split(":");
        for (int i = hextets.length - 1; i >= 0; i--) {
            String reversedHextet = new StringBuilder(hextets[i]).reverse().toString();
            if (reversedHextet.equals("")) {
                newIpAddress.append(doubleColonReplacement);
                continue;
            }
            String trailingZeroes = "0000";
            String paddedHextet = (reversedHextet + trailingZeroes).substring(0, trailingZeroes.length());
            String punctuatedHextet = paddedHextet.replaceAll(".", "$0.");
            newIpAddress.append(punctuatedHextet);
        }
        newIpAddress.append(domain);
        return newIpAddress.toString();
    }

    String convertIpv4ToLookupFormat(String ipAddress) {
        String domain = "in-addr.arpa";
        String[] octets = ipAddress.split("\\.");
        StringBuilder convertedIpAddress = new StringBuilder();
        for (int i = octets.length - 1; i >= 0; i--) {
            convertedIpAddress.append(octets[i]).append(".");
        }
        convertedIpAddress.append(domain);
        return convertedIpAddress.toString();
    }

    List<InetAddress> mockableGetAllByName(String hostname) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(hostname));
    }
}
