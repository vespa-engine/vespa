package com.yahoo.container.plugin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static com.yahoo.container.plugin.BundleTest.findBundleJar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class NonPublicApiDetectionTest {

    private static Attributes mainAttributes;

    @BeforeAll
    public static void setup() {
        try {
            File componentJar = findBundleJar("non-public-api-usage");
            mainAttributes = new JarFile(componentJar).getManifest().getMainAttributes();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void usage_of_non_publicApi_packages_is_detected() {
        var nonPublicApiAttribute = mainAttributes.getValue("X-JDisc-Non-PublicApi-Import-Package");
        var usedNonPublicApi = Arrays.stream(nonPublicApiAttribute.split(",")).collect(Collectors.toSet());

        assertEquals(2, usedNonPublicApi.size());
        assertTrue(usedNonPublicApi.contains("ai.vespa.http"));
        assertTrue(usedNonPublicApi.contains("com.yahoo.io"));
    }

    @Test
    void exported_yahoo_packages_in_non_vespa_artifacts_are_marked_as_public_api() {
        var publicApiAttribute = mainAttributes.getValue("X-JDisc-PublicApi-Package");
        assertEquals("com.yahoo.test", publicApiAttribute);
    }
}
