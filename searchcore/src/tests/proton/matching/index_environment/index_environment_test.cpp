// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/searchcore/proton/matching/indexenvironment.h>
#include <vespa/searchlib/fef/onnx_models.h>
#include <vespa/searchlib/fef/ranking_expressions.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>

using namespace proton::matching;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::IRankingAssetsRepo;
using search::fef::OnnxModel;
using search::fef::OnnxModels;
using search::fef::Properties;
using search::fef::RankingExpressions;
using search::index::Schema;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using vespalib::eval::ConstantValue;
using SAF = Schema::AttributeField;
using SIAF = Schema::ImportedAttributeField;
using SIF = Schema::IndexField;

const std::string my_expr_ref(
    "this is my reference ranking expression.\n"
    "this is my reference ranking expression.\n"
    "it will not compile into a function.\n"
    "it will not compile into a function.\n"
    "it is just some text, that can also be compressed...\n"
    "it is just some text, that can also be compressed...\n");

RankingExpressions make_expressions() {
    RankingExpressions expr_list;
    expr_list.add("expr1", TEST_PATH("my_expr"));
    expr_list.add("expr2", TEST_PATH("my_expr.lz4"));
    return expr_list;
}

OnnxModels make_models() {
    OnnxModels::Vector list;
    list.emplace_back(std::move(OnnxModel("model1", "path1").input_feature("input1","feature1").output_name("output1", "out1")));
    list.emplace_back(OnnxModel("model2", "path2"));
    return {std::move(list)};
}

struct MyRankingAssetsRepo : public IRankingAssetsRepo {
    RankingExpressions _expressions;
    OnnxModels _onnxModels;
    MyRankingAssetsRepo(RankingExpressions expressions, OnnxModels onnxModels)
        : _expressions(std::move(expressions)),
          _onnxModels(std::move(onnxModels))
    {}
    ~MyRankingAssetsRepo() override;
    ConstantValue::UP getConstant(const std::string &) const override {
        return {};
    }

    std::string getExpression(const std::string & name) const override {
        return _expressions.loadExpression(name);
    }

    const OnnxModel *getOnnxModel(const std::string & name) const override {
        return _onnxModels.getModel(name);
    }
};

MyRankingAssetsRepo::~MyRankingAssetsRepo() = default;

Schema::UP
buildSchema()
{
    Schema::UP result = std::make_unique<Schema>();
    result->addImportedAttributeField(SIAF("imported_a", DataType::INT32, CollectionType::SINGLE));
    result->addImportedAttributeField(SIAF("imported_b", DataType::STRING, CollectionType::ARRAY));
    return result;
}

Schema::UP
buildEmptySchema()
{
    return std::make_unique<Schema>();
}

struct Fixture {
    MyRankingAssetsRepo repo;
    Schema::UP schema;
    IndexEnvironment env;
    explicit Fixture(Schema::UP schema_)
        : repo(make_expressions(), make_models()),
          schema(std::move(schema_)),
          env(7, *schema, Properties(), repo)
    {
    }
    ~Fixture();
    void assert_field_common(const FieldInfo *field,
                             size_t idx,
                             const std::string &name,
                             DataType dataType,
                             CollectionType collectionType) const {
        EXPECT_EQ(field, env.getFieldByName(name));
        EXPECT_EQ(name, field->name());
        EXPECT_EQ(dataType, field->get_data_type());
        EXPECT_TRUE(collectionType == field->collection());
        EXPECT_EQ(idx, field->id());
    }
    void assertField(size_t idx,
                     const std::string &name,
                     DataType dataType,
                     CollectionType collectionType) const {
        SCOPED_TRACE("idx=" + std::to_string(idx) + ", name=" + name);
        const FieldInfo *field = env.getField(idx);
        ASSERT_NE(nullptr, field);
        assert_field_common(field, idx, name, dataType, collectionType);
    }
    void assertHiddenAttributeField(size_t idx,
                                    const std::string &name,
                                    DataType dataType,
                                    CollectionType collectionType) const {
        SCOPED_TRACE("idx=" + std::to_string(idx) + ", name=" + name);
        const FieldInfo *field = env.getField(idx);
        ASSERT_NE(nullptr, field);
        assert_field_common(field, idx, name, dataType, collectionType);
        EXPECT_FALSE(field->hasAttribute());
        EXPECT_TRUE(field->type() == FieldType::HIDDEN_ATTRIBUTE);
        EXPECT_TRUE(field->isFilter());
    }
    void assertAttributeField(size_t idx,
                              const std::string &name,
                              DataType dataType,
                              CollectionType collectionType) const {
        SCOPED_TRACE("idx=" + std::to_string(idx) + ", name=" + name);
        const FieldInfo *field = env.getField(idx);
        ASSERT_NE(nullptr, field);
        assert_field_common(field, idx, name, dataType, collectionType);
        EXPECT_TRUE(field->hasAttribute());
        EXPECT_TRUE(field->type() == FieldType::ATTRIBUTE);
        EXPECT_FALSE(field->isFilter());
    }
    void assert_virtual_field(size_t idx,
                              const std::string& name) const {
        SCOPED_TRACE("idx=" + std::to_string(idx) + ", name=" + name);
        const FieldInfo *field = env.getField(idx);
        ASSERT_NE(nullptr, field);
        assert_field_common(field, idx, name, DataType::COMBINED, CollectionType::ARRAY);
        EXPECT_TRUE(field->type() == FieldType::VIRTUAL);
    }
};

Fixture::~Fixture() = default;

TEST(IndexEnvironmentTest, require_that_document_meta_store_is_always_extracted_in_index_environment)
{
    Fixture f(buildEmptySchema());
    ASSERT_EQ(1u, f.env.getNumFields());
    f.assertHiddenAttributeField(0, "[documentmetastore]", DataType::RAW, CollectionType::SINGLE);
}

TEST(IndexEnvironmentTest, require_that_distribution_key_is_visible_in_index_environment)
{
    Fixture f(buildEmptySchema());
    ASSERT_EQ(7u, f.env.getDistributionKey());
}

TEST(IndexEnvironmentTest, require_that_imported_attribute_fields_are_extracted_in_index_environment)
{
    Fixture f(buildSchema());
    ASSERT_EQ(3u, f.env.getNumFields());
    f.assertAttributeField(0, "imported_a", DataType::INT32, CollectionType::SINGLE);
    f.assertAttributeField(1, "imported_b", DataType::STRING, CollectionType::ARRAY);
    EXPECT_EQ("[documentmetastore]", f.env.getField(2)->name());
}

Schema::UP schema_with_virtual_fields() {
    // These attributes represent parts of the following fields:
    //   * field person_map type map<int, person>, where the person struct has the fields name and year.
    //   * field int_map type map<int, int>
    //
    // In this example 'person_map', 'person_map.value', and 'int_map' are virtual fields as seen from the ranking framework.
    auto result = std::make_unique<Schema>();
    result->addAttributeField(SAF("person_map.key", DataType::INT32, CollectionType::ARRAY));
    result->addAttributeField(SAF("person_map.value.name", DataType::STRING, CollectionType::ARRAY));
    result->addAttributeField(SAF("person_map.value.year", DataType::INT32, CollectionType::ARRAY));
    result->addImportedAttributeField(SAF("int_map.key", DataType::INT32, CollectionType::ARRAY));
    result->addImportedAttributeField(SAF("int_map.value", DataType::INT32, CollectionType::ARRAY));
    // Index fields do not represent virtual fields:
    result->addIndexField(SIF("url.hostname", DataType::STRING, CollectionType::SINGLE));
    result->addIndexField(SIF("url.port", DataType::STRING, CollectionType::SINGLE));
    return result;
}

TEST(IndexEnvironmentTest, virtual_fields_are_extracted_in_index_environment)
{
    Fixture f(schema_with_virtual_fields());
    ASSERT_EQ(11u, f.env.getNumFields());
    f.assertAttributeField(0, "person_map.key", DataType::INT32, CollectionType::ARRAY);
    f.assertAttributeField(1, "person_map.value.name", DataType::STRING, CollectionType::ARRAY);
    f.assertAttributeField(2, "person_map.value.year", DataType::INT32, CollectionType::ARRAY);
    f.assertField(3, "url.hostname", DataType::STRING, CollectionType::SINGLE);
    f.assertField(4, "url.port", DataType::STRING, CollectionType::SINGLE);
    f.assertAttributeField(5, "int_map.key", DataType::INT32, CollectionType::ARRAY);
    f.assertAttributeField(6, "int_map.value", DataType::INT32, CollectionType::ARRAY);
    EXPECT_EQ("[documentmetastore]", f.env.getField(7)->name());
    f.assert_virtual_field(8, "int_map");
    f.assert_virtual_field(9, "person_map");
    f.assert_virtual_field(10, "person_map.value");
}

TEST(IndexEnvironmentTest, require_that_onnx_model_config_can_be_obtained)
{
    Fixture f1(buildEmptySchema());
    {
        auto model = f1.env.getOnnxModel("model1");
        ASSERT_TRUE(model != nullptr);
        EXPECT_EQ(model->file_path(), std::string("path1"));
        EXPECT_EQ(model->input_feature("input1").value(), std::string("feature1"));
        EXPECT_EQ(model->output_name("output1").value(), std::string("out1"));
    }
    {
        auto model = f1.env.getOnnxModel("model2");
        ASSERT_TRUE(model != nullptr);
        EXPECT_EQ(model->file_path(), std::string("path2"));
        EXPECT_FALSE(model->input_feature("input1").has_value());
        EXPECT_FALSE(model->output_name("output1").has_value());
    }
    EXPECT_TRUE(f1.env.getOnnxModel("model3") == nullptr);
}

TEST(IndexEnvironmentTest, require_that_external_ranking_expressions_can_be_obtained)
{
    Fixture f1(buildEmptySchema());
    auto expr1 = f1.env.getRankingExpression("expr1");
    auto expr2 = f1.env.getRankingExpression("expr2");
    auto expr3 = f1.env.getRankingExpression("expr3");
    EXPECT_EQ(expr1, my_expr_ref);
    EXPECT_EQ(expr2, my_expr_ref);
    EXPECT_TRUE(expr3.empty());
}

GTEST_MAIN_RUN_ALL_TESTS()
