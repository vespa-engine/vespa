// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.application.provider.Bundle;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class ConfigModelUtilsTest {

    public static final String VALID_TEST_BUNDLE = "src/test/cfg/application/app1/components/";
    public static final String INVALID_TEST_BUNDLE = "src/test/cfg/application/validation/invalidjar_app/components";

    @Test
    public void all_def_files_in_correct_directory_are_handled_and_files_outside_are_ignored() {
        List<Bundle> bundles = Bundle.getBundles(new File(VALID_TEST_BUNDLE));
        assertThat(bundles.size(), is(1));
        assertThat(bundles.get(0).getDefEntries().size(), is(5));
    }

    @Test
    public void def_file_with_namespace_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("test-namespace");
        assertThat(defEntry.defNamespace, is("config"));
    }

    @Test
    public void def_file_with_namespace_and_namespace_in_filename_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("namespace-in-filename");
        assertThat(defEntry.defNamespace, is("a.b"));
    }

    @Test
    public void def_file_with_package_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("test-package");
        assertThat(defEntry.defNamespace, is("com.mydomain.mypackage"));
    }

    @Test
    public void def_file_with_package_and_pacakage_in_filename_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("package-in-filename");
        assertThat(defEntry.defNamespace, is("com.mydomain.mypackage"));
    }

    @Test
    public void def_file_with_both_package_and_namespace_gets_package_as_namespace() {
        Bundle.DefEntry defEntry = getDefEntry("namespace-and-package");
        assertThat(defEntry.defNamespace, is("com.mydomain.mypackage"));
    }

    private static Bundle.DefEntry getDefEntry(String defName) {
        Bundle bundle = Bundle.getBundles(new File(VALID_TEST_BUNDLE)).get(0);

        for (Bundle.DefEntry defEntry : bundle.getDefEntries()) {
            if (defEntry.defName.equals(defName))
                return defEntry;
        }
        throw new IllegalArgumentException("No def file with name '" + defName + "' found in the test bundle.");
    }

    @Test
    public void invalid_jar_file_fails_to_load() {
        try {
            Bundle.getBundles(new File(INVALID_TEST_BUNDLE));
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Error opening jar file 'invalid.jar'. Please check that this is a valid jar file"));
        }
    }

}
