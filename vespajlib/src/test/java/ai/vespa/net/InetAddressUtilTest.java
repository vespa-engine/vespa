package ai.vespa.net;

import com.google.common.net.InetAddresses;
import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author hakonhall
 */
class InetAddressUtilTest {
    @Test
    void testToString() {
        assertEquals("1080::8:800:200c:417a", toString("1080:0:0:0:8:800:200C:417A"));
        assertEquals("1080::8:0:0:417a", toString("1080:0:0:0:8:0:0:417A"));
        assertEquals("::8190:3426", toString("::129.144.52.38"));
        assertEquals("::", toString("::"));

        assertEquals("0.0.0.0", toString("0.0.0.0"));
        assertEquals("222.173.190.239", toString("222.173.190.239"));
    }

    @Test
    void testToStringWithInterface() throws SocketException {
        NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .forEach(inetAddress -> {
                    String address = InetAddressUtil.toString(inetAddress);
                    assertEquals(-1, address.indexOf('%'), "No interface in " + address);
                });
    }

    @Test
    void testToStringWithInterface2() throws UnknownHostException {
        byte[] bytes = new byte[] { 0x10,(byte)0x80, 0,0, 0,0, 0,0, 0,8, 8,0, 0x20,0x0c, 0x41,0x7a };
        Inet6Address address = Inet6Address.getByAddress(null, bytes, 1);
        // Verify Guava's InetAddresses.toAddrString() includes the interface.
        // If this assert fails, we can use InetAddresses.toAddrString() instead of InetAddressUtil.toString().
        assertNotEquals(-1, InetAddresses.toAddrString(address).indexOf('%'));
        assertEquals("1080::8:800:200c:417a", InetAddressUtil.toString(address));
    }

    private static String toString(String ipAddress) {
        InetAddress inetAddress = InetAddresses.forString(ipAddress);
        return InetAddressUtil.toString(inetAddress);
    }

}
