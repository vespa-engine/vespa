// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.maven;

import com.yahoo.component.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetadataTest {

    @Test
    void testParsing() {
        Metadata metadata = Metadata.fromXml(metadataXml);
        assertEquals("com.yahoo.vespa", metadata.id().groupId());
        assertEquals("tenant-base", metadata.id().artifactId());
        assertEquals(Version.fromString("6.297.80"), metadata.versions().get(0));
        assertEquals(Version.fromString("7.61.10"), metadata.versions().get(metadata.versions().size() - 1));
    }

    private static final String metadataXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                              "<metadata>\n" +
                                              "  <groupId>com.yahoo.vespa</groupId>\n" +
                                              "  <artifactId>tenant-base</artifactId>\n" +
                                              "  <versioning>\n" +
                                              "    <latest>7.61.10</latest>\n" +
                                              "    <release>7.61.10</release>\n" +
                                              "    <versions>\n" +
                                              "      <version>6.297.80</version>\n" +
                                              "      <version>6.300.15</version>\n" +
                                              "      <version>6.301.8</version>\n" +
                                              "      <version>6.303.29</version>\n" +
                                              "      <version>6.304.14</version>\n" +
                                              "      <version>6.305.35</version>\n" +
                                              "      <version>6.328.65</version>\n" +
                                              "      <version>6.329.64</version>\n" +
                                              "      <version>6.330.51</version>\n" +
                                              "      <version>7.3.19</version>\n" +
                                              "      <version>7.18.17</version>\n" +
                                              "      <version>7.20.129</version>\n" +
                                              "      <version>7.21.18</version>\n" +
                                              "      <version>7.22.18</version>\n" +
                                              "      <version>7.38.38</version>\n" +
                                              "      <version>7.39.5</version>\n" +
                                              "      <version>7.40.41</version>\n" +
                                              "      <version>7.41.15</version>\n" +
                                              "      <version>7.57.40</version>\n" +
                                              "      <version>7.60.51</version>\n" +
                                              "      <version>7.61.10</version>\n" +
                                              "    </versions>\n" +
                                              "    <lastUpdated>20190619054245</lastUpdated>\n" +
                                              "  </versioning>\n" +
                                              "</metadata>\n";
}
