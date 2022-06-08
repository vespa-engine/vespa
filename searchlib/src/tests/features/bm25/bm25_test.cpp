// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/bm25_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::objectstore;
using CollectionType = FieldInfo::CollectionType;
using StringVector = std::vector<vespalib::string>;

struct Bm25BlueprintTest : public ::testing::Test {
    BlueprintFactory factory;
    test::IndexEnvironment index_env;

    Bm25BlueprintTest()
        : factory(),
          index_env()
    {
        setup_search_features(factory);
        test::IndexEnvironmentBuilder builder(index_env);
        builder.addField(FieldType::INDEX, CollectionType::SINGLE, "is");
        builder.addField(FieldType::INDEX, CollectionType::ARRAY, "ia");
        builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "iws");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "as");
    }

    Blueprint::SP make_blueprint() const {
        return factory.createBlueprint("bm25");
    }

    void expect_setup_fail(const StringVector& params) {
        auto blueprint = make_blueprint();
        test::DummyDependencyHandler deps(*blueprint);
        EXPECT_FALSE(blueprint->setup(index_env, params));
    }

    Blueprint::SP expect_setup_succeed(const StringVector& params) {
        auto blueprint = make_blueprint();
        test::DummyDependencyHandler deps(*blueprint);
        EXPECT_TRUE(blueprint->setup(index_env, params));
        EXPECT_EQ(0, deps.input.size());
        EXPECT_EQ(StringVector({"score"}), deps.output);
        return blueprint;
    }
};

TEST_F(Bm25BlueprintTest, blueprint_can_be_created_from_factory)
{
    auto bp = factory.createBlueprint("bm25");
    EXPECT_TRUE(bp.get() != nullptr);
    EXPECT_TRUE(dynamic_cast<Bm25Blueprint*>(bp.get()) != nullptr);
}

TEST_F(Bm25BlueprintTest, blueprint_setup_fails_when_parameter_list_is_not_valid)
{
    expect_setup_fail({});           // wrong parameter number
    expect_setup_fail({"as"});       // 'as' is an attribute
    expect_setup_fail({"is", "ia"}); // wrong parameter number
}

TEST_F(Bm25BlueprintTest, blueprint_setup_fails_when_k1_param_is_malformed)
{
    index_env.getProperties().add("bm25(is).k1", "malformed");
    expect_setup_fail({"is"});
}

TEST_F(Bm25BlueprintTest, blueprint_setup_fails_when_b_param_is_malformed)
{
    index_env.getProperties().add("bm25(is).b", "malformed");
    expect_setup_fail({"is"});
}

TEST_F(Bm25BlueprintTest, blueprint_setup_succeeds_for_index_field)
{
    expect_setup_succeed({"is"});
    expect_setup_succeed({"ia"});
    expect_setup_succeed({"iws"});
}

TEST_F(Bm25BlueprintTest, blueprint_can_prepare_shared_state_with_average_field_length)
{
    auto blueprint = expect_setup_succeed({"is"});
    test::QueryEnvironment query_env;
    query_env.get_avg_field_lengths()["is"] = 10;
    ObjectStore store;
    blueprint->prepareSharedState(query_env, store);
    EXPECT_DOUBLE_EQ(10, as_value<double>(*store.get("bm25.afl.is")));
}

TEST_F(Bm25BlueprintTest, dump_features_for_all_index_fields)
{
    FtTestApp::FT_DUMP(factory, "bm25", index_env,
                       StringList().add("bm25(is)").add("bm25(ia)").add("bm25(iws)"));
}

struct Scorer {

    double avg_field_length;
    double k1_param;
    double b_param;

    Scorer() :
        avg_field_length(10),
        k1_param(1.2),
        b_param(0.75)
    {}

    feature_t score(feature_t num_occs, feature_t field_length, double inverse_doc_freq) const {
        return inverse_doc_freq * (num_occs * (1 + k1_param)) /
                (num_occs + (k1_param * ((1 - b_param) + b_param * field_length / avg_field_length)));
    }
};

struct Bm25ExecutorTest : public ::testing::Test {
    BlueprintFactory factory;
    FtFeatureTest test;
    test::MatchDataBuilder::UP match_data;
    Scorer scorer;
    static constexpr uint32_t total_doc_count = 100;

    Bm25ExecutorTest()
        : factory(),
          test(factory, "bm25(foo)"),
          match_data()
    {
        setup_search_features(factory);
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        test.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        add_query_term("foo", 25);
        add_query_term("foo", 35);
        add_query_term("bar", 45);
        test.getQueryEnv().getBuilder().set_avg_field_length("foo", 10);
    }
    void add_query_term(const vespalib::string& field_name, uint32_t matching_doc_count) {
        auto* term = test.getQueryEnv().getBuilder().addIndexNode({field_name});
        term->field(0).setDocFreq(matching_doc_count, total_doc_count);
        term->setUniqueId(test.getQueryEnv().getNumTerms() - 1);
    }
    void setup() {
        EXPECT_TRUE(test.setup());
        match_data = test.createMatchDataBuilder();
        clear_term(0, 0);
        clear_term(1, 0);
        clear_term(2, 1);
    }
    bool execute(feature_t exp_score) {
        return test.execute(exp_score, 0.000001);
    }
    void clear_term(uint32_t term_id, uint32_t field_id) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        tfmd->reset(123);
    }
    void prepare_term(uint32_t term_id, uint32_t field_id, uint16_t num_occs, uint16_t field_length, uint32_t doc_id = 1) {
        auto* tfmd = match_data->getTermFieldMatchData(term_id, field_id);
        ASSERT_TRUE(tfmd != nullptr);
        tfmd->reset(doc_id);
        tfmd->setNumOccs(num_occs);
        tfmd->setFieldLength(field_length);
    }

    double idf(uint32_t matching_doc_count) const {
        return Bm25Executor::calculate_inverse_document_frequency(matching_doc_count, total_doc_count);
    }

    feature_t score(feature_t num_occs, feature_t field_length, double inverse_doc_freq) const {
        return scorer.score(num_occs, field_length, inverse_doc_freq);
    }
};

TEST_F(Bm25ExecutorTest, score_is_calculated_for_a_single_term)
{
    setup();
    prepare_term(0, 0, 3, 20);
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_F(Bm25ExecutorTest, score_is_calculated_for_multiple_terms)
{
    setup();
    prepare_term(0, 0, 3, 20);
    prepare_term(1, 0, 7, 5);
    EXPECT_TRUE(execute(score(3.0, 20, idf(25)) + score(7.0, 5.0, idf(35))));
}

TEST_F(Bm25ExecutorTest, term_that_does_not_match_document_is_ignored)
{
    setup();
    prepare_term(0, 0, 3, 20);
    uint32_t unmatched_doc_id = 123;
    prepare_term(1, 0, 7, 5, unmatched_doc_id);
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_F(Bm25ExecutorTest, term_searching_another_field_is_ignored)
{
    setup();
    prepare_term(2, 1, 3, 20);
    EXPECT_TRUE(execute(0.0));
}

TEST_F(Bm25ExecutorTest, uses_average_field_length_from_shared_state_if_found)
{
    test.getQueryEnv().getObjectStore().add("bm25.afl.foo", std::make_unique<AnyWrapper<double>>(15));
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.avg_field_length = 15;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_F(Bm25ExecutorTest, calculates_inverse_document_frequency)
{
    EXPECT_DOUBLE_EQ(std::log(1 + (99 + 0.5) / (1 + 0.5)),
                     Bm25Executor::calculate_inverse_document_frequency(1, 100));
    EXPECT_DOUBLE_EQ(std::log(1 + (60 + 0.5) / (40 + 0.5)),
                     Bm25Executor::calculate_inverse_document_frequency(40, 100));
    EXPECT_DOUBLE_EQ(std::log(1 + (0.5) / (100 + 0.5)),
                     Bm25Executor::calculate_inverse_document_frequency(100, 100));
}

TEST_F(Bm25ExecutorTest, k1_param_can_be_overriden)
{
    test.getIndexEnv().getProperties().add("bm25(foo).k1", "2.5");
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.k1_param = 2.5;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_F(Bm25ExecutorTest, b_param_can_be_overriden)
{
    test.getIndexEnv().getProperties().add("bm25(foo).b", "0.9");
    setup();
    prepare_term(0, 0, 3, 20);
    scorer.b_param = 0.9;
    EXPECT_TRUE(execute(score(3.0, 20, idf(25))));
}

TEST_F(Bm25ExecutorTest, inverse_document_frequency_can_be_overriden_with_significance)
{
    test.getQueryEnv().getProperties().add("vespa.term.0.significance", "0.35");
    setup();
    prepare_term(0, 0, 3, 20);
    EXPECT_TRUE(execute(score(3.0, 20, 0.35)));
}

GTEST_MAIN_RUN_ALL_TESTS()
