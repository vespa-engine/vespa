// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/features/matches_for_labels_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/labels.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using vespalib::eval::spec_from_value;
using vespalib::eval::TensorSpec;
using CollectionType = FieldInfo::CollectionType;

namespace {

TensorSpec empty_spec() {
    return TensorSpec("tensor<float>(label{})");
}

TensorSpec::Address label_addr(const std::string& label) {
    return {{"label", label}};
}

struct MatchesForLabelsBlueprintFixture {
    IndexEnvironment index_env;

    MatchesForLabelsBlueprintFixture() : index_env() {
        IndexEnvironmentBuilder builder(index_env);
        builder.addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "tags");
        builder.addField(FieldType::VIRTUAL, CollectionType::SINGLE, "vfoo");
    }

    bool try_setup(const std::vector<std::string>& params) {
        MatchesForLabelsBlueprint bp;
        DummyDependencyHandler    deps(bp);
        bp.setName("matches_for_labels");
        return ((Blueprint&)bp).setup(index_env, params);
    }
};

TEST(MatchesForLabelsBlueprintTest, setup_fails_for_invalid_parameter_lists) {
    MatchesForLabelsBlueprintFixture f;
    EXPECT_FALSE(f.try_setup({}));
    EXPECT_FALSE(f.try_setup({"foo", "bar"}));
    EXPECT_FALSE(f.try_setup({"foo", "0"})); // discriminates vs matches's optional number
    EXPECT_FALSE(f.try_setup({"unknown"}));
}

TEST(MatchesForLabelsBlueprintTest, setup_succeeds_for_index_attribute_and_virtual_fields) {
    MatchesForLabelsBlueprintFixture f;
    for (const char* field : {"foo", "tags", "vfoo"}) {
        MatchesForLabelsBlueprint bp;
        DummyDependencyHandler    deps(bp);
        bp.setName(std::string("matches_for_labels(") + field + ")");
        EXPECT_TRUE(((Blueprint&)bp).setup(f.index_env, {field}));
        EXPECT_EQ(0, deps.input.size());
        EXPECT_EQ(std::vector<std::string>{"out"}, deps.output);
    }
}

struct MatchesForLabelsFixture {
    BlueprintFactory           factory;
    FtFeatureTest              test;
    test::MatchDataBuilder::UP match_data;

    MatchesForLabelsFixture() : factory(), test(factory, "matches_for_labels(foo)"), match_data() {
        setup_search_features(factory);
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "tags");
        auto* t0 = test.getQueryEnv().getBuilder().addIndexNode({"foo"});
        t0->setUniqueId(11);
        auto* t1 = test.getQueryEnv().getBuilder().addIndexNode({"foo"});
        t1->setUniqueId(12);
        auto* t2 = test.getQueryEnv().getBuilder().addIndexNode({"bar"});
        t2->setUniqueId(13);
    }
    ~MatchesForLabelsFixture();

    void add_label(const std::string& label, const std::vector<uint32_t>& uids) {
        MultiLabel(label, uids).inject(test.getQueryEnv().getProperties());
    }
    void setup() {
        EXPECT_TRUE(test.setup());
        match_data = test.createMatchDataBuilder();
    }
    void hit_index(uint32_t term_id, uint32_t field_id, uint32_t doc_id = 1) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        tfmd->reset(doc_id);
    }
    TensorSpec execute(uint32_t doc_id = 1) { return spec_from_value(test.resolveObjectFeature(doc_id)); }
};

MatchesForLabelsFixture::~MatchesForLabelsFixture() = default;

TEST(MatchesForLabelsExecutorTest, one_label_one_term_that_hits_gives_one_cell) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.setup();
    f.hit_index(0, 1);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
}

TEST(MatchesForLabelsExecutorTest, one_label_two_terms_in_the_field_is_any_of) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11, 12});
    f.setup();
    f.hit_index(0, 1);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
}

TEST(MatchesForLabelsExecutorTest, two_labels_only_one_hits_leaves_the_other_absent) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.add_label("y", {12});
    f.setup();
    f.hit_index(0, 1);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
}

TEST(MatchesForLabelsExecutorTest, query_without_labels_gives_empty_tensor) {
    MatchesForLabelsFixture f;
    f.setup();
    f.hit_index(0, 1);
    // createExecutor's ConstantTensorRefExecutor path (no vespa.label.* at all)
    EXPECT_EQ(empty_spec(), f.execute());
}

TEST(MatchesForLabelsExecutorTest, no_label_matching_the_document_gives_empty_tensor) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.add_label("y", {12});
    f.setup();
    // execute()'s _empty_output path (labels exist, none hit this document)
    EXPECT_EQ(empty_spec(), f.execute());
}

TEST(MatchesForLabelsExecutorTest, label_whose_only_term_searches_another_field_is_absent) {
    MatchesForLabelsFixture f;
    f.add_label("x", {13});
    f.setup();
    f.hit_index(2, 2);
    EXPECT_EQ(empty_spec(), f.execute());
}

TEST(MatchesForLabelsExecutorTest, label_whose_only_term_is_on_no_field_is_absent) {
    MatchesForLabelsFixture f;
    auto& qe = f.test.getQueryEnv();
    qe.getTerms().push_back(SimpleTermData());
    SimpleTermData& td = qe.getTerms().back();
    td.setUniqueId(14);
    auto& tfd = td.addField(FieldInfo::no_field().id()); // id 0
    tfd.setHandle(qe.getLayout().allocTermField(tfd.getFieldId()));
    ASSERT_NE(tfd.getHandle(), IllegalHandle);
    f.add_label("x", {14});
    f.setup();
    f.hit_index(0, 1);
    EXPECT_EQ(empty_spec(), f.execute());
}

// Predicate pin: has_ranking_data is false when HIDDEN_FROM_RANKING is set.
// A YQL {label} on a near/onear/sameElement child is not this case — ranking
// unpack clears the flag, so that child is ranking-visible (cell 1).
TEST(MatchesForLabelsExecutorTest, hidden_from_ranking_gives_empty_tensor) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.setup();
    f.hit_index(0, 1);
    auto* tfmd = f.match_data->getTermFieldMatchData(0, 1);
    ASSERT_TRUE(tfmd != nullptr);
    tfmd->set_hidden_from_ranking();
    EXPECT_EQ(empty_spec(), f.execute());
}

struct MatchesForLabelsAttributeFixture {
    BlueprintFactory           factory;
    FtFeatureTest              test;
    test::MatchDataBuilder::UP match_data;

    MatchesForLabelsAttributeFixture() : factory(), test(factory, "matches_for_labels(tags)"), match_data() {
        setup_search_features(factory);
        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "tags");
        auto* term = test.getQueryEnv().getBuilder().addAttributeNode("tags");
        term->setUniqueId(11);
    }
    ~MatchesForLabelsAttributeFixture();

    void add_label(const std::string& label, const std::vector<uint32_t>& uids) {
        MultiLabel(label, uids).inject(test.getQueryEnv().getProperties());
    }
    void setup() {
        EXPECT_TRUE(test.setup());
        match_data = test.createMatchDataBuilder();
    }
    TensorSpec execute(uint32_t doc_id = 1) { return spec_from_value(test.resolveObjectFeature(doc_id)); }
};

MatchesForLabelsAttributeFixture::~MatchesForLabelsAttributeFixture() = default;

TEST(MatchesForLabelsExecutorTest, attribute_field_is_one_on_hit_and_empty_on_other_docid) {
    MatchesForLabelsAttributeFixture f;
    f.add_label("x", {11});
    f.setup();
    ASSERT_TRUE(f.match_data->setWeight("tags", 0, 1));
    ASSERT_TRUE(f.match_data->apply(1));
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute(1));
    EXPECT_EQ(empty_spec(), f.execute(2));
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
