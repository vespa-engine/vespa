package ai.vespa.net;

import com.google.common.net.InetAddresses;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @author hakonhall
 */
public class InetAddressUtil {
    private InetAddressUtil() {}

    /** Returns the lower case string representation of the IP address (w/o scope), with :: compression for IPv6. */
    public static String toString(InetAddress inetAddress) {
        if (inetAddress instanceof Inet6Address) {
            String address = InetAddresses.toAddrString(inetAddress);
            // toAddrString() returns any interface/scope as a %-suffix,
            // see https://github.com/google/guava/commit/3f61870ac6e5b18dbb74ce6f6cb2930ad8750a43
            int percentIndex = address.indexOf('%');
            return percentIndex < 0 ? address : address.substring(0, percentIndex);
        } else {
            return inetAddress.getHostAddress();
        }
    }
}
