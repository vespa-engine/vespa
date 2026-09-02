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
    EXPECT_FALSE(f.try_setup({"foo", "bar"}));
    // wrong parameter number; unlike matches, there is no term-index overload
    EXPECT_FALSE(f.try_setup({"foo", "0"}));
    EXPECT_FALSE(f.try_setup({"unknown"}));
}

TEST(MatchesForLabelsBlueprintTest, setup_succeeds_without_parameters) {
    MatchesForLabelsBlueprintFixture f;
    MatchesForLabelsBlueprint        bp;
    DummyDependencyHandler           deps(bp);
    bp.setName("matches_for_labels");
    EXPECT_TRUE(((Blueprint&)bp).setup(f.index_env, Blueprint::StringVector()));
    EXPECT_EQ(0, deps.input.size());
    EXPECT_EQ(std::vector<std::string>{"out"}, deps.output);
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

    explicit MatchesForLabelsFixture(const std::string& feature = "matches_for_labels(foo)")
        : factory(), test(factory, feature), match_data() {
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
    virtual ~MatchesForLabelsFixture();

    void add_label(const std::string& label, const std::vector<uint32_t>& uids) {
        MultiLabel(label, uids).inject(test.getQueryEnv().getProperties());
    }
    void setup() {
        EXPECT_TRUE(test.setup());
        match_data = test.createMatchDataBuilder();
    }
    void prepare_term(uint32_t term_id, uint32_t field_id, uint32_t doc_id = 1) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        tfmd->reset(doc_id);
    }
    TensorSpec execute(uint32_t doc_id = 1) { return spec_from_value(test.resolveObjectFeature(doc_id)); }
};

MatchesForLabelsFixture::~MatchesForLabelsFixture() = default;

TEST(MatchesForLabelsExecutorTest, label_with_one_term_gives_one_cell) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.setup();
    f.prepare_term(0, 1);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
}

TEST(MatchesForLabelsExecutorTest, label_with_multiple_terms_in_the_field_is_any_of) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11, 12});
    f.setup();
    f.prepare_term(0, 1);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
}

TEST(MatchesForLabelsExecutorTest, label_not_matching_the_document_is_absent) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.add_label("y", {12});
    f.setup();
    f.prepare_term(0, 1);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
}

TEST(MatchesForLabelsExecutorTest, query_without_labels_gives_empty_tensor) {
    MatchesForLabelsFixture f;
    f.setup();
    f.prepare_term(0, 1);
    EXPECT_EQ(empty_spec(), f.execute());
}

TEST(MatchesForLabelsExecutorTest, no_label_matching_the_document_gives_empty_tensor) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.add_label("y", {12});
    f.setup();
    EXPECT_EQ(empty_spec(), f.execute());
}

TEST(MatchesForLabelsExecutorTest, label_whose_terms_search_another_field_is_absent) {
    MatchesForLabelsFixture f;
    f.add_label("x", {13});
    f.setup();
    f.prepare_term(2, 2);
    EXPECT_EQ(empty_spec(), f.execute());
}

TEST(MatchesForLabelsExecutorTest, label_whose_term_is_on_no_field_is_absent) {
    MatchesForLabelsFixture f;
    auto&                   query_env = f.test.getQueryEnv();
    query_env.getTerms().push_back(SimpleTermData());
    SimpleTermData& term = query_env.getTerms().back();
    term.setUniqueId(14);
    auto& term_field = term.addField(FieldInfo::no_field().id()); // field id 0
    term_field.setHandle(query_env.getLayout().allocTermField(term_field.getFieldId()));
    ASSERT_NE(term_field.getHandle(), IllegalHandle);
    f.add_label("x", {14});
    f.setup();
    f.prepare_term(0, 1);
    EXPECT_EQ(empty_spec(), f.execute());
}

// A term in a non-contributing query branch may have match data for this
// document while remaining hidden from ranking. It must not produce a cell.
TEST(MatchesForLabelsExecutorTest, hidden_from_ranking_gives_empty_tensor) {
    MatchesForLabelsFixture f;
    f.add_label("x", {11});
    f.setup();
    f.prepare_term(0, 1);
    auto* tfmd = f.match_data->getTermFieldMatchData(0, 1);
    ASSERT_TRUE(tfmd != nullptr);
    tfmd->set_hidden_from_ranking();
    EXPECT_EQ(empty_spec(), f.execute());
}

// Without a field parameter every field searched by the labeled terms counts.
struct MatchesForLabelsAnyFieldFixture : MatchesForLabelsFixture {
    MatchesForLabelsAnyFieldFixture() : MatchesForLabelsFixture("matches_for_labels") {}
    ~MatchesForLabelsAnyFieldFixture() override;
};

MatchesForLabelsAnyFieldFixture::~MatchesForLabelsAnyFieldFixture() = default;

TEST(MatchesForLabelsAnyFieldTest, label_matching_in_any_field_gives_one_cell) {
    MatchesForLabelsAnyFieldFixture f;
    f.add_label("x", {11});
    f.add_label("y", {13});
    f.setup();
    f.prepare_term(0, 1); // term 11 in field foo
    f.prepare_term(2, 2); // term 13 in field bar
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1).add(label_addr("y"), 1), f.execute());
}

TEST(MatchesForLabelsAnyFieldTest, label_not_matching_the_document_is_absent) {
    MatchesForLabelsAnyFieldFixture f;
    f.add_label("x", {11});
    f.add_label("y", {13});
    f.setup();
    f.prepare_term(2, 2); // only term 13 matches
    EXPECT_EQ(empty_spec().add(label_addr("y"), 1), f.execute());
}

TEST(MatchesForLabelsAnyFieldTest, query_without_labels_gives_empty_tensor) {
    MatchesForLabelsAnyFieldFixture f;
    f.setup();
    f.prepare_term(0, 1);
    EXPECT_EQ(empty_spec(), f.execute());
}

// A label wrapper searches no field of its own, but is still a labeled query
// item that can match; the field-less feature must pick it up.
TEST(MatchesForLabelsAnyFieldTest, label_on_term_searching_no_field_gives_one_cell) {
    MatchesForLabelsAnyFieldFixture f;
    auto&                           query_env = f.test.getQueryEnv();
    query_env.getTerms().push_back(SimpleTermData());
    SimpleTermData& term = query_env.getTerms().back();
    term.setUniqueId(14);
    auto& term_field = term.addField(FieldInfo::no_field().id()); // field id 0
    term_field.setHandle(query_env.getLayout().allocTermField(term_field.getFieldId()));
    ASSERT_NE(term_field.getHandle(), IllegalHandle);
    f.add_label("x", {14});
    f.setup();
    f.prepare_term(3, 0);
    EXPECT_EQ(empty_spec().add(label_addr("x"), 1), f.execute());
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

TEST(MatchesForLabelsExecutorTest, attribute_field_match_gives_one_cell_only_for_matching_docid) {
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
