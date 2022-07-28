// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports;

import com.yahoo.test.json.JsonTestHelper;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.BaseReport.Type.SOFT_FAIL;
import static com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.BaseReport.Type.UNSPECIFIED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hakonhall
 */
public class BaseReportTest {
    private static final String JSON_1 = "{\"createdMillis\": 1, \"description\": \"desc\"}";
    private static final String JSON_2 = "{\"createdMillis\": 1, \"description\": \"desc\", \"type\": \"SOFT_FAIL\"}";

    @Test
    void testSerialization1() {
        JsonTestHelper.assertJsonEquals(new BaseReport(1L, "desc", SOFT_FAIL).toJsonNode(),
                JSON_2);
        JsonTestHelper.assertJsonEquals(new BaseReport(null, "desc", SOFT_FAIL).toJsonNode(),
                "{\"description\": \"desc\", \"type\": \"SOFT_FAIL\"}");
        JsonTestHelper.assertJsonEquals(new BaseReport(1L, null, SOFT_FAIL).toJsonNode(),
                "{\"createdMillis\": 1, \"type\": \"SOFT_FAIL\"}");
        JsonTestHelper.assertJsonEquals(new BaseReport(null, null, SOFT_FAIL).toJsonNode(),
                "{\"type\": \"SOFT_FAIL\"}");

        JsonTestHelper.assertJsonEquals(new BaseReport(1L, "desc", null).toJsonNode(),
                JSON_1);
        JsonTestHelper.assertJsonEquals(new BaseReport(null, "desc", null).toJsonNode(),
                "{\"description\": \"desc\"}");
        JsonTestHelper.assertJsonEquals(new BaseReport(1L, null, null).toJsonNode(),
                "{\"createdMillis\": 1}");
        JsonTestHelper.assertJsonEquals(new BaseReport(null, null, null).toJsonNode(),
                "{}");
    }

    @Test
    void testShouldUpdate() {
        BaseReport report = new BaseReport(1L, "desc", SOFT_FAIL);
        assertFalse(report.updates(report));

        // createdMillis is ignored
        assertFalse(new BaseReport(1L, "desc", SOFT_FAIL).updates(report));
        assertFalse(new BaseReport(2L, "desc", SOFT_FAIL).updates(report));
        assertFalse(new BaseReport(null, "desc", SOFT_FAIL).updates(report));

        // description is not ignored
        assertTrue(new BaseReport(1L, "desc 2", SOFT_FAIL).updates(report));
        assertTrue(new BaseReport(1L, null, SOFT_FAIL).updates(report));

        // type is not ignored
        assertTrue(new BaseReport(1L, "desc", null).updates(report));
        assertTrue(new BaseReport(1L, "desc", BaseReport.Type.HARD_FAIL).updates(report));
    }

    @Test
    void testJsonSerialization() {
        BaseReport report = BaseReport.fromJson(JSON_2);
        assertEquals(1L, (long) report.getCreatedMillisOrNull());
        assertEquals("desc", report.getDescriptionOrNull());
        assertEquals(SOFT_FAIL, report.getTypeOrNull());
        JsonTestHelper.assertJsonEquals(report.toJson(), JSON_2);
    }

    @Test
    void testUnspecifiedType() {
        BaseReport report = new BaseReport(1L, "desc", null);
        assertNull(report.getTypeOrNull());
        assertEquals(UNSPECIFIED, report.getType());
    }
}