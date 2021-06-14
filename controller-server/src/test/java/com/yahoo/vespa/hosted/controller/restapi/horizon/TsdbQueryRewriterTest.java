package com.yahoo.vespa.hosted.controller.restapi.horizon;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author valerijf
 */
public class TsdbQueryRewriterTest {

    @Test
    public void rewrites_query() throws IOException {
        assertRewrite("filters-complex.json", "filters-complex.expected.json", Role.reader(TenantName.from("tenant2")));

        assertRewrite("filter-in-execution-graph.json",
                "filter-in-execution-graph.expected.json",
                Role.reader(TenantName.from("tenant2")), Role.athenzTenantAdmin(TenantName.from("tenant3")));

        assertRewrite("filter-in-execution-graph.json",
                "filter-in-execution-graph.expected.operator.json",
                Role.reader(TenantName.from("tenant2")), Role.athenzTenantAdmin(TenantName.from("tenant3")), Role.hostedOperator());

        assertRewrite("no-filters.json",
                "no-filters.expected.json",
                Role.reader(TenantName.from("tenant2")), Role.athenzTenantAdmin(TenantName.from("tenant3")));

        assertRewrite("filters-meta-query.json",
                "filters-meta-query.expected.json",
                Role.reader(TenantName.from("tenant2")), Role.athenzTenantAdmin(TenantName.from("tenant3")));
    }

    @Test(expected = TsdbQueryRewriter.UnauthorizedException.class)
    public void throws_if_no_roles() throws IOException {
        assertRewrite("filters-complex.json", "filters-complex.expected.json");
    }

    private static void assertRewrite(String initialFilename, String expectedFilename, Role... roles) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get("src/test/resources/horizon", initialFilename));
        data = TsdbQueryRewriter.rewrite(data, Set.of(roles), SystemName.Public);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new JsonFormat(false).encode(baos, SlimeUtils.jsonToSlime(data));
        String expectedJson = Files.readString(Paths.get("src/test/resources/horizon", expectedFilename));

        assertEquals(expectedJson, baos.toString());
    }
}