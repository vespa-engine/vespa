// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for dummypersistence.

#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config-stor-distribution.h>


using namespace storage::spi;
using namespace storage;
using document::test::makeBucketSpace;
using dummy::BucketContent;

namespace {

struct Fixture {
    BucketContent content;

    void insert(DocumentId id, Timestamp timestamp, int meta_flags) {
        content.insert(DocEntry::UP(new DocEntry(timestamp, meta_flags, id)));
    }

    Fixture() {
        insert(DocumentId("doc:test:3"), Timestamp(3), NONE);
        insert(DocumentId("doc:test:1"), Timestamp(1), NONE);
        insert(DocumentId("doc:test:2"), Timestamp(2), NONE);
    }
};

TEST("require that empty BucketContent behaves") {
    BucketContent content;
    EXPECT_FALSE(content.hasTimestamp(Timestamp(1)));
    EXPECT_FALSE(content.getEntry(Timestamp(1)).get());
    EXPECT_FALSE(content.getEntry(DocumentId("doc:test:1")).get());
}

TEST_F("require that BucketContent can retrieve by timestamp", Fixture) {
    DocEntry::SP entry = f.content.getEntry(Timestamp(1));
    ASSERT_TRUE(entry.get());
    ASSERT_TRUE(entry->getDocumentId());
    ASSERT_EQUAL("doc:test:1", entry->getDocumentId()->toString());
}

TEST_F("require that BucketContent can retrieve by doc id", Fixture) {
    DocEntry::SP entry = f.content.getEntry(DocumentId("doc:test:2"));
    ASSERT_TRUE(entry.get());
    ASSERT_TRUE(entry->getDocumentId());
    ASSERT_EQUAL("doc:test:2", entry->getDocumentId()->toString());
}

TEST_F("require that BucketContent can check a timestamp", Fixture) {
    EXPECT_FALSE(f.content.hasTimestamp(Timestamp(0)));
    EXPECT_TRUE(f.content.hasTimestamp(Timestamp(1)));
    EXPECT_TRUE(f.content.hasTimestamp(Timestamp(2)));
    EXPECT_TRUE(f.content.hasTimestamp(Timestamp(3)));
    EXPECT_FALSE(f.content.hasTimestamp(Timestamp(4)));
}

TEST_F("require that BucketContent can provide bucket info", Fixture) {
    uint32_t lastChecksum = 0;
    EXPECT_NOT_EQUAL(lastChecksum, f.content.getBucketInfo().getChecksum());
    lastChecksum = f.content.getBucketInfo().getChecksum();
    f.insert(DocumentId("doc:test:3"), Timestamp(4), NONE);
    EXPECT_NOT_EQUAL(lastChecksum, f.content.getBucketInfo().getChecksum());
    lastChecksum = f.content.getBucketInfo().getChecksum();
    f.insert(DocumentId("doc:test:2"), Timestamp(5), REMOVE_ENTRY);
    EXPECT_NOT_EQUAL(lastChecksum, f.content.getBucketInfo().getChecksum());
    f.insert(DocumentId("doc:test:1"), Timestamp(6), REMOVE_ENTRY);
    f.insert(DocumentId("doc:test:3"), Timestamp(7), REMOVE_ENTRY);
    EXPECT_EQUAL(0u, f.content.getBucketInfo().getChecksum());
}

TEST_F("require that setClusterState sets the cluster state", Fixture) {
    lib::ClusterState s("version:1 storage:3 .1.s:d distributor:3");
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));
    ClusterState state(s, 1, d);

    std::shared_ptr<const document::DocumentTypeRepo> repo;
    dummy::DummyPersistence provider(repo);
    provider.setClusterState(makeBucketSpace(), state);

    EXPECT_EQUAL(false, provider.getClusterState().nodeUp());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
