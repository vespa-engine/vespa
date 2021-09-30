// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage::api {

namespace {

size_t hash_of(const vespalib::string & cluster, const lib::NodeType& type, uint16_t index) {
    return StorageMessageAddress(&cluster, type, index).internal_storage_hash();
}

}

TEST(StorageMessageAddressTest, storage_hash_covers_all_expected_fields) {
    EXPECT_EQ(hash_of("foo", lib::NodeType::STORAGE, 0),
              hash_of("foo", lib::NodeType::STORAGE, 0));
    EXPECT_EQ(hash_of("foo", lib::NodeType::DISTRIBUTOR, 0),
              hash_of("foo", lib::NodeType::DISTRIBUTOR, 0));
    EXPECT_EQ(hash_of("foo", lib::NodeType::STORAGE, 123),
              hash_of("foo", lib::NodeType::STORAGE, 123));
    EXPECT_EQ(hash_of("foo", lib::NodeType::STORAGE, 0),
              hash_of("bar", lib::NodeType::STORAGE, 0));

    // These tests are all true with extremely high probability, though they do
    // depend on a hash function that may inherently cause collisions.
    EXPECT_NE(hash_of("foo", lib::NodeType::STORAGE, 0),
              hash_of("foo", lib::NodeType::DISTRIBUTOR, 0));
    EXPECT_NE(hash_of("foo", lib::NodeType::STORAGE, 0),
              hash_of("foo", lib::NodeType::STORAGE, 1));

    EXPECT_EQ(16u, sizeof(StorageMessageAddress));
    EXPECT_EQ(72u, sizeof(StorageMessage));
    EXPECT_EQ(16u, sizeof(mbus::Trace));
}

} // storage::api
