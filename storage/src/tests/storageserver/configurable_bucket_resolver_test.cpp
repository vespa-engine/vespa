// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/configurable_bucket_resolver.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <cppunit/extensions/HelperMacros.h>

namespace storage {

using document::DocumentId;

struct ConfigurableBucketResolverTest : CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(ConfigurableBucketResolverTest);
    CPPUNIT_TEST(bucket_space_from_name_is_defined_for_default_space);
    CPPUNIT_TEST(bucket_space_from_name_is_defined_for_global_space);
    CPPUNIT_TEST(bucket_space_from_name_throws_exception_for_unknown_space);
    CPPUNIT_TEST(name_from_bucket_space_is_defined_for_default_space);
    CPPUNIT_TEST(name_from_bucket_space_is_defined_for_global_space);
    CPPUNIT_TEST(name_from_bucket_space_throws_exception_for_unknown_space);
    CPPUNIT_TEST(known_bucket_space_is_resolved_from_document_id);
    CPPUNIT_TEST(unknown_bucket_space_in_id_throws_exception);
    CPPUNIT_TEST(can_create_resolver_from_bucket_space_config);
    CPPUNIT_TEST_SUITE_END();

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

    void bucket_space_from_name_is_defined_for_default_space();
    void bucket_space_from_name_is_defined_for_global_space();
    void bucket_space_from_name_throws_exception_for_unknown_space();
    void name_from_bucket_space_is_defined_for_default_space();
    void name_from_bucket_space_is_defined_for_global_space();
    void name_from_bucket_space_throws_exception_for_unknown_space();
    void known_bucket_space_is_resolved_from_document_id();
    void unknown_bucket_space_in_id_throws_exception();
    void can_create_resolver_from_bucket_space_config();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ConfigurableBucketResolverTest);

// TODO reduce overlap with FixedBucketSpacesTest
void ConfigurableBucketResolverTest::bucket_space_from_name_is_defined_for_default_space() {
    auto space = create_empty_resolver().bucketSpaceFromName("default");
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::default_space(), space);
}

void ConfigurableBucketResolverTest::bucket_space_from_name_is_defined_for_global_space() {
    auto space = create_empty_resolver().bucketSpaceFromName("global");
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::global_space(), space);
}

void ConfigurableBucketResolverTest::bucket_space_from_name_throws_exception_for_unknown_space() {
    try {
        create_empty_resolver().bucketSpaceFromName("bjarne");
        CPPUNIT_FAIL("Expected exception on unknown bucket space name");
    } catch (document::UnknownBucketSpaceException& e) {
    }
}

void ConfigurableBucketResolverTest::name_from_bucket_space_is_defined_for_default_space() {
    CPPUNIT_ASSERT_EQUAL(vespalib::string("default"),
                         create_empty_resolver().nameFromBucketSpace(document::FixedBucketSpaces::default_space()));
}

void ConfigurableBucketResolverTest::name_from_bucket_space_is_defined_for_global_space() {
    CPPUNIT_ASSERT_EQUAL(vespalib::string("global"),
                         create_empty_resolver().nameFromBucketSpace(document::FixedBucketSpaces::global_space()));
}

void ConfigurableBucketResolverTest::name_from_bucket_space_throws_exception_for_unknown_space() {
    try {
        create_empty_resolver().nameFromBucketSpace(document::BucketSpace(1234));
        CPPUNIT_FAIL("Expected exception on unknown bucket space value");
    } catch (document::UnknownBucketSpaceException& e) {
    }
}

void ConfigurableBucketResolverTest::known_bucket_space_is_resolved_from_document_id() {
    auto resolver = create_simple_resolver();
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::default_space(),
                         resolver.bucketFromId(DocumentId("id::foo::xyz")).getBucketSpace());
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::default_space(),
                         resolver.bucketFromId(DocumentId("id::bar::xyz")).getBucketSpace());
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::global_space(),
                         resolver.bucketFromId(DocumentId("id::baz::xyz")).getBucketSpace());
}

void ConfigurableBucketResolverTest::unknown_bucket_space_in_id_throws_exception() {
    try {
        create_simple_resolver().bucketFromId(DocumentId("id::bjarne::xyz"));
        CPPUNIT_FAIL("Expected exception on unknown document type -> bucket space mapping");
    } catch (document::UnknownBucketSpaceException& e) {
    }
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

void ConfigurableBucketResolverTest::can_create_resolver_from_bucket_space_config() {
    BucketSpacesConfigBuilder builder;
    builder.documenttype.emplace_back(make_doc_type("foo", "default"));
    builder.documenttype.emplace_back(make_doc_type("bar", "global"));
    builder.documenttype.emplace_back(make_doc_type("baz", "global"));
    auto resolver = ConfigurableBucketResolver::from_config(builder);
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::default_space(),
                         resolver->bucketFromId(DocumentId("id::foo::xyz")).getBucketSpace());
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::global_space(),
                         resolver->bucketFromId(DocumentId("id::bar::xyz")).getBucketSpace());
    CPPUNIT_ASSERT_EQUAL(document::FixedBucketSpaces::global_space(),
                         resolver->bucketFromId(DocumentId("id::baz::xyz")).getBucketSpace());
}

}

