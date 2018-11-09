// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/fdispatch/search/fnet_search.h>
#include <vespa/searchlib/engine/searchreply.h>

#include <vespa/log/log.h>
LOG_SETUP("search_coverage_test");

using namespace fdispatch;
using search::engine::SearchReply;

std::vector<FastS_FNET_SearchNode>
createNodes(uint32_t count) {
    std::vector<FastS_FNET_SearchNode> nodes;
    nodes.reserve(count);
    for (uint32_t partid(0); partid < count; partid++) {
        nodes.emplace_back(nullptr, partid);
    }
    return nodes;
}

void
query(FastS_FNET_SearchNode & node) {
    node.DirtySetChannelOnlyForTesting((FNET_Channel *) 1);
}

void
respond(FastS_FNET_SearchNode & node, size_t covered, size_t active, size_t soonActive, uint32_t degradeReason) {
    node._qresult = new FS4Packet_QUERYRESULTX();
    node._qresult->_coverageDocs = covered;
    node._qresult->_activeDocs = active;
    node._qresult->_soonActiveDocs = soonActive;
    node._qresult->_coverageDegradeReason = degradeReason;
}

void
respond(FastS_FNET_SearchNode & node, size_t covered, size_t active, size_t soonActive) {
    respond(node, covered, active, soonActive, 0);
}

void disconnectNodes(std::vector<FastS_FNET_SearchNode> & nodes) {
    for (auto & node : nodes) {
        node.DirtySetChannelOnlyForTesting(nullptr);
    }
}
TEST("testCoverageWhenAllNodesAreUp") {
    std::vector<FastS_FNET_SearchNode> nodes = createNodes(4);
    for (auto & node : nodes) {
        query(node);
        respond(node, 25, 30, 50);
    }
    FastS_SearchInfo si = FastS_FNET_Search::computeCoverage(nodes, 1, false);
    EXPECT_EQUAL(4u, si._nodesQueried);
    EXPECT_EQUAL(4u, si._nodesReplied);
    EXPECT_EQUAL(100u, si._coverageDocs);
    EXPECT_EQUAL(120u, si._activeDocs);
    EXPECT_EQUAL(200u, si._soonActiveDocs);
    EXPECT_EQUAL(0u, si._degradeReason);
    disconnectNodes(nodes);
}

TEST("testCoverageWhenNoNodesAreUp") {
    std::vector<FastS_FNET_SearchNode> nodes = createNodes(4);
    for (auto & node : nodes) {
        query(node);
    }
    FastS_SearchInfo si = FastS_FNET_Search::computeCoverage(nodes, 1, false);
    EXPECT_EQUAL(4u, si._nodesQueried);
    EXPECT_EQUAL(0u, si._nodesReplied);
    EXPECT_EQUAL(0u, si._coverageDocs);
    EXPECT_EQUAL(0u, si._activeDocs);
    EXPECT_EQUAL(0u, si._soonActiveDocs);
    EXPECT_EQUAL(SearchReply::Coverage::TIMEOUT, si._degradeReason);
    disconnectNodes(nodes);
}

TEST("testCoverageWhenNoNodesAreUpWithAdaptiveTimeout") {
    std::vector<FastS_FNET_SearchNode> nodes = createNodes(4);
    for (auto & node : nodes) {
        query(node);
    }
    FastS_SearchInfo si = FastS_FNET_Search::computeCoverage(nodes, 1, true);
    EXPECT_EQUAL(4u, si._nodesQueried);
    EXPECT_EQUAL(0u, si._nodesReplied);
    EXPECT_EQUAL(0u, si._coverageDocs);
    EXPECT_EQUAL(0u, si._activeDocs);
    EXPECT_EQUAL(0u, si._soonActiveDocs);
    EXPECT_EQUAL(SearchReply::Coverage::ADAPTIVE_TIMEOUT, si._degradeReason);
    disconnectNodes(nodes);
}

TEST("testCoverageWhen1NodesIsDown") {
    std::vector<FastS_FNET_SearchNode> nodes = createNodes(4);
    for (auto & node : nodes) {
        query(node);
    }
    respond(nodes[0], 25, 30, 50);
    respond(nodes[2], 25, 30, 50);
    respond(nodes[3], 25, 30, 50);

    FastS_SearchInfo si = FastS_FNET_Search::computeCoverage(nodes, 1, false);
    EXPECT_EQUAL(4u, si._nodesQueried);
    EXPECT_EQUAL(3u, si._nodesReplied);
    EXPECT_EQUAL(75u, si._coverageDocs);
    EXPECT_EQUAL(120u, si._activeDocs);
    EXPECT_EQUAL(200u, si._soonActiveDocs);
    EXPECT_EQUAL(SearchReply::Coverage::TIMEOUT, si._degradeReason);

    // Do not trigger dirty magic when you still have enough coverage in theory
    si = FastS_FNET_Search::computeCoverage(nodes, 2, false);
    EXPECT_EQUAL(4u, si._nodesQueried);
    EXPECT_EQUAL(3u, si._nodesReplied);
    EXPECT_EQUAL(75u, si._coverageDocs);
    EXPECT_EQUAL(90u, si._activeDocs);
    EXPECT_EQUAL(150u, si._soonActiveDocs);
    EXPECT_EQUAL(0u, si._degradeReason);
    disconnectNodes(nodes);
}

TEST("testCoverageWhen1NodeDoesnotReplyWithAdaptiveTimeout") {
    std::vector<FastS_FNET_SearchNode> nodes = createNodes(4);
    for (auto & node : nodes) {
        query(node);
    }
    respond(nodes[0], 25, 30, 50);
    respond(nodes[2], 25, 30, 50);
    respond(nodes[3], 25, 30, 50);

    FastS_SearchInfo si = FastS_FNET_Search::computeCoverage(nodes, 1, true);
    EXPECT_EQUAL(4u, si._nodesQueried);
    EXPECT_EQUAL(3u, si._nodesReplied);
    EXPECT_EQUAL(75u, si._coverageDocs);
    EXPECT_EQUAL(120u, si._activeDocs);
    EXPECT_EQUAL(200u, si._soonActiveDocs);
    EXPECT_EQUAL(SearchReply::Coverage::ADAPTIVE_TIMEOUT, si._degradeReason);
    disconnectNodes(nodes);
}


TEST_MAIN() { TEST_RUN_ALL(); }
