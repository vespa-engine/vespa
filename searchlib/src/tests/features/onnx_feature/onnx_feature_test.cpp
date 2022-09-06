// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/issue.h>
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
using vespalib::Issue;

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
std::string source_dir = get_source_dir();
std::string vespa_dir = source_dir + "/" + "../../../../..";
std::string simple_model = vespa_dir + "/" + "eval/src/tests/tensor/onnx_wrapper/simple.onnx";
std::string dynamic_model = vespa_dir + "/" + "eval/src/tests/tensor/onnx_wrapper/dynamic.onnx";
std::string strange_names_model = source_dir + "/" + "strange_names.onnx";
std::string fragile_model = source_dir + "/" + "fragile.onnx";

uint32_t default_docid = 1;

vespalib::string expr_feature(const vespalib::string &name) {
    return fmt("rankingExpression(%s)", name.c_str());
}

vespalib::string onnx_feature(const vespalib::string &name) {
    return fmt("onnx(%s)", name.c_str());
}

vespalib::string onnx_feature_old(const vespalib::string &name) {
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
        factory.addPrototype(std::make_shared<OnnxBlueprint>("onnx"));
        factory.addPrototype(std::make_shared<OnnxBlueprint>("onnxModel"));
    }
    ~OnnxFeatureTest() override;
    void add_expr(const vespalib::string &name, const vespalib::string &expr) {
        vespalib::string feature_name = expr_feature(name);
        vespalib::string expr_name = feature_name + ".rankingScript";
        indexEnv.getProperties().add(expr_name, expr);
    }
    void add_onnx(OnnxModel model) {
        indexEnv.addOnnxModel(std::move(model));
    }
    bool try_compile(const vespalib::string &seed) {
        resolver->addSeed(seed);
        if (!resolver->compile()) {
            return false;
        }
        MatchDataLayout mdl;
        QueryEnvironment queryEnv(&indexEnv);
        match_data = mdl.createMatchData();
        program.setup(*match_data, queryEnv, overrides);
        return true;
    }
    void compile(const vespalib::string &seed) {
        ASSERT_TRUE(try_compile(seed));
    }
    TensorSpec get(const vespalib::string &feature, uint32_t docid) const {
        auto result = program.get_all_features(false);
        for (size_t i = 0; i < result.num_features(); ++i) {
            if (result.name_of(i) == feature) {
                return TensorSpec::from_value(result.resolve(i).as_object(docid));
            }
        }
        return {"error"};
    }
    TensorSpec get(uint32_t docid) const {
        auto result = program.get_seeds(false);
        EXPECT_EQ(1u, result.num_features());
        return TensorSpec::from_value(result.resolve(0).as_object(docid));
    }
};

OnnxFeatureTest::~OnnxFeatureTest() = default;

TEST_F(OnnxFeatureTest, simple_onnx_model_can_be_calculated) {
    add_expr("query_tensor", "tensor<float>(a[1],b[4]):[[docid,2,3,4]]");
    add_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    add_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    add_onnx(OnnxModel("simple", simple_model));
    compile(onnx_feature("simple"));
    EXPECT_EQ(get(1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get("onnx(simple).output", 1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get(2), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 84.0));
    EXPECT_EQ(get(3), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 89.0));
}

TEST_F(OnnxFeatureTest, simple_onnx_model_can_be_calculated_with_old_name) {
    add_expr("query_tensor", "tensor<float>(a[1],b[4]):[[docid,2,3,4]]");
    add_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    add_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    add_onnx(OnnxModel("simple", simple_model));
    compile(onnx_feature_old("simple"));
    EXPECT_EQ(get(1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get("onnxModel(simple).output", 1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get(2), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 84.0));
    EXPECT_EQ(get(3), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 89.0));
}

TEST_F(OnnxFeatureTest, dynamic_onnx_model_can_be_calculated) {
    add_expr("query_tensor", "tensor<float>(a[1],b[4]):[[docid,2,3,4]]");
    add_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    add_expr("bias_tensor", "tensor<float>(a[1],b[2]):[[4,5]]");
    add_onnx(OnnxModel("dynamic", dynamic_model));
    compile(onnx_feature("dynamic"));
    EXPECT_EQ(get(1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get("onnx(dynamic).output", 1), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 79.0));
    EXPECT_EQ(get(2), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 84.0));
    EXPECT_EQ(get(3), TensorSpec("tensor<float>(d0[1],d1[1])").add({{"d0",0},{"d1",0}}, 89.0));
}

TEST_F(OnnxFeatureTest, strange_input_and_output_names_are_normalized) {
    add_expr("input_0", "tensor<float>(a[2]):[10,20]");
    add_expr("input_1", "tensor<float>(a[2]):[5,10]");
    add_onnx(OnnxModel("strange_names", strange_names_model));
    compile(onnx_feature("strange_names"));
    auto expect_add = TensorSpec("tensor<float>(d0[2])").add({{"d0",0}},15).add({{"d0",1}},30);
    auto expect_sub = TensorSpec("tensor<float>(d0[2])").add({{"d0",0}},5).add({{"d0",1}},10);
    EXPECT_EQ(get(1), expect_add);
    EXPECT_EQ(get("onnx(strange_names).foo_bar", 1), expect_add);
    EXPECT_EQ(get("onnx(strange_names)._baz_0", 1), expect_sub);
}

TEST_F(OnnxFeatureTest, input_features_and_output_names_can_be_specified) {
    add_expr("my_first_input", "tensor<float>(a[2]):[10,20]");
    add_expr("my_second_input", "tensor<float>(a[2]):[5,10]");
    add_onnx(std::move(OnnxModel("custom_names", strange_names_model)
             .input_feature("input:0", "rankingExpression(my_first_input)")
             .input_feature("input/1", "rankingExpression(my_second_input)")
             .output_name("foo/bar", "my_first_output")
             .output_name("-baz:0", "my_second_output")));
    compile(onnx_feature("custom_names"));
    auto expect_add = TensorSpec("tensor<float>(d0[2])").add({{"d0",0}},15).add({{"d0",1}},30);
    auto expect_sub = TensorSpec("tensor<float>(d0[2])").add({{"d0",0}},5).add({{"d0",1}},10);
    EXPECT_EQ(get(1), expect_add);
    EXPECT_EQ(get("onnx(custom_names).my_first_output", 1), expect_add);
    EXPECT_EQ(get("onnx(custom_names).my_second_output", 1), expect_sub);
}

TEST_F(OnnxFeatureTest, fragile_model_can_be_evaluated) {
    add_expr("in1", "tensor<float>(x[2]):[docid,5]");
    add_expr("in2", "tensor<float>(x[2]):[docid,10]");
    add_onnx(std::move(OnnxModel("fragile", fragile_model).dry_run_on_setup(true)));
    EXPECT_TRUE(try_compile(onnx_feature("fragile")));
    EXPECT_EQ(get(1), TensorSpec::from_expr("tensor<float>(d0[2]):[2,15]"));
    EXPECT_EQ(get(3), TensorSpec::from_expr("tensor<float>(d0[2]):[6,15]"));
}

struct MyIssues : Issue::Handler {
    std::vector<vespalib::string> list;
    Issue::Binding capture;
    MyIssues() : list(), capture(Issue::listen(*this)) {}
    void handle(const Issue &issue) override { list.push_back(issue.message()); }
};

TEST_F(OnnxFeatureTest, broken_model_evaluates_to_all_zeros) {
    add_expr("in1", "tensor<float>(x[2]):[docid,5]");
    add_expr("in2", "tensor<float>(x[3]):[docid,10,31515]");
    add_onnx(std::move(OnnxModel("fragile", fragile_model).dry_run_on_setup(false)));
    EXPECT_TRUE(try_compile(onnx_feature("fragile")));
    MyIssues my_issues;
    EXPECT_EQ(my_issues.list.size(), 0);
    EXPECT_EQ(get(1), TensorSpec::from_expr("tensor<float>(d0[2]):[0,0]"));
    EXPECT_EQ(my_issues.list.size(), 1);
    EXPECT_EQ(get(3), TensorSpec::from_expr("tensor<float>(d0[2]):[0,0]"));
    ASSERT_EQ(my_issues.list.size(), 2);
    EXPECT_EQ(my_issues.list[0], my_issues.list[1]);
}

TEST_F(OnnxFeatureTest, broken_model_fails_with_dry_run) {
    add_expr("in1", "tensor<float>(x[2]):[docid,5]");
    add_expr("in2", "tensor<float>(x[3]):[docid,10,31515]");
    add_onnx(std::move(OnnxModel("fragile", fragile_model).dry_run_on_setup(true)));
    EXPECT_FALSE(try_compile(onnx_feature("fragile")));
}

GTEST_MAIN_RUN_ALL_TESTS()
