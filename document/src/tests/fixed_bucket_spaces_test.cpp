// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <gtest/gtest.h>

namespace document {

TEST(FixedBucketSpacesTest, bucket_space_from_name_is_defined_for_default_space)
{
    EXPECT_EQ(FixedBucketSpaces::default_space(), FixedBucketSpaces::from_string("default"));
}

TEST(FixedBucketSpacesTest, bucket_space_from_name_is_defined_for_global_space)
{
    EXPECT_EQ(FixedBucketSpaces::global_space(), FixedBucketSpaces::from_string("global"));
}

TEST(FixedBucketSpacesTest, bucket_space_from_name_throws_exception_for_unknown_space)
{
    try {
        FixedBucketSpaces::from_string("banana");
        FAIL() << "Expected exception on unknown bucket space name";
    } catch (document::UnknownBucketSpaceException& e) {
    }
}

TEST(FixedBucketSpacesTest, name_from_bucket_space_is_defined_for_default_space)
{
    EXPECT_EQ(vespalib::stringref("default"),
                         FixedBucketSpaces::to_string(FixedBucketSpaces::default_space()));
    EXPECT_EQ(vespalib::stringref("default"), FixedBucketSpaces::default_space_name());
}

TEST(FixedBucketSpacesTest, name_from_bucket_space_is_defined_for_global_space)
{
    EXPECT_EQ(vespalib::stringref("global"),
                         FixedBucketSpaces::to_string(FixedBucketSpaces::global_space()));
    EXPECT_EQ(vespalib::stringref("global"), FixedBucketSpaces::global_space_name());
}

TEST(FixedBucketSpacesTest, name_from_bucket_space_throws_exception_for_unknown_space)
{
    try {
        FixedBucketSpaces::to_string(BucketSpace(4567));
        FAIL() << "Expected exception on unknown bucket space value";
    } catch (document::UnknownBucketSpaceException& e) {
    }
}

}
