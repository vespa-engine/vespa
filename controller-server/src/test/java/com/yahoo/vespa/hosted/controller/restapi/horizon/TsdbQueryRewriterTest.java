// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.horizon;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static com.yahoo.slime.SlimeUtils.jsonToSlimeOrThrow;
import static com.yahoo.slime.SlimeUtils.toJsonBytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author valerijf
 */
public class TsdbQueryRewriterTest {

    @Test
    void rewrites_query() throws IOException {
        assertRewrite("filters-complex.json", "filters-complex.expected.json", Set.of(TenantName.from("tenant2")), false);

        assertRewrite("filter-in-execution-graph.json",
                "filter-in-execution-graph.expected.json",
                Set.of(TenantName.from("tenant2"), TenantName.from("tenant3")), false);

        assertRewrite("filter-in-execution-graph.json",
                "filter-in-execution-graph.expected.operator.json",
                Set.of(TenantName.from("tenant2"), TenantName.from("tenant3")), true);

        assertRewrite("no-filters.json",
                "no-filters.expected.json",
                Set.of(TenantName.from("tenant2"), TenantName.from("tenant3")), false);

        assertRewrite("filters-meta-query.json",
                "filters-meta-query.expected.json",
                Set.of(TenantName.from("tenant2"), TenantName.from("tenant3")), false);
    }

    private static void assertRewrite(String initialFilename, String expectedFilename, Set<TenantName> tenants, boolean operator) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get("src/test/resources/horizon", initialFilename));
        data = TsdbQueryRewriter.rewrite(data, tenants, operator, SystemName.Public);

        String actualJson = new String(toJsonBytes(jsonToSlimeOrThrow(data).get(), false), UTF_8);
        String expectedJson = new String(toJsonBytes(jsonToSlimeOrThrow(Files.readAllBytes(Paths.get("src/test/resources/horizon", expectedFilename))).get(), false), UTF_8);

        assertEquals(expectedJson, actualJson);
    }
}
