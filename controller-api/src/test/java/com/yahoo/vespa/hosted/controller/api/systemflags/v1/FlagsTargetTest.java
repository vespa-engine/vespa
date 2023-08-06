// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.SystemName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hakonhall
 */
class FlagsTargetTest {
    @Test
    void sanityCheckFilename() {
        assertTrue(FlagsTarget.filenameForSystem("default.json", SystemName.main));
        assertTrue(FlagsTarget.filenameForSystem("main.json", SystemName.main));
        assertTrue(FlagsTarget.filenameForSystem("main.controller.json", SystemName.main));
        assertTrue(FlagsTarget.filenameForSystem("main.prod.json", SystemName.main));
        assertTrue(FlagsTarget.filenameForSystem("main.prod.us-west-1.json", SystemName.main));
        assertTrue(FlagsTarget.filenameForSystem("main.prod.abc-foo-3.json", SystemName.main));

        assertFalse(FlagsTarget.filenameForSystem("public.json", SystemName.main));
        assertFalse(FlagsTarget.filenameForSystem("public.controller.json", SystemName.main));
        assertFalse(FlagsTarget.filenameForSystem("public.prod.json", SystemName.main));
        assertFalse(FlagsTarget.filenameForSystem("public.prod.us-west-1.json", SystemName.main));
        assertFalse(FlagsTarget.filenameForSystem("public.prod.abc-foo-3.json", SystemName.main));

        assertFlagValidationException("First part of flag filename is neither 'default' nor a valid system: defaults.json", "defaults.json");
        assertFlagValidationException("Invalid flag filename: default", "default");
        assertFlagValidationException("Invalid flag filename: README", "README");
        assertFlagValidationException("First part of flag filename is neither 'default' nor a valid system: nosystem.json", "nosystem.json");
        assertFlagValidationException("Invalid environment in flag filename: main.noenv.json", "main.noenv.json");
        assertFlagValidationException("Invalid region in flag filename: main.prod.%badregion.json", "main.prod.%badregion.json");
    }

    private void assertFlagValidationException(String expectedMessage, String filename) {
        FlagValidationException e = assertThrows(FlagValidationException.class, () -> FlagsTarget.filenameForSystem(filename, SystemName.main));
        assertEquals(expectedMessage, e.getMessage());
    }

}