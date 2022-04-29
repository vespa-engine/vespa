package com.yahoo.container.plugin.mojo;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class ApplicationMojoTest {

    @Test
    public void testRegex() {
        assertTrue(ApplicationMojo.isVespaParent("ai.vespa"));
        assertTrue(ApplicationMojo.isVespaParent("ai.vespa.hosted"));
        assertTrue(ApplicationMojo.isVespaParent("com.yahoo.vespa"));
        assertTrue(ApplicationMojo.isVespaParent("com.yahoo.vespa.hosted"));

        assertFalse(ApplicationMojo.isVespaParent("ai"));
        assertFalse(ApplicationMojo.isVespaParent("ai.vespa."));
        assertFalse(ApplicationMojo.isVespaParent("ai.vespaxxx."));
        assertFalse(ApplicationMojo.isVespaParent("com.yahoo"));
        assertFalse(ApplicationMojo.isVespaParent("com.yahoo.vespa."));
        assertFalse(ApplicationMojo.isVespaParent("com.yahoo.vespaxxx"));
    }

}
