// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/tensor_from_labels_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::StringAttribute;
using vespalib::eval::Value;
using vespalib::eval::Function;
using vespalib::eval::TensorSpec;
using vespalib::eval::SimpleValue;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using AttributePtr = search::AttributeVector::SP;
using FTA = FtTestAppBase;

Value::UP make_tensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

Value::UP make_empty(const vespalib::string &type) {
    return make_tensor(TensorSpec(type));
}

struct SetupFixture
{
    TensorFromLabelsBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture()
        : blueprint(),
          indexEnv()
    {
    }
};

TEST(TensorFromLabelsTest, require_that_blueprint_can_be_created_from_factory)
{
    SetupFixture f;
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "tensorFromLabels"));
}

TEST(TensorFromLabelsTest, require_that_setup_fails_if_source_spec_is_invalid)
{
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("source(foo)"));
}

TEST(TensorFromLabelsTest, require_that_setup_succeeds_with_attribute_source)
{
    SetupFixture f;
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("attribute(foo)"),
            StringList(), StringList().add("tensor"));
}

TEST(TensorFromLabelsTest, require_that_setup_succeeds_with_query_source)
{
    SetupFixture f;
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("query(foo)"),
            StringList(), StringList().add("tensor"));
}

struct ExecFixture
{
    BlueprintFactory factory;
    FtFeatureTest test;
    ExecFixture(const vespalib::string &feature)
        : factory(),
          test(factory, feature)
    {
        setup_search_features(factory);
        setupAttributeVectors();
        setupQueryEnvironment();
        EXPECT_TRUE(test.setup());
    }
    void setupAttributeVectors() {
        std::vector<AttributePtr> attrs;
        attrs.push_back(AttributeFactory::createAttribute("astr", AVC(AVBT::STRING,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("aint", AVC(AVBT::INT32,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("wsstr", AVC(AVBT::STRING,  AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("sint", AVC(AVBT::INT32,  AVCT::SINGLE)));

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        StringAttribute *astr = static_cast<StringAttribute *>(attrs[0].get());
        // Note that the weight parameter is not used
        astr->append(1, "a", 0);
        astr->append(1, "b", 0);
        astr->append(1, "c", 0);

        IntegerAttribute *aint = static_cast<IntegerAttribute *>(attrs[1].get());
        aint->append(1, 3, 0);
        aint->append(1, 5, 0);
        aint->append(1, 7, 0);
        
        IntegerAttribute *sint = static_cast<IntegerAttribute *>(attrs[3].get());
        sint->update(1, 5);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("astr_query", "[d e f e]");
        test.getQueryEnv().getProperties().add("aint_query", "[11 13 17]");
    }
    const Value &extractTensor(uint32_t docid) {
        return test.resolveObjectFeature(docid);
    }
    const Value &execute() {
        return extractTensor(1);
    }
};

// Tests for attribute source:

TEST(TensorFromLabelsTest, require_that_array_string_attribute_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromLabels(attribute(astr))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(astr{})")
                           .add({{"astr", "a"}}, 1)
                           .add({{"astr", "b"}}, 1)
                           .add({{"astr", "c"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_array_string_attribute_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromLabels(attribute(astr),dim)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(dim{})")
                           .add({{"dim", "a"}}, 1)
                           .add({{"dim", "b"}}, 1)
                           .add({{"dim", "c"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_array_integer_attribute_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromLabels(attribute(aint))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(aint{})")
                           .add({{"aint", "7"}}, 1)
                           .add({{"aint", "3"}}, 1)
                           .add({{"aint", "5"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_array_attribute_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromLabels(attribute(aint),dim)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(dim{})")
                           .add({{"dim", "7"}}, 1)
                           .add({{"dim", "3"}}, 1)
                           .add({{"dim", "5"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_single_value_integer_attribute_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromLabels(attribute(sint))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(sint{})")
                           .add({{"sint", "5"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_single_value_integer_attribute_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromLabels(attribute(sint),foobar)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(foobar{})")
                           .add({{"foobar", "5"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_empty_tensor_is_created_if_attribute_does_not_exists)
{
    ExecFixture f("tensorFromLabels(attribute(null))");
    EXPECT_EQ(*make_empty("tensor(null{})"), f.execute());
}

TEST(TensorFromLabelsTest, require_that_empty_tensor_is_created_if_attribute_type_is_not_supported)
{
    ExecFixture f("tensorFromLabels(attribute(wsstr))");
    EXPECT_EQ(*make_empty("tensor(wsstr{})"), f.execute());
}


// Tests for query source:

TEST(TensorFromLabelsTest, require_that_string_array_from_query_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromLabels(query(astr_query))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(astr_query{})")
                           .add({{"astr_query", "d"}}, 1)
                           .add({{"astr_query", "e"}}, 1)
                           .add({{"astr_query", "f"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_integer_array_from_query_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromLabels(query(aint_query))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(aint_query{})")
                           .add({{"aint_query", "13"}}, 1)
                           .add({{"aint_query", "17"}}, 1)
                           .add({{"aint_query", "11"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_string_array_from_query_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromLabels(query(astr_query),dim)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(dim{})")
                           .add({{"dim", "d"}}, 1)
                           .add({{"dim", "e"}}, 1)
                           .add({{"dim", "f"}}, 1)), f.execute());
}

TEST(TensorFromLabelsTest, require_that_empty_tensor_is_created_if_query_parameter_is_not_found)
{
    ExecFixture f("tensorFromLabels(query(null))");
    EXPECT_EQ(*make_empty("tensor(null{})"), f.execute());
}

GTEST_MAIN_RUN_ALL_TESTS()
