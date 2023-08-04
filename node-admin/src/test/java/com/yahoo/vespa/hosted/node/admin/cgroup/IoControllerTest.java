// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.hosted.node.admin.cgroup.IoController.Device;
import static com.yahoo.vespa.hosted.node.admin.cgroup.IoController.Max;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
class IoControllerTest {

    @Test
    void device_number_parsing() {
        assertEquals(new Device(253, 15), Device.fromDeviceNumber(253 * 256 + 15));
        assertEquals(new Device(345, 123), Device.fromDeviceNumber(345 * 256 + 123));
    }

    @Test
    void parse_io_max() {
        assertEquals(Max.UNLIMITED, Max.fromString(""));
        assertEquals(new Max(Size.from(1), Size.max(), Size.max(), Size.max()), Max.fromString("rbps=1 wiops=max"));
    }
}
