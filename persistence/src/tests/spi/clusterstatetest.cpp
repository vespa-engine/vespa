// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/test.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldvalue/document.h>
#include <gtest/gtest.h>

using storage::spi::test::makeSpiBucket;
using vespalib::Trinary;
using document::GlobalId;

namespace storage::spi {

TEST(ClusterStateTest, testClusterUp)
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    {
        lib::ClusterState s("version:1 storage:3 distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.clusterUp());
    }

    {
        lib::ClusterState s("version:1 storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.clusterUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(false, state.clusterUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(false, state.clusterUp());
    }
}

TEST(ClusterStateTest, testNodeUp)
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    {
        lib::ClusterState s("version:1 storage:3 distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(false, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 storage:3 .0.s:d distributor:3");
        ClusterState state(s, 1, d);
        EXPECT_EQ(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 distributor:3 .0.s:d");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(false, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:r distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:i distributor:3");
        ClusterState state(s, 0, d);
        EXPECT_EQ(true, state.nodeUp());
    }
}

namespace {

bool
nodeMarkedAsInitializingInState(const std::string& stateStr,
                                const lib::Distribution& d,
                                uint16_t node)
{
    lib::ClusterState s(stateStr);
    ClusterState state(s, node, d);
    return state.nodeInitializing();
}

} // anon ns

TEST(ClusterStateTest, testNodeInitializing)
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    EXPECT_TRUE(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 distributor:3", d, 0));
    EXPECT_TRUE(nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:i distributor:3", d, 0));
    EXPECT_TRUE(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:i distributor:3", d, 1));
    // To mirror nodeUp functionality, we ignore cluster state.
    EXPECT_TRUE(nodeMarkedAsInitializingInState(
            "version:1 cluster:d storage:3 .0.s:i distributor:3", d, 0));
    // Distributors don't technically have init state, but just go with it.
    EXPECT_TRUE(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 distributor:3 .0.s:i", d, 0));
    EXPECT_TRUE(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:d distributor:3", d, 0));
    EXPECT_TRUE(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:r distributor:3", d, 0));
    EXPECT_TRUE(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:m distributor:3", d, 0));
}

namespace {

lib::Distribution::DistributionConfig getCfg(uint16_t redundancy, uint16_t readyCopies)
{
    lib::Distribution::DistributionConfigBuilder config(lib::Distribution::getDefaultDistributionConfig(redundancy, 100).get());
    config.readyCopies = readyCopies;
    return config;
}

Trinary toTrinary(bool v) {
    return v ? Trinary::True : Trinary::False;
}

}

TEST(ClusterStateTest, testReady)
{
    lib::ClusterState s("version:1 storage:3 distributor:3");

    Bucket b(makeSpiBucket(document::BucketId(16, 1)));

    // With 3 copies, this bucket has ideal state 0, 2, 1

    // Nothing ready with 0 ready copies.
    {
        lib::Distribution d(getCfg(3, 0));
        ClusterState state(s, 0, d);
        EXPECT_EQ(toTrinary(false), state.shouldBeReady(b));
    }

    // Only node 0 with 1 ready copy.
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 1));
        ClusterState state(s, i, d);
        EXPECT_EQ(toTrinary(i == 0), state.shouldBeReady(b));
    }

    // All of them with 3 ready copies
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 3));
        ClusterState state(s, i, d);
        EXPECT_EQ(toTrinary(true), state.shouldBeReady(b));
    }

    // Node 0 and node 1 with 2 ready copies.
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 2));
        ClusterState state(s, i, d);
        EXPECT_EQ(toTrinary(i == 0 || i == 2), state.shouldBeReady(b));
    }

    // All of them with 3 ready copies
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 3));
        ClusterState state(s, i, d);
        EXPECT_EQ(toTrinary(true), state.shouldBeReady(b));
    }

    lib::ClusterState s2("version:1 storage:3 .0.s:d distributor:3");

    // The two others should be ready now
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 2));
        ClusterState state(s2, i, d);
        EXPECT_EQ(toTrinary(i == 1 || i == 2), state.shouldBeReady(b));
    }

    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 1));
        ClusterState state(s2, i, d);
        EXPECT_EQ(toTrinary(i == 2), state.shouldBeReady(b));
    }
}

namespace {

bool
node_marked_as_retired_in_state(const std::string& stateStr,
                                const lib::Distribution& d,
                                uint16_t node)
{
    lib::ClusterState s(stateStr);
    ClusterState state(s, node, d);
    return state.nodeRetired();
}

}

TEST(ClusterStateTest, can_infer_own_node_retired_state)
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    EXPECT_TRUE(!node_marked_as_retired_in_state("distributor:3 storage:3", d, 0));
    EXPECT_TRUE(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:i", d, 0));
    EXPECT_TRUE(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:d", d, 0));
    EXPECT_TRUE(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:m", d, 0));
    EXPECT_TRUE(node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:r", d, 0));
    EXPECT_TRUE(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:r", d, 1));
    EXPECT_TRUE(!node_marked_as_retired_in_state("distributor:3 storage:3 .1.s:r", d, 0));
}

namespace {

bool
node_marked_as_maintenance_in_state(const std::string& stateStr,
                                    const lib::Distribution& d,
                                    uint16_t node,
                                    bool maintenance_in_all_spaces)
{
    lib::ClusterState s(stateStr);
    ClusterState state(s, node, d, maintenance_in_all_spaces);
    return state.nodeMaintenance();
}

}

// We want to track the maintenance state for the _node_, not just the _bucket space_.
TEST(ClusterStateTest, node_maintenance_state_is_set_independent_of_bucket_space_state_string)
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    // Note: it doesn't actually matter what the cluster state string itself says here
    EXPECT_FALSE(node_marked_as_maintenance_in_state("distributor:3 storage:3", d, 0, false));
    EXPECT_TRUE(node_marked_as_maintenance_in_state("distributor:3 storage:3", d, 0, true));
    EXPECT_TRUE(node_marked_as_maintenance_in_state("distributor:3 storage:3 .0.s:d", d, 0, true));
    EXPECT_FALSE(node_marked_as_maintenance_in_state("distributor:3 storage:3 .0.s:m", d, 0, false));
}

TEST(DocEntryTest, test_basics) {
    EXPECT_EQ(24, sizeof(DocEntry));
}

TEST(DocEntryTest, test_meta_only) {
    DocEntry::UP e = DocEntry::create(Timestamp(9), DocumentMetaEnum::NONE);
    EXPECT_EQ(9, e->getTimestamp());
    EXPECT_FALSE(e->isRemove());
    EXPECT_EQ(24, e->getSize());
    EXPECT_EQ(nullptr, e->getDocument());
    EXPECT_EQ(nullptr, e->getDocumentId());
    EXPECT_EQ("", e->getDocumentType());
    EXPECT_EQ(GlobalId(), e->getGid());

    DocEntry::UP r = DocEntry::create(Timestamp(666), DocumentMetaEnum::REMOVE_ENTRY);
    EXPECT_EQ(666, r->getTimestamp());
    EXPECT_TRUE(r->isRemove());
}

TEST(DocEntryTest, test_docid_only) {
    DocEntry::UP e = DocEntry::create(Timestamp(9), DocumentMetaEnum::NONE, DocumentId("id:test:test::1"));
    EXPECT_EQ(9, e->getTimestamp());
    EXPECT_FALSE(e->isRemove());
    EXPECT_EQ(16, e->getSize());
    EXPECT_EQ(nullptr, e->getDocument());
    EXPECT_NE(nullptr, e->getDocumentId());
    EXPECT_EQ("test", e->getDocumentType());
    EXPECT_EQ(GlobalId::parse("gid(0xc4ca4238f9f9649222750be2)"), e->getGid());
}

TEST(DocEntryTest, test_doctype_and_gid) {
    DocEntry::UP e = DocEntry::create(Timestamp(9), DocumentMetaEnum::NONE, "doc_type", GlobalId::parse("gid(0xc4cef118f9f9649222750be2)"));
    EXPECT_EQ(9, e->getTimestamp());
    EXPECT_FALSE(e->isRemove());
    EXPECT_EQ(20, e->getSize());
    EXPECT_EQ(nullptr, e->getDocument());
    EXPECT_EQ(nullptr, e->getDocumentId());
    EXPECT_EQ("doc_type", e->getDocumentType());
    EXPECT_EQ(GlobalId::parse("gid(0xc4cef118f9f9649222750be2)"), e->getGid());
}

TEST(DocEntryTest, test_document_only) {
    document::TestDocMan testDocMan;
    DocEntry::UP e = DocEntry::create(Timestamp(9), testDocMan.createRandomDocument(0, 1000));
    EXPECT_EQ(9, e->getTimestamp());
    EXPECT_FALSE(e->isRemove());
    EXPECT_EQ(632, e->getSize());
    EXPECT_NE(nullptr, e->getDocument());
    EXPECT_NE(nullptr, e->getDocumentId());
    EXPECT_EQ("testdoctype1", e->getDocumentType());
    EXPECT_EQ(GlobalId::parse("gid(0x4bc7000087365609f22f1f4b)"), e->getGid());
}

}
