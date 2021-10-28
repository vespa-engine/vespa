// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.provision.serialization;

import com.yahoo.config.provision.NetworkPorts;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serializes network port allocations to/from JSON.
 *
 * @author arnej
 */
public class NetworkPortsSerializer {

    // Network port fields
    private static final String portNumberKey = "port";
    private static final String serviceTypeKey = "type";
    private static final String configIdKey = "cfg";
    private static final String portSuffixKey = "suf";

    // ---------------- Serialization ----------------------------------------------------

    public static void toSlime(NetworkPorts networkPorts, Cursor array) {
        for (NetworkPorts.Allocation allocation : networkPorts.allocations()) {
            Cursor object = array.addObject();
            object.setLong(portNumberKey, allocation.port);
            object.setString(serviceTypeKey, allocation.serviceType);
            object.setString(configIdKey, allocation.configId);
            object.setString(portSuffixKey, allocation.portSuffix);
        }
    }

    // ---------------- Deserialization --------------------------------------------------

    public static Optional<NetworkPorts> fromSlime(Inspector array) {
        List<NetworkPorts.Allocation> list = new ArrayList<>(array.entries());
        array.traverse((ArrayTraverser) (int i, Inspector item) -> {
            list.add(new NetworkPorts.Allocation((int)item.field(portNumberKey).asLong(),
                                                 item.field(serviceTypeKey).asString(),
                                                 item.field(configIdKey).asString(),
                                                 item.field(portSuffixKey).asString()));
            }
        );
        if (list.size() > 0) {
            NetworkPorts allocator = new NetworkPorts(list);
            return Optional.of(allocator);
        }
        return Optional.empty();
    }

}
