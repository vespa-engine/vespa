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
using SIAF = Schema::ImportedAttributeField;

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
