// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using vespalib::eval::spec_from_value;
using vespalib::eval::TensorSpec;

using CollectionType = FieldInfo::CollectionType;

namespace {

TensorSpec empty_spec() {
    return TensorSpec("tensor(term{})");
}

TensorSpec::Address term_addr(const std::string& label) {
    return {{"term", label}};
}

struct QueryTermDocumentFrequencyTest {
    BlueprintFactory factory;
    FtFeatureTest    test;
    explicit QueryTermDocumentFrequencyTest(const std::string& feature) : factory(), test(factory, feature) {
        setup_search_features(factory);
    }
    void add_index_field(const std::string& name) {
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, name);
    }
    SimpleTermData* add_term(const std::vector<std::string>& field_names) {
        return test.getQueryEnv().getBuilder().addIndexNode(field_names);
    }
    void add_docfreq_override(uint32_t uid, uint64_t frequency, uint64_t count) {
        std::string key = "vespa.term." + std::to_string(uid) + ".docfreq";
        test.getQueryEnv().getProperties().add(key, std::to_string(frequency));
        test.getQueryEnv().getProperties().add(key, std::to_string(count));
    }
    TensorSpec execute() {
        EXPECT_TRUE(test.setup());
        return spec_from_value(test.resolveObjectFeature());
    }
};

} // namespace

TEST(QueryTermDocumentFrequencyTest, cells_hold_per_term_document_frequencies_for_the_field) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo");
    f.add_term({"foo"})->lookupField(0)->setDocFreq(10, 100);
    f.add_term({"foo"})->lookupField(0)->setDocFreq(25, 100);
    EXPECT_EQ(empty_spec().add(term_addr("0"), 10).add(term_addr("1"), 25), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, field_is_resolved_by_field_id_not_position) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("bar"); // field id 0
    f.add_index_field("foo"); // field id 1
    f.add_term({"foo"})->lookupField(1)->setDocFreq(7, 100);
    EXPECT_EQ(empty_spec().add(term_addr("0"), 7), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, labels_are_query_term_indexes_not_renumbered) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo"); // field id 0
    f.add_index_field("bar"); // field id 1
    f.add_term({"bar"})->lookupField(1)->setDocFreq(3, 100);
    f.add_term({"foo"})->lookupField(0)->setDocFreq(11, 100);
    EXPECT_EQ(empty_spec().add(term_addr("1"), 11), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, cell_uses_the_target_fields_frequency_for_multi_field_terms) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo"); // field id 0
    f.add_index_field("bar"); // field id 1
    auto* term = f.add_term({"foo", "bar"});
    term->lookupField(0)->setDocFreq(13, 100);
    term->lookupField(1)->setDocFreq(42, 100);
    EXPECT_EQ(empty_spec().add(term_addr("0"), 13), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, query_provided_document_frequency_overrides_field_statistic) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo");
    f.add_term({"foo"})->setUniqueId(7).lookupField(0)->setDocFreq(10, 100);
    f.add_docfreq_override(7, 60, 1000);
    EXPECT_EQ(empty_spec().add(term_addr("0"), 60), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, no_override_lookup_for_terms_with_unset_unique_id) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo");
    f.add_term({"foo"})->setUniqueId(0).lookupField(0)->setDocFreq(10, 100);
    f.add_docfreq_override(0, 60, 1000);
    EXPECT_EQ(empty_spec().add(term_addr("0"), 10), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, override_on_term_not_searching_the_field_does_not_add_a_cell) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo"); // field id 0
    f.add_index_field("bar"); // field id 1
    f.add_term({"bar"})->setUniqueId(7).lookupField(1)->setDocFreq(3, 100);
    f.add_docfreq_override(7, 60, 1000);
    f.add_term({"foo"})->lookupField(0)->setDocFreq(11, 100);
    EXPECT_EQ(empty_spec().add(term_addr("1"), 11), f.execute());
}

TEST(QueryTermDocumentFrequencyTest, no_terms_searching_the_field_gives_empty_tensor) {
    QueryTermDocumentFrequencyTest f("queryTermDocumentFrequency(foo)");
    f.add_index_field("foo"); // field id 0
    f.add_index_field("bar"); // field id 1
    f.add_term({"bar"})->lookupField(1)->setDocFreq(3, 100);
    EXPECT_EQ(empty_spec(), f.execute());
}

GTEST_MAIN_RUN_ALL_TESTS()
