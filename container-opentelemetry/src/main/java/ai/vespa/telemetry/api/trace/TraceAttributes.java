// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import io.opentelemetry.api.common.AttributeKey;

/**
 * The attribute vocabulary of Vespa-internal spans: every key is declared here once, so a name is never
 * spelled twice and the whole emitted vocabulary can be reviewed in one place — which is what makes
 * cardinality and privacy reviewable at all.
 *
 * <p>Keys are pre-allocated. {@link io.opentelemetry.api.trace.Span#setAttribute} has String-keyed
 * overloads, but each call would build a fresh {@link AttributeKey}.</p>
 *
 * <p>NEVER declare a key that carries user content — query text, YQL, document ids or hit contents.</p>
 *
 * @author onur
 */
public final class TraceAttributes {

    private TraceAttributes() { }

    /** Id of the search chain being executed, e.g. "default". */
    public static final AttributeKey<String> SEARCH_CHAIN = AttributeKey.stringKey("vespa.search.chain");

    /** Rank profile the query resolves to; "default" when the query sets none. */
    public static final AttributeKey<String> QUERY_RANK_PROFILE = AttributeKey.stringKey("vespa.query.rank_profile");

    /** Component id of the searcher. */
    public static final AttributeKey<String> SEARCHER_ID = AttributeKey.stringKey("vespa.searcher.id");

    /** Implementation class of the searcher, which separates third-party searchers sharing an id pattern. */
    public static final AttributeKey<String> SEARCHER_CLASS = AttributeKey.stringKey("vespa.searcher.class");

    /** Summary class being filled. */
    public static final AttributeKey<String> FILL_SUMMARY_CLASS = AttributeKey.stringKey("vespa.fill.summary_class");

    /** Number of concrete hits the fill covers. */
    public static final AttributeKey<Long> FILL_HITS = AttributeKey.longKey("vespa.fill.hits");

    /** Hits that came back with no docsum after the first wave of docsum requests. Non-zero means the content
     *  layer returned empty summaries, which is what a redistribution in progress looks like from here. */
    public static final AttributeKey<Long> FILL_SKIPPED = AttributeKey.longKey("vespa.fill.skipped");

    /** Whether the retry wave was sent. FALSE means it was considered and DECLINED by the retry limit, so those
     *  docsums are lost for this query; ABSENT means no hit was ever missing and the question never arose. */
    public static final AttributeKey<Boolean> FILL_RETRIED = AttributeKey.booleanKey("vespa.fill.retried");

    /** How wide the retry fanned out. Vespa does not know which node now holds a moved document, so it asks
     *  every other known node - this can be as large as the cluster. */
    public static final AttributeKey<Long> FILL_RETRY_NODES = AttributeKey.longKey("vespa.fill.retry_nodes");

    /** Hits still missing a docsum after the retry. */
    public static final AttributeKey<Long> FILL_UNFILLED = AttributeKey.longKey("vespa.fill.unfilled");

    /** Whether one docsum request belongs to the retry wave rather than the first. */
    public static final AttributeKey<Boolean> FILL_RETRY = AttributeKey.booleanKey("vespa.fill.retry");

    /** How many content-cluster backends one fill asked. */
    public static final AttributeKey<Long> FILL_BACKENDS = AttributeKey.longKey("vespa.fill.backends");

    /** How many content nodes one dispatch of a fill fanned out to. Determined by which nodes own the surviving
     *  hits, NOT by the cluster size - unlike the search side, where every node in the group is asked. */
    public static final AttributeKey<Long> FILL_NODES = AttributeKey.longKey("vespa.fill.nodes");

    /** Hits that actually got a docsum. Compare against {@link #FILL_HITS} to see a partial fill. */
    public static final AttributeKey<Long> FILL_HITS_FILLED = AttributeKey.longKey("vespa.fill.hits_filled");

    /** How many docsums one content node was asked for. */
    public static final AttributeKey<Long> FILL_HITS_REQUESTED = AttributeKey.longKey("vespa.fill.hits_requested");

    /** The single schema a dispatch is searching. One query can produce several concurrent dispatches — one per
     *  schema, one per grouping pass — so without this their spans are indistinguishable. */
    public static final AttributeKey<String> SCHEMA = AttributeKey.stringKey("vespa.schema");

    /** Vespa error code of a failed operation, e.g. 12 for a timeout. */
    public static final AttributeKey<Long> ERROR_CODE = AttributeKey.longKey("vespa.error_code");

    /** The SHORT error message — a fixed literal per error code in every {@code ErrorMessage} factory
     *  ("Timed out", "Backend communication error", …).
     *
     *  <p>NEVER the DETAILED message: that is the caller-supplied half, and
     *  {@code VespaBackend:144} passes the request URI into it, which carries the query text.</p> */
    public static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("vespa.error_message");

    /** Name of the content cluster a dispatch is talking to. */
    public static final AttributeKey<String> CONTENT_CLUSTER = AttributeKey.stringKey("vespa.content.cluster");

    /** Distribution key of the content node — the identifier operators actually work with. */
    public static final AttributeKey<Long> CONTENT_NODE_KEY = AttributeKey.longKey("vespa.content.node_key");

    /** Host the content node runs on. */
    public static final AttributeKey<String> CONTENT_NODE_HOST = AttributeKey.stringKey("vespa.content.node_host");

    /** Which replica group of the cluster was queried. */
    public static final AttributeKey<Long> CONTENT_GROUP = AttributeKey.longKey("vespa.content.group");

    /** OpenTelemetry semantic convention: the RPC system in use. */
    public static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

    /** OpenTelemetry semantic convention: the RPC method invoked. Named with a KEY suffix only because
     *  {@code RpcSearchInvoker} already has a private constant {@code RPC_METHOD} holding the method's VALUE,
     *  and renaming existing code for the convenience of instrumentation is the wrong way round. */
    public static final AttributeKey<String> RPC_METHOD_KEY = AttributeKey.stringKey("rpc.method");

    /** Percentage of the corpus the result actually covers; below 100 means the query was degraded. */
    public static final AttributeKey<Long> COVERAGE_PERCENTAGE = AttributeKey.longKey("vespa.coverage.percentage");

    /** Nodes that answered. */
    public static final AttributeKey<Long> COVERAGE_NODES = AttributeKey.longKey("vespa.coverage.nodes");

    /** Nodes that were asked, whether or not they answered. */
    public static final AttributeKey<Long> COVERAGE_NODES_TRIED = AttributeKey.longKey("vespa.coverage.nodes_tried");

    /** Whether the result is degraded, by any cause — timeout, adaptive timeout, match phase or ANN timeout. */
    public static final AttributeKey<Boolean> COVERAGE_DEGRADED = AttributeKey.booleanKey("vespa.coverage.degraded");

}
