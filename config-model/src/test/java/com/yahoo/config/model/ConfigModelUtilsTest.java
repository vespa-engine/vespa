// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.application.provider.Bundle;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Ulf Lilleengen
 */
public class ConfigModelUtilsTest {

    public static final String VALID_TEST_BUNDLE = "src/test/cfg/application/app1/components/";
    public static final String INVALID_TEST_BUNDLE = "src/test/cfg/application/validation/invalidjar_app/components";

    @Test
    void all_def_files_in_correct_directory_are_handled_and_files_outside_are_ignored() {
        List<Bundle> bundles = Bundle.getBundles(new File(VALID_TEST_BUNDLE));
        assertEquals(1, bundles.size());
        assertEquals(5, bundles.get(0).getDefEntries().size());
    }

    @Test
    void def_file_with_namespace_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("test-namespace");
        assertEquals("config", defEntry.defNamespace);
    }

    @Test
    void def_file_with_namespace_and_namespace_in_filename_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("namespace-in-filename");
        assertEquals("a.b", defEntry.defNamespace);
    }

    @Test
    void def_file_with_package_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("test-package");
        assertEquals("com.mydomain.mypackage", defEntry.defNamespace);
    }

    @Test
    void def_file_with_package_and_pacakage_in_filename_is_handled() {
        Bundle.DefEntry defEntry = getDefEntry("package-in-filename");
        assertEquals("com.mydomain.mypackage", defEntry.defNamespace);
    }

    @Test
    void def_file_with_both_package_and_namespace_gets_package_as_namespace() {
        Bundle.DefEntry defEntry = getDefEntry("namespace-and-package");
        assertEquals("com.mydomain.mypackage", defEntry.defNamespace);
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
    void invalid_jar_file_fails_to_load() {
        try {
            Bundle.getBundles(new File(INVALID_TEST_BUNDLE));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error opening jar file 'invalid.jar'. Please check that this is a valid jar file", e.getMessage());
        }
    }

}
