package com.yahoo.nativec;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MallInfoTestCase {
    @Test
    public void requireThatMallInfo2IsDetected() {
        if (Platform.isLinux()) {
            assertNull(MallInfo2.init());
        } else {
            assertEquals("Platform is unsúpported. Only supported on linux.", MallInfo2.init().getMessage());
        }
    }
    @Test
    public void requireThatMallInfoIsDetected() {
        if (Platform.isLinux()) {
            assertNull(MallInfo.init());
        } else {
            assertEquals("Platform is unsúpported. Only supported on linux.", MallInfo.init().getMessage());
        }
    }
}
