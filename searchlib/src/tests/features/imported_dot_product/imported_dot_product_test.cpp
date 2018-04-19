// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/features/dotproductfeature.h>
#include <vespa/searchlib/test/imported_attribute_fixture.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/rankresult.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>

using namespace search;
using namespace search::attribute;
using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::index;

template <typename T>
std::unique_ptr<fef::Anything> create_param(const vespalib::string& param) {
    Properties props;
    props.add("foo", param);
    return std::make_unique<dotproduct::ArrayParam<T>>(props.lookup("foo"));
}

struct FixtureBase : ImportedAttributeFixture {

    BlueprintFactory _factory;
    FixtureBase() {
        DotProductBlueprint bp;
        _factory.addPrototype(bp.createInstance());
    }

    // Both array and wset attributes can have integer "key" types, so we let specific
    // sub-fixtures implement the mappings.
    virtual void setup_integer_mappings(BasicType int_type) = 0;

    void check_single_execution(feature_t expected,
                                const vespalib::string& vector,
                                DocId doc_id,
                                std::unique_ptr<fef::Anything> pre_parsed = std::unique_ptr<fef::Anything>()) {
        RankResult result;
        result.addScore("dotProduct(" + imported_attr->getName() + ",vector)", expected);
        result.setEpsilon(0.00001);
        FtFeatureTest feature(_factory, result.getKeys());

        feature.getQueryEnv().getProperties().add("dotProduct.vector", vector);
        if (pre_parsed) {
            feature.getQueryEnv().getObjectStore().add("dotProduct.vector.object", std::move(pre_parsed));
        }
        auto readGuard = imported_attr->makeReadGuard(false);
        const IAttributeVector *attr = readGuard->attribute();
        feature.getIndexEnv().getAttributeMap().add(std::move(readGuard));
        schema::CollectionType collection_type(
                (attr->getCollectionType() == attribute::CollectionType::ARRAY)
                ? schema::CollectionType::ARRAY : schema::CollectionType::WEIGHTEDSET);
        feature.getIndexEnv().getBuilder().addField(
                FieldType::ATTRIBUTE, collection_type, imported_attr->getName());
        ASSERT_TRUE(feature.setup());
        EXPECT_TRUE(feature.execute(result, doc_id));
    }

    template <typename BaseFullWidthType, typename PerTypeSetupFunctor>
    void check_executions(PerTypeSetupFunctor setup_func,
                          const std::vector<BasicType>& types,
                          feature_t expected,
                          const vespalib::string& vector,
                          DocId doc_id,
                          const vespalib::string& shared_param = "") {
        for (auto type : types) {
            setup_func(type);
            std::unique_ptr<fef::Anything> pre_parsed;
            if (!shared_param.empty()) {
                pre_parsed = create_param<BaseFullWidthType>(shared_param);
            }
            check_single_execution(expected, vector, doc_id, std::move(pre_parsed));
        }
    }

    void check_all_integer_executions(feature_t expected,
                                      const vespalib::string& vector,
                                      DocId doc_id,
                                      const vespalib::string& shared_param = "") {
        check_executions<int64_t>([this](auto int_type){ this->setup_integer_mappings(int_type); },
                                  {{BasicType::INT32, BasicType::INT64}},
                                  expected, vector, doc_id, shared_param);
    }
};

struct ArrayFixture : FixtureBase {

    void setup_integer_mappings(BasicType int_type) override {
        reset_with_array_value_reference_mappings<IntegerAttribute, int64_t>(
                int_type,
                {{DocId(1), dummy_gid(3), DocId(3), {{2, 3, 5}}},
                 {DocId(3), dummy_gid(7), DocId(7), {{7, 11}}},
                 {DocId(5), dummy_gid(8), DocId(8), {{13, 17, 19, 23}}}});
    }

    void setup_float_mappings(BasicType float_type) {
        reset_with_array_value_reference_mappings<FloatingPointAttribute, double>(
                float_type,
                {{DocId(2), dummy_gid(4), DocId(4), {{2.2, 3.3, 5.5}}},
                 {DocId(4), dummy_gid(8), DocId(8), {{7.7, 11.11}}},
                 {DocId(6), dummy_gid(9), DocId(9), {{13.1, 17.2, 19.3, 23.4}}}});
    }

    template <typename ExpectedType>
    void check_prepare_state_output(const vespalib::string& input_vector) {
        FtFeatureTest feature(_factory, "");
        DotProductBlueprint bp;
        DummyDependencyHandler dependency_handler(bp);
        ParameterList params({Parameter(ParameterType::ATTRIBUTE, imported_attr->getName()),
                              Parameter(ParameterType::STRING, "fancyvector")});

        feature.getIndexEnv().getAttributeMap().add(imported_attr->makeReadGuard(false));
        feature.getIndexEnv().getBuilder().addField(
                FieldType::ATTRIBUTE, schema::CollectionType::ARRAY, imported_attr->getName());

        bp.setup(feature.getIndexEnv(), params);
        feature.getQueryEnv().getProperties().add("dotProduct.fancyvector", input_vector);
        auto& obj_store = feature.getQueryEnv().getObjectStore();
        bp.prepareSharedState(feature.getQueryEnv(), obj_store);
        // Resulting name is very implementation defined. But at least the tests will break if it changes.
        const auto* parsed = obj_store.get("dotProduct.fancyvector.object");
        ASSERT_TRUE(parsed != nullptr);
        const auto* as_object = dynamic_cast<const ExpectedType*>(parsed);
        ASSERT_TRUE(as_object != nullptr);
        // We don't test the parsed output values here; that's the responsibility of other tests.
    }

    void check_all_float_executions(feature_t expected,
                                    const vespalib::string& vector,
                                    DocId doc_id,
                                    const vespalib::string& shared_param = "") {
        check_executions<double>([this](auto float_type){ this->setup_float_mappings(float_type); },
                                 {{BasicType::FLOAT, BasicType::DOUBLE}},
                                 expected, vector, doc_id, shared_param);
    }
};

TEST_F("Dense i32/i64 array dot products can be evaluated with string parameter", ArrayFixture) {
    f.check_all_integer_executions(2*2 + 3*3 + 5*4, "[2 3 4]", DocId(1));
}

TEST_F("Dense float/double array dot products can be evaluated with string parameter", ArrayFixture) {
    f.check_all_float_executions(2.2*7.7 + 3.3*11.11 + 5.5*13.13, "[7.7 11.11 13.13]", DocId(2));
}

TEST_F("Zero-length i32/i64 array query vector evaluates to zero", ArrayFixture) {
    f.check_all_integer_executions(0, "[]", DocId(1));
}

TEST_F("Zero-length float/double array query vector evaluates to zero", ArrayFixture) {
    f.check_all_float_executions(0, "[]", DocId(1));
}

TEST_F("prepareSharedState emits i64 vector for i32 imported attribute", ArrayFixture) {
    f.setup_integer_mappings(BasicType::INT32);
    f.template check_prepare_state_output<dotproduct::ArrayParam<int64_t>>("[101 202 303]");
}

TEST_F("prepareSharedState emits i64 vector for i64 imported attribute", ArrayFixture) {
    f.setup_integer_mappings(BasicType::INT64);
    f.template check_prepare_state_output<dotproduct::ArrayParam<int64_t>>("[101 202 303]");
}

TEST_F("prepareSharedState emits double vector for float imported attribute", ArrayFixture) {
    f.setup_float_mappings(BasicType::FLOAT);
    f.template check_prepare_state_output<dotproduct::ArrayParam<double>>("[10.1 20.2 30.3]");
}

TEST_F("prepareSharedState emits double vector for double imported attribute", ArrayFixture) {
    f.setup_float_mappings(BasicType::DOUBLE);
    f.template check_prepare_state_output<dotproduct::ArrayParam<double>>("[10.1 20.2 30.3]");
}

TEST_F("Dense i32/i64 array dot product can be evaluated with pre-parsed object parameter", ArrayFixture) {
    f.check_all_integer_executions(2*5 + 3*6 + 5*7, "[2 3 4]", DocId(1), "[5 6 7]"); // String input is ignored in favor of stored object
}

TEST_F("Dense float/double array dot product can be evaluated with pre-parsed object parameter", ArrayFixture) {
    f.check_all_float_executions(2.2*7.7 + 3.3*11.11 + 5.5*13.13, "[2.0 3.0 4.0]", DocId(2), "[7.7 11.11 13.13]");
}

TEST_F("Sparse i32/i64 array dot products can be evaluated with string parameter", ArrayFixture) {
    // Have an outlier index to prevent auto-flattening of sparse input
    f.check_all_integer_executions(2*13 + 4*23, "{0:2,3:4,50:100}", DocId(5));
}

TEST_F("Sparse float/double array dot products can be evaluated with string parameter", ArrayFixture) {
    f.check_all_float_executions(2.5*13.1 + 4.25*23.4, "{0:2.5,3:4.25,50:100.1}", DocId(6));
}

TEST_F("Sparse i32/i64 array dot products can be evaluated with pre-parsed object parameter", ArrayFixture) {
    // As before, we cheat a bit by having a different raw string vector than the pre-parsed vector.
    f.check_all_integer_executions(2*13 + 4*23, "[0 0 0]", DocId(5), "{0:2,3:4,50:100}");
}

TEST_F("Sparse float/double array dot products can be evaluated with pre-parsed object parameter", ArrayFixture) {
    f.check_all_float_executions(2.5*13.1 + 4.25*23.4, "[0 0 0]", DocId(6), "{0:2.5,3:4.25,50:100.1}");
}

struct WsetFixture : FixtureBase {
    void setup_integer_mappings(BasicType int_type) override {
        const std::vector<WeightedInt> doc7_values({WeightedInt(200, 7), WeightedInt(300, 13)});
        reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
                int_type,
                {{DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    }
};

TEST_F("i32/i64 wset dot products can be evaluated with string parameter", WsetFixture) {
    f.check_all_integer_executions(21*7 + 19*13, "{200:21,300:19,999:1234}", DocId(3));
}

TEST_F("string wset dot products can be evaluated with string parameter", WsetFixture) {
    std::vector<WeightedString> doc7_values{{WeightedString("bar", 7), WeightedString("baz", 41)}};
    reset_with_wset_value_reference_mappings<StringAttribute, WeightedString>(
            f, BasicType::STRING,
            {{DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    f.check_single_execution(5*7 + 3*41, "{bar:5,baz:3,nosuchkey:1234}", DocId(3));
}

TEST_F("integer enum dot products can be evaluated with string parameter", WsetFixture) {
    const std::vector<WeightedInt> doc7_values({WeightedInt(200, 7), WeightedInt(300, 13)});
    // We only check i32 here, since the enum (fast search) aspect is what matters here.
    reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
            f, BasicType::INT32,
            {{DocId(3), dummy_gid(7), DocId(7), doc7_values}},
            FastSearchConfig::ExplicitlyEnabled);
    f.check_single_execution(21*7 + 19*13, "{200:21,300:19,999:1234}", DocId(3));
}

// Observed TODOs out of scope for these tests:
// - pre-parsed vectors not currently implemented for weighted sets.
// - non-imported cases should also be tested for prepareSharedState.

TEST_MAIN() { TEST_RUN_ALL(); }
