// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <tests/distributor/bucketdatabasetest.h>

namespace storage {
namespace distributor {

struct MapBucketDatabaseTest : public BucketDatabaseTest {
    MapBucketDatabase _db;
    BucketDatabase& db() override { return _db; };

    CPPUNIT_TEST_SUITE(MapBucketDatabaseTest);
    SETUP_DATABASE_TESTS();
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MapBucketDatabaseTest);

}
}
