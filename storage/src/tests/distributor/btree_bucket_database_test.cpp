// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/btree_bucket_database.h>
#include <tests/distributor/bucketdatabasetest.h>

namespace storage::distributor {

INSTANTIATE_TEST_CASE_P(BTreeDatabase, BucketDatabaseTest,
                        ::testing::Values(std::make_shared<BTreeBucketDatabase>()));

}

