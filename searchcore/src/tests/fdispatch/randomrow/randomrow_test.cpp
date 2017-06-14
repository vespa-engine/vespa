// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("randomrow_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/fdispatch/search/plain_dataset.h>

using fdispatch::StateOfRows;

TEST("requireThatEmpyStateReturnsRowZero")
{
    StateOfRows s(1, 1.0, 1000);
    EXPECT_EQUAL(0u, s.getRandomWeightedRow());
    EXPECT_EQUAL(1.0, s.getRowState(0).getAverageSearchTime());
}

TEST("requireThatDecayWorks")
{
    StateOfRows s(1, 1.0, 1000);
    s.updateSearchTime(1.0, 0);
    EXPECT_EQUAL(1.0, s.getRowState(0).getAverageSearchTime());
    s.updateSearchTime(2.0, 0);
    EXPECT_EQUAL(1.001, s.getRowState(0).getAverageSearchTime());
    s.updateSearchTime(2.0, 0);
    EXPECT_APPROX(1.002, s.getRowState(0).getAverageSearchTime(), 0.0001);
    s.updateSearchTime(0.1, 0);
    s.updateSearchTime(0.1, 0);
    s.updateSearchTime(0.1, 0);
    s.updateSearchTime(0.1, 0);
    EXPECT_APPROX(0.998396, s.getRowState(0).getAverageSearchTime(), 0.000001);
}

TEST("requireWeightedSelectionWorks")
{
    StateOfRows s(5, 1.0, 1000);
    EXPECT_EQUAL(0u, s.getWeightedNode(-0.1));
    EXPECT_EQUAL(0u, s.getWeightedNode(0.0));
    EXPECT_EQUAL(0u, s.getWeightedNode(0.1));
    EXPECT_EQUAL(1u, s.getWeightedNode(0.2));
    EXPECT_EQUAL(1u, s.getWeightedNode(0.39));
    EXPECT_EQUAL(2u, s.getWeightedNode(0.4));
    EXPECT_EQUAL(3u, s.getWeightedNode(0.6));
    EXPECT_EQUAL(4u, s.getWeightedNode(0.8));
    EXPECT_EQUAL(4u, s.getWeightedNode(2.0));
}

TEST("requireWeightedSelectionWorksFineWithDifferentWeights")
{
    StateOfRows s(5, 1.0, 1000);
    s.getRowState(0).setAverageSearchTime(0.1);
    s.getRowState(1).setAverageSearchTime(0.2);
    s.getRowState(2).setAverageSearchTime(0.3);
    s.getRowState(3).setAverageSearchTime(0.4);
    s.getRowState(4).setAverageSearchTime(0.5);
    EXPECT_EQUAL(0.1, s.getRowState(0).getAverageSearchTime());
    EXPECT_EQUAL(0.2, s.getRowState(1).getAverageSearchTime());
    EXPECT_EQUAL(0.3, s.getRowState(2).getAverageSearchTime());
    EXPECT_EQUAL(0.4, s.getRowState(3).getAverageSearchTime());
    EXPECT_EQUAL(0.5, s.getRowState(4).getAverageSearchTime());
    EXPECT_EQUAL(0u, s.getWeightedNode(-0.1));
    EXPECT_EQUAL(0u, s.getWeightedNode(0.0));
    EXPECT_EQUAL(0u, s.getWeightedNode(0.4379));
    EXPECT_EQUAL(1u, s.getWeightedNode(0.4380));
    EXPECT_EQUAL(1u, s.getWeightedNode(0.6569));
    EXPECT_EQUAL(2u, s.getWeightedNode(0.6570));
    EXPECT_EQUAL(2u, s.getWeightedNode(0.8029));
    EXPECT_EQUAL(3u, s.getWeightedNode(0.8030));
    EXPECT_EQUAL(3u, s.getWeightedNode(0.9124));
    EXPECT_EQUAL(4u, s.getWeightedNode(0.9125));
    EXPECT_EQUAL(4u, s.getWeightedNode(2.0));
}

TEST("require randomness")
{
    StateOfRows s(3, 1.0, 1000);
    s.getRowState(0).setAverageSearchTime(1.0);
    s.getRowState(1).setAverageSearchTime(1.0);
    s.getRowState(2).setAverageSearchTime(1.0);
    size_t counts[3] = {0,0,0};
    for (size_t i(0); i < 1000; i++) {
        counts[s.getRandomWeightedRow()]++;
    }
    EXPECT_EQUAL(322ul, counts[0]);
    EXPECT_EQUAL(345ul, counts[1]);
    EXPECT_EQUAL(333ul, counts[2]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
