// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports;

import com.yahoo.test.json.JsonTestHelper;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class BaseReportTest {
    @Test
    public void testSerialization() {
        BaseReport report = new BaseReport(1L, "desc");
        JsonTestHelper.assertJsonEquals(new BaseReport(1L, "desc").toJsonNode(),
                "{\"createdMillis\": 1, \"description\": \"desc\"}");
        JsonTestHelper.assertJsonEquals(new BaseReport(null, "desc").toJsonNode(),
                "{\"description\": \"desc\"}");
        JsonTestHelper.assertJsonEquals(new BaseReport(1L, null).toJsonNode(),
                "{\"createdMillis\": 1}");
        JsonTestHelper.assertJsonEquals(new BaseReport(null, null).toJsonNode(),
                "{}");
    }

    @Test
    public void testShouldUpdate() {
        BaseReport report = new BaseReport(1L, "desc");
        assertFalse(report.updates(report));
        assertFalse(new BaseReport(1L, "desc").updates(report));
        assertFalse(new BaseReport(2L, "desc").updates(report));
        assertFalse(new BaseReport(null, "desc").updates(report));

        assertTrue(new BaseReport(1L, "desc 2").updates(report));
        assertTrue(new BaseReport(1L, null).updates(report));
    }
}