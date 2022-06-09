package com.yahoo.nativec;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MallInfoTestCase {
    @Test
    public void requireThatMallInfo2IsDetected() {
        if (Platform.isLinux()) {
            GLibcVersion version = new GLibcVersion();
            if ((version.major() >= 3) || ((version.major() == 2) && (version.minor() >= 33))) {
                assertNull(MallInfo2.init());
            }
        } else {
            assertEquals("Platform is unsupported. Only supported on Linux.", MallInfo2.init().getMessage());
        }
    }
    @Test
    public void requireThatMallInfoIsDetected() {
        if (Platform.isLinux()) {
            assertNull(MallInfo.init());
        } else {
            assertEquals("Platform is unsupported. Only supported on Linux.", MallInfo.init().getMessage());
        }
    }
}
