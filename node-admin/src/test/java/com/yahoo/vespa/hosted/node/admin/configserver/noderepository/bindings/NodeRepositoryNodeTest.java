// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.RealNodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.BaseReport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hakonhall
 */
public class NodeRepositoryNodeTest {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final NodeRepositoryNode node = new NodeRepositoryNode();
    private final NodeAttributes attributes = new NodeAttributes();


    /**
     * Test both how NodeRepositoryNode serialize, and the serialization of an empty NodeRepositoryNode
     * patched with a NodeAttributes, as they work in tandem:
     *   NodeAttributes -> NodeRepositoryNode -> JSON.
     */
    @Test
    void testReportsSerialization() {
        // Make sure we don't accidentally patch with "reports": null, as that actually means removing all reports.
        assertEquals(JsonInclude.Include.NON_NULL, NodeRepositoryNode.class.getAnnotation(JsonInclude.class).value());

        // Absent report and unmodified attributes => nothing about reports in JSON
        node.reports = null;
        assertNodeAndAttributes("{}");

        // Make sure we're able to patch with a null report value ("reportId": null), as that means removing the report.
        node.reports = new HashMap<>();
        node.reports.put("rid", null);
        attributes.withReportRemoved("rid");
        assertNodeAndAttributes("{\"reports\": {\"rid\": null}}");

        // Add ridTwo report to node
        ObjectNode reportJson = mapper.createObjectNode();
        reportJson.set(BaseReport.CREATED_FIELD, mapper.valueToTree(3));
        reportJson.set(BaseReport.DESCRIPTION_FIELD, mapper.valueToTree("desc"));
        node.reports.put("ridTwo", reportJson);

        // Add ridTwo report to attributes
        BaseReport reportTwo = new BaseReport(3L, "desc", null);
        attributes.withReport("ridTwo", reportTwo.toJsonNode());

        // Verify node serializes to expected, as well as attributes patched on node.
        assertNodeAndAttributes("{\"reports\": {\"rid\": null, \"ridTwo\": {\"createdMillis\": 3, \"description\": \"desc\"}}}");
    }

    private void assertNodeAndAttributes(String expectedJson) {
        assertNodeJson(node, expectedJson);
        assertNodeJson(RealNodeRepository.nodeRepositoryNodeFromNodeAttributes(attributes), expectedJson);
    }

    private void assertNodeJson(NodeRepositoryNode node, String json) {
        JsonNode expected = uncheck(() -> mapper.readTree(json));
        JsonNode actual = uncheck(() -> mapper.valueToTree(node));
        JsonTestHelper.assertJsonEquals(actual, expected);
    }
}