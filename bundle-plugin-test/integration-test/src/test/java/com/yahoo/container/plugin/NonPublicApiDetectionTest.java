package com.yahoo.container.plugin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
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

    private static Set<String> usedNonPublicApi;

    @BeforeAll
    public static void setup() {
        try {
            File componentJar = findBundleJar("non-public-api-usage");
            Attributes mainAttributes = new JarFile(componentJar).getManifest().getMainAttributes();
            var nonPublicApiAttribute = mainAttributes.getValue("X-JDisc-Non-PublicApi-Import-Package");
            usedNonPublicApi = Arrays.stream(nonPublicApiAttribute.split(",")).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void usage_of_non_publicApi_packages_is_detected() {
        assertEquals(2, usedNonPublicApi.size());
        assertTrue(usedNonPublicApi.contains("ai.vespa.http"));
        assertTrue(usedNonPublicApi.contains("com.yahoo.io"));
    }

}
