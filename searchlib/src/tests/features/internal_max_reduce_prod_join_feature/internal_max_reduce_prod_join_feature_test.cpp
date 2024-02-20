// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/features/internal_max_reduce_prod_join_feature.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::FloatingPointAttribute;
using CollectionType = FieldInfo::CollectionType;
using DataType = FieldInfo::DataType;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using AttributePtr = search::AttributeVector::SP;
using FTA = FtTestAppBase;

struct SetupFixture
{
    InternalMaxReduceProdJoinBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture()
        : blueprint(),
          indexEnv()
    {
        addAttribute("long", CollectionType::SINGLE, DataType::INT64);
        addAttribute("longarray", CollectionType::ARRAY, DataType::INT64);
        addAttribute("intarray", CollectionType::ARRAY, DataType::INT32);
        addAttribute("doublearray", CollectionType::ARRAY, DataType::DOUBLE);
    }

    void addAttribute(const vespalib::string &name, const CollectionType &collType, const DataType &dataType) {
        FieldInfo attrInfo(FieldType::ATTRIBUTE, collType, name, 0);
        attrInfo.set_data_type(dataType);
        indexEnv.getFields().push_back(attrInfo);
    }
};

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_blueprint_can_be_created)
{
    SetupFixture f;
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "internalMaxReduceProdJoin"));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_setup_fails_if_attribute_does_not_exist)
{
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("foo").add("bar"));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_setup_fails_if_attribute_is_of_wrong_type)
{
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("long").add("bar"));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_setup_fails_if_attribute_is_of_wrong_array_type)
{
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("doublearray").add("bar"));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_setup_succeeds_with_long_array_attribute)
{
    SetupFixture f;
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv,
                    StringList().add("longarray").add("query"),
                    StringList(),
                    StringList().add("scalar"));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_setup_succeeds_with_int_array_attribute)
{
    SetupFixture f;
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv,
                     StringList().add("intarray").add("query"),
                     StringList(),
                     StringList().add("scalar"));
}

struct ExecFixture
{
    BlueprintFactory factory;
    FtFeatureTest test;
    ExecFixture(const vespalib::string &feature)
            : factory(),
              test(factory, feature)
    {
        factory.addPrototype(std::make_shared<InternalMaxReduceProdJoinBlueprint>());
        setupAttributeVectors();
        setupQueryEnvironment();
        EXPECT_TRUE(test.setup());
    }

    void setupAttributeVectors() {
        vespalib::string attrIntArray = "intarray";
        vespalib::string attrLongArray = "longarray";

        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, DataType::INT64, attrLongArray);
        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, DataType::INT32, attrIntArray);

        std::vector<AttributePtr> attrs;
        attrs.push_back(AttributeFactory::createAttribute(attrLongArray, AVC(AVBT::INT64,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute(attrIntArray, AVC(AVBT::INT32,  AVCT::ARRAY)));
        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        IntegerAttribute *longArray = static_cast<IntegerAttribute *>(attrs[0].get());
        longArray->append(1, 1111, 0);
        longArray->append(1, 2222, 0);
        longArray->append(1, 78, 0);

        IntegerAttribute *intArray = static_cast<IntegerAttribute *>(attrs[1].get());
        intArray->append(1, 78, 0);
        intArray->append(1, 1111, 0);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }

    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("wset", "{1111:1234, 2222:2245}");
        test.getQueryEnv().getProperties().add("wsetnomatch", "{1:1000, 2:2000}");
        test.getQueryEnv().getProperties().add("array", "[1111,2222]");
        test.getQueryEnv().getProperties().add("negativewset", "{1111:-1000, 78:-42}");
    }

    bool evaluatesTo(feature_t expectedValue) {
        return test.execute(expectedValue);
    }

};

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_executor_returns_correct_result_for_long_array)
{
    ExecFixture f("internalMaxReduceProdJoin(longarray,wset)");
    EXPECT_FALSE(f.evaluatesTo(1234));
    EXPECT_TRUE(f.evaluatesTo(2245));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_executor_returns_correct_result_for_int_array)
{
    ExecFixture f("internalMaxReduceProdJoin(intarray,wset)");
    EXPECT_TRUE(f.evaluatesTo(1234));
    EXPECT_FALSE(f.evaluatesTo(2245));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_executor_returns_0_if_no_items_match)
{
    ExecFixture f("internalMaxReduceProdJoin(longarray,wsetnomatch)");
    EXPECT_TRUE(f.evaluatesTo(0.0));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_executor_return_0_if_query_is_not_a_weighted_set)
{
    ExecFixture f("internalMaxReduceProdJoin(longarray,array)");
    EXPECT_TRUE(f.evaluatesTo(0.0));
}

TEST(InternalMaxReduceProdJoinFeatureTest, require_that_executor_supports_negative_numbers)
{
    ExecFixture f("internalMaxReduceProdJoin(intarray,negativewset)");
    EXPECT_FALSE(f.evaluatesTo(-1000));
    EXPECT_TRUE(f.evaluatesTo(-42));
}

GTEST_MAIN_RUN_ALL_TESTS()
