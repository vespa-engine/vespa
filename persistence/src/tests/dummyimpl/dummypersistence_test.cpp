// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for dummypersistence.

#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace storage::spi;
using namespace storage;
using document::test::makeBucketSpace;
using dummy::BucketContent;

namespace {

struct Fixture {
    BucketContent content;

    void insert(DocumentId id, Timestamp timestamp, DocumentMetaEnum meta_flags) {
        content.insert(DocEntry::create(timestamp, meta_flags, id));
    }

    Fixture() {
        insert(DocumentId("id:ns:type::test:3"), Timestamp(3), DocumentMetaEnum::NONE);
        insert(DocumentId("id:ns:type::test:1"), Timestamp(1), DocumentMetaEnum::NONE);
        insert(DocumentId("id:ns:type::test:2"), Timestamp(2), DocumentMetaEnum::NONE);
    }
};

TEST(DummyPersistenceTest, require_that_empty_BucketContent_behaves) {
    BucketContent content;
    EXPECT_FALSE(content.hasTimestamp(Timestamp(1)));
    EXPECT_FALSE(content.getEntry(Timestamp(1)).get());
    EXPECT_FALSE(content.getEntry(DocumentId("id:ns:type::test:1")).get());
}

TEST(DummyPersistenceTest, require_that_BucketContent_can_retrieve_by_timestamp) {
    Fixture f;
    DocEntry::SP entry = f.content.getEntry(Timestamp(1));
    ASSERT_TRUE(entry.get());
    ASSERT_TRUE(entry->getDocumentId());
    ASSERT_EQ("id:ns:type::test:1", entry->getDocumentId()->toString());
}

TEST(DummyPersistenceTest, require_that_BucketContent_can_retrieve_by_doc_id) {
    Fixture f;
    DocEntry::SP entry = f.content.getEntry(DocumentId("id:ns:type::test:2"));
    ASSERT_TRUE(entry.get());
    ASSERT_TRUE(entry->getDocumentId());
    ASSERT_EQ("id:ns:type::test:2", entry->getDocumentId()->toString());
}

TEST(DummyPersistenceTest, require_that_BucketContent_can_check_a_timestamp) {
    Fixture f;
    EXPECT_FALSE(f.content.hasTimestamp(Timestamp(0)));
    EXPECT_TRUE(f.content.hasTimestamp(Timestamp(1)));
    EXPECT_TRUE(f.content.hasTimestamp(Timestamp(2)));
    EXPECT_TRUE(f.content.hasTimestamp(Timestamp(3)));
    EXPECT_FALSE(f.content.hasTimestamp(Timestamp(4)));
}

TEST(DummyPersistenceTest, require_that_BucketContent_can_provide_bucket_info) {
    Fixture f;
    uint32_t lastChecksum = 0;
    EXPECT_NE(lastChecksum, f.content.getBucketInfo().getChecksum());
    lastChecksum = f.content.getBucketInfo().getChecksum();
    f.insert(DocumentId("id:ns:type::test:3"), Timestamp(4), DocumentMetaEnum::NONE);
    EXPECT_NE(lastChecksum, f.content.getBucketInfo().getChecksum());
    lastChecksum = f.content.getBucketInfo().getChecksum();
    f.insert(DocumentId("id:ns:type::test:2"), Timestamp(5), DocumentMetaEnum::REMOVE_ENTRY);
    EXPECT_NE(lastChecksum, f.content.getBucketInfo().getChecksum());
    f.insert(DocumentId("id:ns:type::test:1"), Timestamp(6), DocumentMetaEnum::REMOVE_ENTRY);
    f.insert(DocumentId("id:ns:type::test:3"), Timestamp(7), DocumentMetaEnum::REMOVE_ENTRY);
    EXPECT_EQ(0u, f.content.getBucketInfo().getChecksum());
}

TEST(DummyPersistenceTest, require_that_setClusterState_sets_the_cluster_state) {
    Fixture f;
    lib::ClusterState s("version:1 storage:3 .1.s:d distributor:3");
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));
    ClusterState state(s, 1, d);

    std::shared_ptr<const document::DocumentTypeRepo> repo;
    dummy::DummyPersistence provider(repo);
    provider.setClusterState(makeBucketSpace(), state);

    EXPECT_EQ(false, provider.getClusterState().nodeUp());
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
