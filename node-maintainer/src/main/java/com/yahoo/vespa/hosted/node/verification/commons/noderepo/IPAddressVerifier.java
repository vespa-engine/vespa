package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 14/07/2017.
 * Verifies that the IP addresses of a node points to the correct hostname
 */

public class IPAddressVerifier {

    private static final Logger logger = Logger.getLogger(IPAddressVerifier.class.getName());

    public void reportFaultyIpAddresses(NodeRepoJsonModel nodeRepoJsonModel, SpecVerificationReport specVerificationReport) {
        String[] faultyIpAddresses = getFaultyIpAddresses(nodeRepoJsonModel);
        if (faultyIpAddresses.length > 0) {
            specVerificationReport.setFaultyIpAddresses(faultyIpAddresses);
        }
    }

    public String[] getFaultyIpAddresses(NodeRepoJsonModel jsonModel) {
        String expectedHostname = jsonModel.getHostname();
        ArrayList<String> faultyIpAddresses = new ArrayList<>();
        if (expectedHostname == null || expectedHostname.equals(""))
            return new String[0];
        if (!isValidIpv4(jsonModel.getIpv4Address(), expectedHostname)) {
            faultyIpAddresses.add(jsonModel.getIpv4Address());
        }
        if (!isValidIpv6(jsonModel.getIpv6Address(), expectedHostname)) {
            faultyIpAddresses.add(jsonModel.getIpv6Address());
        }
        return faultyIpAddresses.stream().toArray(String[]::new);
    }

    private boolean isValidIpv4(String ipv4Address, String expectedHostname) {
        if (ipv4Address == null) {
            return true;
        }
        String ipv4LookupFormat = convertIpv4ToLookupFormat(ipv4Address);
        try {
            String ipv4Hostname = reverseLookUp(ipv4LookupFormat);
            return ipv4Hostname.equals(expectedHostname);
        } catch (NamingException e) {
            logger.log(Level.WARNING, "Could not get IPv4 hostname", e);
        }
        return false;
    }

    private boolean isValidIpv6(String ipv6Address, String expectedHostname) {
        if (ipv6Address == null) {
            return true;
        }
        String ipv6LookupFormat = convertIpv6ToLookupFormat(ipv6Address);
        try {
            String ipv6Hostname = reverseLookUp(ipv6LookupFormat);
            return ipv6Hostname.equals(expectedHostname);
        } catch (NamingException e) {
            logger.log(Level.WARNING, "Could not get IPv6 hostname", e);
        }
        return false;
    }

    protected String reverseLookUp(String ipAddress) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        String attributeName = ipAddress;
        DirContext ctx = new InitialDirContext(env);
        Attributes attrs = ctx.getAttributes(attributeName, new String[]{"PTR"});
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

    protected String convertIpv6ToLookupFormat(String ipAddress) {
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
            String punctuatedHextet = paddedHextet.replaceAll(".(?=)", "$0.");
            newIpAddress.append(punctuatedHextet);
        }
        newIpAddress.append(domain);
        return newIpAddress.toString();
    }

    protected String convertIpv4ToLookupFormat(String ipAddress) {
        String domain = "in-addr.arpa";
        String[] octets = ipAddress.split("\\.");
        StringBuilder convertedIpAddress = new StringBuilder();
        for (int i = octets.length - 1; i >= 0; i--) {
            convertedIpAddress.append(octets[i]).append(".");
        }
        convertedIpAddress.append(domain);
        return convertedIpAddress.toString();
    }

}
