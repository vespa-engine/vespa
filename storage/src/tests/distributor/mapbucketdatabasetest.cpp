// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <vespa/storage/storageutil/utils.h>
#include <tests/distributor/bucketdatabasetest.h>

namespace storage {
namespace distributor {

struct MapBucketDatabaseTest : public BucketDatabaseTest {
    MapBucketDatabase _db;

    virtual BucketDatabase& db() override { return _db; }

    CPPUNIT_TEST_SUITE(MapBucketDatabaseTest);
    SETUP_DATABASE_TESTS();
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MapBucketDatabaseTest);

}
}
