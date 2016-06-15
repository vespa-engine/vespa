// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.application.provider.Bundle;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class ConfigModelUtilsTest {

    /**
     * Tests that a def file both with and without namespace in file name are handled, and that
     * def files in other directories than 'configdefinitions/' within the jar file are ignored.
     */
    @Test
    public void testDefFilesInBundle() {
        List<Bundle> bundles = Bundle.getBundles(new File("src/test/cfg/application/app1/components/"));
        assertThat(bundles.size(), is(1));
        Bundle bundle = bundles.get(0);
        assertThat(bundle.getDefEntries().size(), is(2));

        Bundle.DefEntry defEntry1 = bundle.getDefEntries().get(0);
        Bundle.DefEntry defEntry2;
        List<Bundle.DefEntry> defEntries = bundle.getDefEntries();
        if (defEntry1.defName.equals("test1")) {
            defEntry2 = defEntries.get(1);
        } else {
            defEntry1 = defEntries.get(1);
            defEntry2 = defEntries.get(0);
        }
        assertThat(defEntry1.defName, is("test1"));
        assertThat(defEntry1.defNamespace, is("config"));

        assertThat(defEntry2.defName, is("test2"));
        assertThat(defEntry2.defNamespace, is("a.b"));
    }

    /**
     * Tests that an invalid jar is identified as not being a jar file
     */
    @Test
    public void testInvalidJar() {
        try {
            Bundle.getBundles(new File("src/test/cfg/application/validation/invalidjar_app/components"));
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Error opening jar file 'invalid.jar'. Please check that this is a valid jar file"));
        }
    }
}
