// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/searchcore/proton/matching/indexenvironment.h>
#include <vespa/searchcore/proton/matching/ranking_expressions.h>
#include <vespa/searchcore/proton/matching/onnx_models.h>

using namespace proton::matching;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::Properties;
using search::fef::OnnxModel;
using search::index::Schema;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using vespalib::eval::ConstantValue;
using SAF = Schema::AttributeField;
using SIAF = Schema::ImportedAttributeField;
using SIF = Schema::IndexField;

const vespalib::string my_expr_ref(
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
    ConstantValue::UP getConstant(const vespalib::string &) const override {
        return {};
    }

    vespalib::string getExpression(const vespalib::string & name) const override {
        return _expressions.loadExpression(name);
    }

    const OnnxModel *getOnnxModel(const vespalib::string & name) const override {
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
    const FieldInfo *assertField(size_t idx,
                                 const vespalib::string &name,
                                 DataType dataType,
                                 CollectionType collectionType) const {
        const FieldInfo *field = env.getField(idx);
        ASSERT_TRUE(field != nullptr);
        EXPECT_EQUAL(field, env.getFieldByName(name));
        EXPECT_EQUAL(name, field->name());
        EXPECT_EQUAL(dataType, field->get_data_type());
        EXPECT_TRUE(collectionType == field->collection());
        EXPECT_EQUAL(idx, field->id());
        return field;
    }
    void assertHiddenAttributeField(size_t idx,
                                    const vespalib::string &name,
                                    DataType dataType,
                                    CollectionType collectionType) const {
        const FieldInfo *field = assertField(idx, name, dataType, collectionType);
        EXPECT_FALSE(field->hasAttribute());
        EXPECT_TRUE(field->type() == FieldType::HIDDEN_ATTRIBUTE);
        EXPECT_TRUE(field->isFilter());
    }
    void assertAttributeField(size_t idx,
                              const vespalib::string &name,
                              DataType dataType,
                              CollectionType collectionType) const {
        const FieldInfo *field = assertField(idx, name, dataType, collectionType);
        EXPECT_TRUE(field->hasAttribute());
        EXPECT_TRUE(field->type() == FieldType::ATTRIBUTE);
        EXPECT_FALSE(field->isFilter());
    }
    void assert_virtual_field(size_t idx,
                              const vespalib::string& name) const {
        const auto* field = assertField(idx, name, DataType::COMBINED, CollectionType::ARRAY);
        EXPECT_TRUE(field->type() == FieldType::VIRTUAL);
    }
};

TEST_F("require that document meta store is always extracted in index environment", Fixture(buildEmptySchema()))
{
    ASSERT_EQUAL(1u, f.env.getNumFields());
    TEST_DO(f.assertHiddenAttributeField(0, "[documentmetastore]", DataType::RAW, CollectionType::SINGLE));
}

TEST_F("require that distribution key is visible in index environment", Fixture(buildEmptySchema()))
{
    ASSERT_EQUAL(7u, f.env.getDistributionKey());
}

TEST_F("require that imported attribute fields are extracted in index environment", Fixture(buildSchema()))
{
    ASSERT_EQUAL(3u, f.env.getNumFields());
    TEST_DO(f.assertAttributeField(0, "imported_a", DataType::INT32, CollectionType::SINGLE));
    TEST_DO(f.assertAttributeField(1, "imported_b", DataType::STRING, CollectionType::ARRAY));
    EXPECT_EQUAL("[documentmetastore]", f.env.getField(2)->name());
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

TEST_F("virtual fields are extracted in index environment", Fixture(schema_with_virtual_fields()))
{
    ASSERT_EQUAL(11u, f.env.getNumFields());
    TEST_DO(f.assertAttributeField(0, "person_map.key", DataType::INT32, CollectionType::ARRAY));
    TEST_DO(f.assertAttributeField(1, "person_map.value.name", DataType::STRING, CollectionType::ARRAY));
    TEST_DO(f.assertAttributeField(2, "person_map.value.year", DataType::INT32, CollectionType::ARRAY));
    TEST_DO(f.assertField(3, "url.hostname", DataType::STRING, CollectionType::SINGLE));
    TEST_DO(f.assertField(4, "url.port", DataType::STRING, CollectionType::SINGLE));
    TEST_DO(f.assertAttributeField(5, "int_map.key", DataType::INT32, CollectionType::ARRAY));
    TEST_DO(f.assertAttributeField(6, "int_map.value", DataType::INT32, CollectionType::ARRAY));
    EXPECT_EQUAL("[documentmetastore]", f.env.getField(7)->name());
    TEST_DO(f.assert_virtual_field(8, "int_map"));
    TEST_DO(f.assert_virtual_field(9, "person_map"));
    TEST_DO(f.assert_virtual_field(10, "person_map.value"));
}

TEST_F("require that onnx model config can be obtained", Fixture(buildEmptySchema())) {
    {
        auto model = f1.env.getOnnxModel("model1");
        ASSERT_TRUE(model != nullptr);
        EXPECT_EQUAL(model->file_path(), vespalib::string("path1"));
        EXPECT_EQUAL(model->input_feature("input1").value(), vespalib::string("feature1"));
        EXPECT_EQUAL(model->output_name("output1").value(), vespalib::string("out1"));
    }
    {
        auto model = f1.env.getOnnxModel("model2");
        ASSERT_TRUE(model != nullptr);
        EXPECT_EQUAL(model->file_path(), vespalib::string("path2"));
        EXPECT_FALSE(model->input_feature("input1").has_value());
        EXPECT_FALSE(model->output_name("output1").has_value());
    }
    EXPECT_TRUE(f1.env.getOnnxModel("model3") == nullptr);
}

TEST_F("require that external ranking expressions can be obtained", Fixture(buildEmptySchema())) {
    auto expr1 = f1.env.getRankingExpression("expr1");
    auto expr2 = f1.env.getRankingExpression("expr2");
    auto expr3 = f1.env.getRankingExpression("expr3");
    EXPECT_EQUAL(expr1, my_expr_ref);
    EXPECT_EQUAL(expr2, my_expr_ref);
    EXPECT_TRUE(expr3.empty());
}

TEST_MAIN() { TEST_RUN_ALL(); }
