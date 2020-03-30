// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <tests/distributor/bucketdatabasetest.h>

namespace storage::distributor {

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(MapDatabase, BucketDatabaseTest,
                                     ::testing::Values(std::make_shared<MapBucketDatabase>()));

}
