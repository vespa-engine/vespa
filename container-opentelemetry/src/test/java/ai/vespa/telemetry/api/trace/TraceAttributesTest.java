// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import io.opentelemetry.api.common.AttributeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the emitted attribute names. This is deliberate: once traces are queried, an attribute name is as much
 * an interface as a method signature, and renaming one silently breaks dashboards without breaking a build.
 *
 * <p>This class is pure vocabulary. Attributes are set with OpenTelemetry's own {@code Span.setAttribute}
 * at the span sites; there is no Vespa wrapper for it.</p>
 *
 * @author onur
 */
class TraceAttributesTest {

    @Test
    void attribute_names_and_types_are_pinned() {
        assertEquals("vespa.search.chain", TraceAttributes.SEARCH_CHAIN.getKey());
        assertEquals("vespa.query.rank_profile", TraceAttributes.QUERY_RANK_PROFILE.getKey());
        assertEquals("vespa.searcher.id", TraceAttributes.SEARCHER_ID.getKey());
        assertEquals("vespa.searcher.class", TraceAttributes.SEARCHER_CLASS.getKey());
        assertEquals("vespa.fill.summary_class", TraceAttributes.FILL_SUMMARY_CLASS.getKey());
        assertEquals("vespa.fill.hits", TraceAttributes.FILL_HITS.getKey());
        assertEquals("vespa.fill.skipped", TraceAttributes.FILL_SKIPPED.getKey());
        assertEquals("vespa.fill.retried", TraceAttributes.FILL_RETRIED.getKey());
        assertEquals("vespa.fill.retry_nodes", TraceAttributes.FILL_RETRY_NODES.getKey());
        assertEquals("vespa.fill.unfilled", TraceAttributes.FILL_UNFILLED.getKey());
        assertEquals("vespa.fill.retry", TraceAttributes.FILL_RETRY.getKey());
        assertEquals("vespa.fill.backends", TraceAttributes.FILL_BACKENDS.getKey());
        assertEquals("vespa.fill.nodes", TraceAttributes.FILL_NODES.getKey());
        assertEquals("vespa.fill.hits_filled", TraceAttributes.FILL_HITS_FILLED.getKey());
        assertEquals("vespa.fill.hits_requested", TraceAttributes.FILL_HITS_REQUESTED.getKey());
        assertEquals("vespa.schema", TraceAttributes.SCHEMA.getKey());
        assertEquals("vespa.error_code", TraceAttributes.ERROR_CODE.getKey());
        assertEquals("vespa.error_message", TraceAttributes.ERROR_MESSAGE.getKey());
        assertEquals("vespa.content.cluster", TraceAttributes.CONTENT_CLUSTER.getKey());
        assertEquals("vespa.content.node_key", TraceAttributes.CONTENT_NODE_KEY.getKey());
        assertEquals("vespa.content.node_host", TraceAttributes.CONTENT_NODE_HOST.getKey());
        assertEquals("vespa.content.group", TraceAttributes.CONTENT_GROUP.getKey());
        assertEquals("vespa.coverage.percentage", TraceAttributes.COVERAGE_PERCENTAGE.getKey());
        assertEquals("vespa.coverage.nodes", TraceAttributes.COVERAGE_NODES.getKey());
        assertEquals("vespa.coverage.nodes_tried", TraceAttributes.COVERAGE_NODES_TRIED.getKey());
        assertEquals("vespa.coverage.degraded", TraceAttributes.COVERAGE_DEGRADED.getKey());

        // OpenTelemetry semantic-convention names: deliberately NOT under vespa.*
        assertEquals("rpc.system", TraceAttributes.RPC_SYSTEM.getKey());
        assertEquals("rpc.method", TraceAttributes.RPC_METHOD_KEY.getKey());

        assertEquals(AttributeType.STRING, TraceAttributes.SEARCH_CHAIN.getType());
        assertEquals(AttributeType.LONG, TraceAttributes.FILL_HITS.getType());
        assertEquals(AttributeType.LONG, TraceAttributes.ERROR_CODE.getType());
        assertEquals(AttributeType.LONG, TraceAttributes.CONTENT_NODE_KEY.getType());
        assertEquals(AttributeType.BOOLEAN, TraceAttributes.COVERAGE_DEGRADED.getType());
        assertEquals(AttributeType.BOOLEAN, TraceAttributes.FILL_RETRY.getType());
        assertEquals(AttributeType.LONG, TraceAttributes.FILL_RETRY_NODES.getType());
    }
}
