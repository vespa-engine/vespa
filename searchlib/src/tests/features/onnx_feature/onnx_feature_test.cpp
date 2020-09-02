// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/features/onnx_feature.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/fef/test/test_features.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using vespalib::make_string_short::fmt;
using vespalib::eval::TensorSpec;

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
std::string source_dir = get_source_dir();
std::string vespa_dir = source_dir + "/" + "../../../../..";
std::string simple_model = vespa_dir + "/" + "eval/src/tests/tensor/onnx_wrapper/simple.onnx";
std::string dynamic_model = vespa_dir + "/" + "eval/src/tests/tensor/onnx_wrapper/dynamic.onnx";

uint32_t default_docid = 1;

vespalib::string expr_feature(const vespalib::string &name) {
    return fmt("rankingExpression(%s)", name.c_str());
}

vespalib::string onnx_feature(const vespalib::string &name) {
    return fmt("onnxModel(%s)", name.c_str());
}

struct OnnxFeatureTest : ::testing::Test {
    BlueprintFactory factory;
    IndexEnvironment indexEnv;
    BlueprintResolver::SP resolver;
    Properties overrides;
    MatchData::UP match_data;
    RankProgram program;
    OnnxFeatureTest() : factory(), indexEnv(), resolver(new BlueprintResolver(factory, indexEnv)),
                        overrides(), match_data(), program(resolver)
    {
        factory.addPrototype(std::make_shared<DocidBlueprint>());
        factory.addPrototype(std::make_shared<RankingExpressionBlueprint>());
        factory.addPrototype(std::make_shared<OnnxBlueprint>());
    }
    void add_expr(const vespalib::string &name, const vespalib::string &expr) {
        vespalib::string feature_name = expr_feature(name);
        vespalib::string expr_name = feature_name + ".rankingScript";
        indexEnv.getProperties().add(expr_name, expr);
    }
    void add_onnx(const vespalib::string &name, const vespalib::string &file) {
        indexEnv.addOnnxModel(name, file);
    }
    void compile(const vespalib::string &seed) {
        resolver->addSeed(seed);
        ASSERT_TRUE(resolver->compile());
        MatchDataLayout mdl;
        QueryEnvironment queryEnv(&indexEnv);
        match_data = mdl.createMatchData();
        program.setup(*match_data, queryEnv, overrides);
    }
    TensorSpec get(const vespalib::string &feature, uint32_t docid) {
        auto result = program.get_all_features(false);
        for (size_t i = 0; i < result.num_features(); ++i) {
            if (result.name_of(i) == feature) {
                return TensorSpec::from_value(result.resolve(i).as_object(docid));
            }
        }
        return TensorSpec("error");
    }
    TensorSpec get(uint32_t docid) {
        auto result = program.get_seeds(false);
        EXPECT_EQ(1u, result.num_features());
        return TensorSpec::from_value(result.resolve(0).as_object(docid));
    }
};

TEST_F(OnnxFeatureTest, simple_onnx_model_can_be_calculated) {
    add_expr("query_tensor", "tensor<float>(a[1],b[4]):[[docid,2,3,4]]");
    add_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    add_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    add_onnx("simple", simple_model);
    compile(onnx_feature("simple"));
    EXPECT_EQ(get(1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get("onnxModel(simple).output", 1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get(2), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 84.0));
    EXPECT_EQ(get(3), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 89.0));
}

TEST_F(OnnxFeatureTest, dynamic_onnx_model_can_be_calculated) {
    add_expr("query_tensor", "tensor<float>(a[1],b[4]):[[docid,2,3,4]]");
    add_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    add_expr("bias_tensor", "tensor<float>(a[1],b[2]):[[4,5]]");
    add_onnx("dynamic", dynamic_model);
    compile(onnx_feature("dynamic"));
    EXPECT_EQ(get(1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get("onnxModel(dynamic).output", 1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get(2), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 84.0));
    EXPECT_EQ(get(3), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 89.0));
}

GTEST_MAIN_RUN_ALL_TESTS()
