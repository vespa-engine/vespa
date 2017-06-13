package com.yahoo.vespa.hosted.dockerapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;

/**
 * @author freva
 */
public class DockerNetworkCreator {
    private static InetAddress hostDefaultGateway;

    public static InetAddress getDefaultGatewayLinux(boolean ipv6) throws IOException {
        if (hostDefaultGateway == null) {
            String command = ipv6 ? "route -A inet6 -n | grep 'UG[ \t]' | awk '{print $2}'" :
                    "route -n | grep 'UG[ \t]' | awk '{print $2}'";
            Process result = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader output = new BufferedReader(new InputStreamReader(result.getInputStream()));
            hostDefaultGateway = InetAddress.getByName(output.readLine());
        }

        return hostDefaultGateway;
    }

    static NetworkAddressInterface getInterfaceForAddress(InetAddress address) throws SocketException, UnknownHostException {
        for (NetworkInterface netinter : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InterfaceAddress interAddr : netinter.getInterfaceAddresses()) {
                if (address.equals(interAddr.getAddress())) {
                    return new NetworkAddressInterface(netinter, interAddr);
                }
            }
        }

        throw new UnknownHostException("Could not find Ethernet interface address");
    }

    static class NetworkAddressInterface {
        final NetworkInterface networkInterface;
        final InterfaceAddress interfaceAddress;

        NetworkAddressInterface(NetworkInterface networkInterface, InterfaceAddress interfaceAddress) {
            this.networkInterface = networkInterface;
            this.interfaceAddress = interfaceAddress;
        }
    }
}
