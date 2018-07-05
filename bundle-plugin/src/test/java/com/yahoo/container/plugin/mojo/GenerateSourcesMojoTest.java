// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class GenerateSourcesMojoTest {

    @Test
    public void requireThatDefaultConfigGenVersionIsLoadedFromBuildProperties() throws MojoExecutionException {
        String expected = System.getProperty("expectedDefaultConfigGenVersion");
        System.out.println("expectedDefaultConfigGenVersion = " + expected);
        assumeNotNull(expected);

        String actual = GenerateSourcesMojo.loadDefaultConfigGenVersion();
        System.out.println("defaultConfigGenVersion = " + actual);
        assertEquals(expected, actual);
    }
}
