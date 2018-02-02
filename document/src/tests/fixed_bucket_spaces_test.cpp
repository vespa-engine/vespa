// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <cppunit/extensions/HelperMacros.h>

namespace document {

struct FixedBucketSpacesTest : CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(FixedBucketSpacesTest);
    CPPUNIT_TEST(bucket_space_from_name_is_defined_for_default_space);
    CPPUNIT_TEST(bucket_space_from_name_is_defined_for_global_space);
    CPPUNIT_TEST(bucket_space_from_name_throws_exception_for_unknown_space);
    CPPUNIT_TEST(name_from_bucket_space_is_defined_for_default_space);
    CPPUNIT_TEST(name_from_bucket_space_is_defined_for_global_space);
    CPPUNIT_TEST(name_from_bucket_space_throws_exception_for_unknown_space);
    CPPUNIT_TEST_SUITE_END();

    void bucket_space_from_name_is_defined_for_default_space();
    void bucket_space_from_name_is_defined_for_global_space();
    void bucket_space_from_name_throws_exception_for_unknown_space();
    void name_from_bucket_space_is_defined_for_default_space();
    void name_from_bucket_space_is_defined_for_global_space();
    void name_from_bucket_space_throws_exception_for_unknown_space();
};

CPPUNIT_TEST_SUITE_REGISTRATION(FixedBucketSpacesTest);

void FixedBucketSpacesTest::bucket_space_from_name_is_defined_for_default_space() {
    CPPUNIT_ASSERT_EQUAL(FixedBucketSpaces::default_space(), FixedBucketSpaces::from_string("default"));
}

void FixedBucketSpacesTest::bucket_space_from_name_is_defined_for_global_space() {
    CPPUNIT_ASSERT_EQUAL(FixedBucketSpaces::global_space(), FixedBucketSpaces::from_string("global"));
}

void FixedBucketSpacesTest::bucket_space_from_name_throws_exception_for_unknown_space() {
    try {
        FixedBucketSpaces::from_string("banana");
        CPPUNIT_FAIL("Expected exception on unknown bucket space name");
    } catch (document::UnknownBucketSpaceException& e) {
    }
}

void FixedBucketSpacesTest::name_from_bucket_space_is_defined_for_default_space() {
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("default"),
                         FixedBucketSpaces::to_string(FixedBucketSpaces::default_space()));
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("default"), FixedBucketSpaces::default_space_name());
}

void FixedBucketSpacesTest::name_from_bucket_space_is_defined_for_global_space() {
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("global"),
                         FixedBucketSpaces::to_string(FixedBucketSpaces::global_space()));
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("global"), FixedBucketSpaces::global_space_name());
}

void FixedBucketSpacesTest::name_from_bucket_space_throws_exception_for_unknown_space() {
    try {
        FixedBucketSpaces::to_string(BucketSpace(4567));
        CPPUNIT_FAIL("Expected exception on unknown bucket space value");
    } catch (document::UnknownBucketSpaceException& e) {
    }
}

}
