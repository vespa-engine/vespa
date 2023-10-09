// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/configurable_bucket_resolver.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage {

using document::DocumentId;
using namespace ::testing;

struct ConfigurableBucketResolverTest : Test {
    using BucketSpaceMapping = ConfigurableBucketResolver::BucketSpaceMapping;

    BucketSpaceMapping create_simple_mapping() {
        return {{"foo", document::FixedBucketSpaces::default_space()},
                {"bar", document::FixedBucketSpaces::default_space()},
                {"baz", document::FixedBucketSpaces::global_space()}};
    }

    ConfigurableBucketResolver create_empty_resolver() {
        return ConfigurableBucketResolver({});
    }

    ConfigurableBucketResolver create_simple_resolver() {
        return ConfigurableBucketResolver(create_simple_mapping());
    }
};

// TODO reduce overlap with FixedBucketSpacesTest
TEST_F(ConfigurableBucketResolverTest, bucket_space_from_name_is_defined_for_default_space) {
    auto space = create_empty_resolver().bucketSpaceFromName("default");
    EXPECT_EQ(document::FixedBucketSpaces::default_space(), space);
}

TEST_F(ConfigurableBucketResolverTest, bucket_space_from_name_is_defined_for_global_space) {
    auto space = create_empty_resolver().bucketSpaceFromName("global");
    EXPECT_EQ(document::FixedBucketSpaces::global_space(), space);
}

TEST_F(ConfigurableBucketResolverTest, bucket_space_from_name_throws_exception_for_unknown_space) {
    EXPECT_THROW(create_empty_resolver().bucketSpaceFromName("bjarne"),
                 document::UnknownBucketSpaceException);
}

TEST_F(ConfigurableBucketResolverTest, name_from_bucket_space_is_defined_for_default_space) {
    EXPECT_EQ("default", create_empty_resolver().nameFromBucketSpace(document::FixedBucketSpaces::default_space()));
}

TEST_F(ConfigurableBucketResolverTest, name_from_bucket_space_is_defined_for_global_space) {
    EXPECT_EQ("global", create_empty_resolver().nameFromBucketSpace(document::FixedBucketSpaces::global_space()));
}

TEST_F(ConfigurableBucketResolverTest, name_from_bucket_space_throws_exception_for_unknown_space) {
    EXPECT_THROW(create_empty_resolver().nameFromBucketSpace(document::BucketSpace(1234)),
                 document::UnknownBucketSpaceException);
}

TEST_F(ConfigurableBucketResolverTest, known_bucket_space_is_resolved_from_document_id) {
    auto resolver = create_simple_resolver();
    EXPECT_EQ(document::FixedBucketSpaces::default_space(),
              resolver.bucketFromId(DocumentId("id::foo::xyz")).getBucketSpace());
    EXPECT_EQ(document::FixedBucketSpaces::default_space(),
              resolver.bucketFromId(DocumentId("id::bar::xyz")).getBucketSpace());
    EXPECT_EQ(document::FixedBucketSpaces::global_space(),
              resolver.bucketFromId(DocumentId("id::baz::xyz")).getBucketSpace());
}

TEST_F(ConfigurableBucketResolverTest, unknown_bucket_space_in_id_throws_exception) {
    EXPECT_THROW(create_simple_resolver().bucketFromId(DocumentId("id::bjarne::xyz")),
                 document::UnknownBucketSpaceException);
}

using BucketSpacesConfigBuilder = vespa::config::content::core::BucketspacesConfigBuilder;

namespace {

BucketSpacesConfigBuilder::Documenttype make_doc_type(vespalib::stringref name, vespalib::stringref space) {
    BucketSpacesConfigBuilder::Documenttype doc_type;
    doc_type.name = name;
    doc_type.bucketspace = space;
    return doc_type;
}

}

TEST_F(ConfigurableBucketResolverTest, can_create_resolver_from_bucket_space_config) {
    BucketSpacesConfigBuilder builder;
    builder.documenttype.emplace_back(make_doc_type("foo", "default"));
    builder.documenttype.emplace_back(make_doc_type("bar", "global"));
    builder.documenttype.emplace_back(make_doc_type("baz", "global"));
    auto resolver = ConfigurableBucketResolver::from_config(builder);
    EXPECT_EQ(document::FixedBucketSpaces::default_space(),
              resolver->bucketFromId(DocumentId("id::foo::xyz")).getBucketSpace());
    EXPECT_EQ(document::FixedBucketSpaces::global_space(),
              resolver->bucketFromId(DocumentId("id::bar::xyz")).getBucketSpace());
    EXPECT_EQ(document::FixedBucketSpaces::global_space(),
              resolver->bucketFromId(DocumentId("id::baz::xyz")).getBucketSpace());
}

}

