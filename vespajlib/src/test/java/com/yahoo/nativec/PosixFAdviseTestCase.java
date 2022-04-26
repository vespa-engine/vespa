package com.yahoo.nativec;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PosixFAdviseTestCase {
    @Test
    public void requireThatPosixFAdviseIsDetected() {
        if (Platform.isLinux()) {
            assertNull(PosixFAdvise.init());
        } else {
            assertEquals("Platform is unsúpported. Only supported on linux.", PosixFAdvise.init().getMessage());
        }
    }
}
