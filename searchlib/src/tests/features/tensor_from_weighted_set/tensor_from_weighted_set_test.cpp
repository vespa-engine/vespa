// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/tensor_from_weighted_set_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
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
    TensorFromWeightedSetBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture()
        : blueprint(),
          indexEnv()
    {
    }
};

TEST(TensorFromWeightedSetTest, require_that_blueprint_can_be_created_from_factory)
{
    SetupFixture f;
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "tensorFromWeightedSet"));
}

TEST(TensorFromWeightedSetTest, require_that_setup_fails_if_source_spec_is_invalid)
{
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("source(foo)"));
}

TEST(TensorFromWeightedSetTest, require_that_setup_succeeds_with_attribute_source)
{
    SetupFixture f;
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("attribute(foo)"),
            StringList(), StringList().add("tensor"));
}

TEST(TensorFromWeightedSetTest, require_that_setup_succeeds_with_query_source)
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
        attrs.push_back(AttributeFactory::createAttribute("wsstr", AVC(AVBT::STRING,  AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("wsint", AVC(AVBT::INT32,  AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("astr", AVC(AVBT::STRING,  AVCT::ARRAY)));

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        StringAttribute *wsstr = static_cast<StringAttribute *>(attrs[0].get());
        wsstr->append(1, "a", 3);
        wsstr->append(1, "b", 5);
        wsstr->append(1, "c", 7);

        IntegerAttribute *wsint = static_cast<IntegerAttribute *>(attrs[1].get());
        wsint->append(1, 11, 3);
        wsint->append(1, 13, 5);
        wsint->append(1, 17, 7);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("wsquery", "{d:11,e:13,f:17}");
    }
    const Value &extractTensor(uint32_t docid) {
        return test.resolveObjectFeature(docid);
    }
    const Value &execute() {
        return extractTensor(1);
    }
};

TEST(TensorFromWeightedSetTest, require_that_weighted_set_string_attribute_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromWeightedSet(attribute(wsstr))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(wsstr{})")
                           .add({{"wsstr","b"}}, 5)
                           .add({{"wsstr","c"}}, 7)
                           .add({{"wsstr","a"}}, 3)), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_weighted_set_string_attribute_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromWeightedSet(attribute(wsstr),dim)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(dim{})")
                           .add({{"dim","a"}}, 3)
                           .add({{"dim","b"}}, 5)
                           .add({{"dim","c"}}, 7)), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_weighted_set_integer_attribute_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromWeightedSet(attribute(wsint))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(wsint{})")
                           .add({{"wsint","13"}}, 5)
                           .add({{"wsint","17"}}, 7)
                           .add({{"wsint","11"}}, 3)), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_weighted_set_integer_attribute_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromWeightedSet(attribute(wsint),dim)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(dim{})")
                           .add({{"dim","17"}}, 7)
                           .add({{"dim","11"}}, 3)
                           .add({{"dim","13"}}, 5)), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_weighted_set_from_query_can_be_converted_to_tensor_using_default_dimension)
{
    ExecFixture f("tensorFromWeightedSet(query(wsquery))");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(wsquery{})")
                           .add({{"wsquery","f"}}, 17)
                           .add({{"wsquery","d"}}, 11)
                           .add({{"wsquery","e"}}, 13)), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_weighted_set_from_query_can_be_converted_to_tensor_using_explicit_dimension)
{
    ExecFixture f("tensorFromWeightedSet(query(wsquery),dim)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(dim{})")
                           .add({{"dim","d"}}, 11)
                           .add({{"dim","e"}}, 13)
                           .add({{"dim","f"}}, 17)), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_empty_tensor_is_created_if_attribute_does_not_exists)
{
    ExecFixture f("tensorFromWeightedSet(attribute(null))");
    EXPECT_EQ(*make_empty("tensor(null{})"), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_empty_tensor_is_created_if_attribute_type_is_not_supported)
{
    ExecFixture f("tensorFromWeightedSet(attribute(astr))");
    EXPECT_EQ(*make_empty("tensor(astr{})"), f.execute());
}

TEST(TensorFromWeightedSetTest, require_that_empty_tensor_is_created_if_query_parameter_is_not_found)
{
    ExecFixture f("tensorFromWeightedSet(query(null))");
    EXPECT_EQ(*make_empty("tensor(null{})"), f.execute());
}

GTEST_MAIN_RUN_ALL_TESTS()
