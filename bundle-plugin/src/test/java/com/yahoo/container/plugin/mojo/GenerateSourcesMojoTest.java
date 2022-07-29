// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Simon Thoresen Hult
 */
public class GenerateSourcesMojoTest {

    @Test
    void requireThatDefaultConfigGenVersionIsLoadedFromBuildProperties() throws MojoExecutionException {
        String expected = System.getProperty("expectedDefaultConfigGenVersion");
        System.out.println("expectedDefaultConfigGenVersion = " + expected);
        assumeTrue(expected != null);

        String actual = GenerateSourcesMojo.loadDefaultConfigGenVersion();
        System.out.println("defaultConfigGenVersion = " + actual);
        assertEquals(expected, actual);
    }

}
