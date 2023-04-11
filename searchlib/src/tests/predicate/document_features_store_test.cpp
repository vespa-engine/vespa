// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for document_features_store.

#include <vespa/log/log.h>
LOG_SETUP("document_features_store_test");

#include <vespa/searchlib/predicate/document_features_store.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <string>

using namespace search;
using namespace search::predicate;
using std::string;

namespace {

const uint64_t hash1 = 0x12345678;
const uint64_t hash2 = 0x123456789a;
const uint32_t doc_id = 42;

TEST("require that DocumentFeaturesStore can store features.") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.features.push_back(hash1);
    annotations.features.push_back(hash2);
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(2u, features.size());
    EXPECT_EQUAL(1u, features.count(hash1));
    EXPECT_EQUAL(1u, features.count(hash2));

    features_store.remove(doc_id);
    features = features_store.get(doc_id);
    EXPECT_TRUE(features.empty());
}

template <typename Set>
void expectHash(const string &label, const Set &set) {
    TEST_STATE(label.c_str());
    uint64_t hash = PredicateHash::hash64(label);
    EXPECT_EQUAL(1u, set.count(hash));
}

TEST("require that DocumentFeaturesStore can store ranges.") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 2, 4});
    annotations.range_features.push_back({"bar", 7, 13});
    annotations.range_features.push_back({"baz", 9, 19});
    annotations.range_features.push_back({"qux", -10, 10});
    annotations.range_features.push_back({"quux", -39, -10});
    annotations.range_features.push_back({"corge", -9, -1});
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(13u, features.size());
    expectHash("foo=0", features);

    expectHash("bar=0", features);
    expectHash("bar=10", features);

    expectHash("baz=0", features);
    expectHash("baz=10-19", features);

    expectHash("qux=-10", features);
    expectHash("qux=-9-0", features);
    expectHash("qux=10", features);
    expectHash("qux=0-9", features);

    expectHash("quux=-19-10", features);
    expectHash("quux=-29-20", features);
    expectHash("quux=-39-30", features);

    expectHash("corge=-9-0", features);
}

TEST("require that DocumentFeaturesStore can store large ranges.") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 10, 199});
    annotations.range_features.push_back({"bar", 100, 239});
    annotations.range_features.push_back({"baz", -999, 999});
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(17u, features.size());
    expectHash("foo=10-19", features);
    expectHash("foo=20-29", features);
    expectHash("foo=30-39", features);
    expectHash("foo=40-49", features);
    expectHash("foo=50-59", features);
    expectHash("foo=60-69", features);
    expectHash("foo=70-79", features);
    expectHash("foo=80-89", features);
    expectHash("foo=90-99", features);
    expectHash("foo=100-199", features);

    expectHash("bar=200-209", features);
    expectHash("bar=210-219", features);
    expectHash("bar=220-229", features);
    expectHash("bar=230-239", features);
    expectHash("bar=100-199", features);

    expectHash("baz=-999-0", features);
    expectHash("baz=0-999", features);
}

TEST("require that DocumentFeaturesStore can use very large ranges.") {
    DocumentFeaturesStore features_store(2);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", LLONG_MIN, 39});
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(4u, features.size());
    expectHash("foo=-9223372036854775808", features);
    expectHash("foo=-9223372036854775807-0", features);
    expectHash("foo=0-31", features);
    expectHash("foo=32-39", features);
}

TEST("require that duplicate range features are removed.") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 80, 199});
    annotations.range_features.push_back({"foo", 85, 199});
    annotations.range_features.push_back({"foo", 90, 199});
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(4u, features.size());
    expectHash("foo=80-89", features);
    expectHash("foo=90-99", features);
    expectHash("foo=100-199", features);
    expectHash("foo=80", features);
}

TEST("require that only unique features are returned") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 100, 199});
    annotations.features.push_back(PredicateHash::hash64("foo=100-199"));
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(1u, features.size());
    expectHash("foo=100-199", features);
}

TEST("require that both features and ranges are removed by 'remove'") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 100, 199});
    annotations.features.push_back(PredicateHash::hash64("foo=100-199"));
    features_store.insert(annotations, doc_id);
    features_store.remove(doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(0u, features.size());
}

TEST("require that both features and ranges counts towards memory usage") {
    DocumentFeaturesStore features_store(10);
    EXPECT_EQUAL(50064u, features_store.getMemoryUsage().usedBytes());

    PredicateTreeAnnotations annotations;
    annotations.features.push_back(PredicateHash::hash64("foo=100-199"));
    features_store.insert(annotations, doc_id);
    EXPECT_EQUAL(50072u, features_store.getMemoryUsage().usedBytes());

    annotations.features.clear();
    annotations.range_features.push_back({"foo", 100, 199});
    features_store.insert(annotations, doc_id + 1);
    EXPECT_EQUAL(50168u, features_store.getMemoryUsage().usedBytes());
}

TEST("require that DocumentFeaturesStore can be serialized") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 100, 199});
    annotations.features.push_back(PredicateHash::hash64("foo=bar"));
    features_store.insert(annotations, doc_id);

    auto features = features_store.get(doc_id);
    ASSERT_EQUAL(2u, features.size());
    expectHash("foo=bar", features);
    expectHash("foo=100-199", features);

    vespalib::DataBuffer buffer;
    features_store.serialize(buffer);

    DocumentFeaturesStore features_store2(buffer);
    features = features_store2.get(doc_id);
    ASSERT_EQUAL(2u, features.size());
    expectHash("foo=bar", features);
    expectHash("foo=100-199", features);
}

TEST("require that serialization cleans up wordstore") {
    DocumentFeaturesStore features_store(10);
    PredicateTreeAnnotations annotations;
    annotations.range_features.push_back({"foo", 100, 199});
    features_store.insert(annotations, doc_id);
    EXPECT_EQUAL(50160u, features_store.getMemoryUsage().usedBytes());
    annotations.range_features.push_back({"bar", 100, 199});
    features_store.insert(annotations, doc_id + 1);
    EXPECT_EQUAL(50548u, features_store.getMemoryUsage().usedBytes());
    features_store.remove(doc_id + 1);
    EXPECT_EQUAL(50500u, features_store.getMemoryUsage().usedBytes());

    vespalib::DataBuffer buffer;
    features_store.serialize(buffer);
    DocumentFeaturesStore features_store2(buffer);
    EXPECT_EQUAL(50160u, features_store2.getMemoryUsage().usedBytes());
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
