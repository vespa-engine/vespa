// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/storage/distributor/operations/external/newest_replica.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/gtest/matchers/elements_are_distinct.h>

using namespace ::testing;
using storage::api::Timestamp;
using document::BucketId;

namespace storage::distributor {

TEST(NewestReplicaTest, equality_predicate_considers_all_fields) {
    std::vector elems = {
        NewestReplica::of(Timestamp(1000), BucketId(16, 1), 0, false, false),
        NewestReplica::of(Timestamp(1001), BucketId(16, 1), 0, false, false),
        NewestReplica::of(Timestamp(1000), BucketId(16, 2), 0, false, false),
        NewestReplica::of(Timestamp(1000), BucketId(16, 1), 1, false, false),
        NewestReplica::of(Timestamp(1000), BucketId(16, 1), 0, true,  false),
        NewestReplica::of(Timestamp(1000), BucketId(16, 1), 0, false, true)
    };
    EXPECT_THAT(elems, ElementsAreDistinct());
}

}
