package com.yahoo.nativec;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GlibCTestCase {
    @Test
    public void requireThatPosixFAdviseIsDetected() {
        if (Platform.isLinux()) {
            assertNull(PosixFAdvise.init());
        } else {
            assertEquals("Platform is unsupported. Only supported on Linux.", PosixFAdvise.init().getMessage());
        }
    }

    @Test
    public void requireGlibcVersionIsDetected() {
        if (Platform.isLinux()) {
            assertNull(GLibcVersion.init());
            GLibcVersion version = new GLibcVersion();
            assertNotEquals("", version.version());
            assertTrue(version.major() >= 2);
            assertTrue((version.major() >= 3) || ((version.major() == 2) && (version.minor() >= 17)));
        } else {
            assertEquals("Platform is unsupported. Only supported on Linux.", PosixFAdvise.init().getMessage());
        }
    }
}
