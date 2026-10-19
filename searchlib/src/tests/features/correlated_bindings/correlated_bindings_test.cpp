// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/searchlib/features/correlated_bindings_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/queryeval/correlated_bindings.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/issue.h>
#include <limits>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using namespace search::queryeval;
using vespalib::eval::TensorSpec;
using vespalib::eval::spec_from_value;

namespace {

using DocumentRows = std::map<uint32_t, std::vector<BindingRow>>;

struct Reader : CorrelatedBindingReader {
    const DocumentRows& rows;
    std::vector<BindingRow> matches;
    explicit Reader(const DocumentRows& source) : rows(source), matches() {}
    std::span<const BindingRow> read(uint32_t docid, std::span<const uint32_t> projection) override {
        matches.clear();
        auto found = rows.find(docid);
        if (found != rows.end()) {
            for (const auto& binding : found->second) {
                BindingRow row;
                for (uint32_t column : projection) row.push_back(binding.at(column));
                matches.push_back(std::move(row));
            }
        }
        return matches;
    }
};

struct Factory : CorrelatedBindingFactory {
    DocumentRows rows;
    std::map<std::string, uint32_t> names{{"unit", 1}, {"zone", 0}};
    explicit Factory(DocumentRows source) : rows(std::move(source)) {}
    const std::map<std::string, uint32_t>& columns() const override { return names; }
    std::unique_ptr<CorrelatedBindingReader> create() const override { return std::make_unique<Reader>(rows); }
};

struct Fixture {
    BlueprintFactory factory;
    FtFeatureTest test;
    Fixture(const std::string& feature, DocumentRows rows = {}, bool has_correlation = true)
        : factory(), test(factory, feature) {
        setup_search_features(factory);
        if (has_correlation)
            test.getQueryEnv().getObjectStore().add(CorrelatedBindingFactory::store_key("selection"),
                                                   std::make_unique<Factory>(std::move(rows)));
        EXPECT_TRUE(test.setup());
    }
    TensorSpec execute(uint32_t docid) { return spec_from_value(test.resolveObjectFeature(docid)); }
};

struct Issues : vespalib::Issue::Handler {
    std::vector<std::string> messages;
    vespalib::Issue::Binding capture = vespalib::Issue::listen(*this);
    void handle(const vespalib::Issue& issue) override { messages.push_back(issue.message()); }
};

}

TEST(CorrelatedBindingsTest, setup_rejects_missing_duplicate_and_invalid_dimension_arguments) {
    for (const auto& parameters : std::vector<std::vector<std::string>>{
            {}, {"selection"}, {"", "unit"}, {"selection", "unit", "unit"}, {"selection", "bad dimension"}}) {
        IndexEnvironment env;
        CorrelatedBindingsBlueprint blueprint;
        DummyDependencyHandler handler(blueprint);
        EXPECT_FALSE(static_cast<Blueprint&>(blueprint).setup(env, parameters));
    }
}

TEST(CorrelatedBindingsTest, absent_and_empty_correlations_return_empty_tensors) {
    Fixture absent("correlatedBindings(selection,unit)", {}, false);
    Fixture empty("correlatedBindings(selection,unit,zone)");
    EXPECT_EQ(TensorSpec("tensor(unit{})"), absent.execute(1));
    EXPECT_EQ(TensorSpec("tensor(unit{},zone{})"), empty.execute(1));
}

TEST(CorrelatedBindingsTest, projection_deduplicates_units_and_clears_output_between_documents) {
    using namespace std::literals;
    Fixture f("correlatedBindings(selection,unit)", {
        {1, {{"A"sv, int64_t(7)}, {"B"sv, int64_t(7)}, {"B"sv, int64_t(9)}}},
        {3, {{"A"sv, int64_t(5)}}}
    });
    auto first = TensorSpec("tensor(unit{})").add({{"unit", "7"}}, 1).add({{"unit", "9"}}, 1);
    EXPECT_EQ(first, f.execute(1));
    EXPECT_EQ(TensorSpec("tensor(unit{})"), f.execute(2));
    EXPECT_EQ(TensorSpec("tensor(unit{})").add({{"unit", "5"}}, 1), f.execute(3));
    EXPECT_EQ(first, f.execute(1));
}

TEST(CorrelatedBindingsTest, tuple_dimensions_are_sorted_and_keep_exact_string_and_integer_labels) {
    using namespace std::literals;
    int64_t max = std::numeric_limits<int64_t>::max();
    int64_t min = std::numeric_limits<int64_t>::min() + 1;
    Fixture f("correlatedBindings(selection,zone,unit)", {
        {1, {{"Blå"sv, max}, {"blå"sv, min}, {"Blå"sv, max}}}
    });
    EXPECT_EQ(TensorSpec("tensor(unit{},zone{})")
                      .add({{"unit", std::to_string(max)}, {"zone", "Blå"}}, 1)
                      .add({{"unit", std::to_string(min)}, {"zone", "blå"}}, 1), f.execute(1));
}

TEST(CorrelatedBindingsTest, unknown_variables_report_an_issue_and_return_an_empty_tensor) {
    Issues issues;
    Fixture f("correlatedBindings(selection,unknown)");
    EXPECT_EQ(TensorSpec("tensor(unknown{})"), f.execute(1));
    ASSERT_FALSE(issues.messages.empty());
    for (const auto& message : issues.messages)
        EXPECT_NE(std::string::npos, message.find("unbound variable 'unknown'")) << message;
}

GTEST_MAIN_RUN_ALL_TESTS()
