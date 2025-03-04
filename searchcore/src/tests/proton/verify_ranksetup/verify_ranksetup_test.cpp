// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/onnx_model.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <filesystem>
#include <initializer_list>
#include <map>
#include <set>
#include <string>
#include <vector>

const char *prog = "../../../apps/verify_ranksetup/vespa-verify-ranksetup-bin";
const std::string gen_dir("generated");

const char *valid_feature = "value(0)";
const char *invalid_feature = "invalid_feature_name and format";

using namespace search::fef::indexproperties;
using namespace search::index;

using search::fef::OnnxModel;
using search::index::schema::CollectionType;
using search::index::schema::DataType;

using vespalib::make_string_short::fmt;

enum class SearchMode { INDEXED, STREAMING, BOTH };

struct Writer {
    FILE *file;
    explicit Writer(const std::string &file_name) {
        file = fopen(file_name.c_str(), "w");
        EXPECT_TRUE(file != nullptr);
    }
    void fmt(const char *format, ...) const __attribute__((format(printf,2,3)))
    {
        va_list ap;
        va_start(ap, format);
        vfprintf(file, format, ap);
        va_end(ap);
    }
    ~Writer() { fclose(file); }
};

void verify_dir() {
    std::string pwd(getenv("PWD"));
    ASSERT_NE(pwd.find("searchcore/src/tests/proton/verify_ranksetup"), pwd.npos);
}

//-----------------------------------------------------------------------------

struct Attribute {
    std::string dataType;
    std::string collectionType;
    std::string imported;
    Attribute(const std::string &dataType_,
              const std::string &collectionType_,
              const std::string &imported_)
        : dataType(dataType_), collectionType(collectionType_), imported(imported_)
    {}
    ~Attribute();
};

Attribute::~Attribute() = default;

struct CommonSetup {
    std::map<std::string,std::pair<std::string,std::string> > indexes;
    std::map<std::string,Attribute>                           attributes;
    std::map<std::string,std::string>                         properties;
    std::map<std::string,std::string>                         constants;
    std::vector<bool>                                         extra_profiles;
    std::map<std::string,std::string>                         ranking_expressions;
    std::map<std::string,OnnxModel>                           onnx_models;
    CommonSetup();
    ~CommonSetup();
    void add_onnx_model(OnnxModel model) {
        onnx_models.insert_or_assign(model.name(), std::move(model));
    }
    void index(const std::string &name, schema::DataType data_type,
               schema::CollectionType collection_type)
    {
        indexes[name].first = schema::getTypeName(data_type);
        indexes[name].second = schema::getTypeName(collection_type);
    }
    void attribute(const std::string &name, schema::DataType data_type,
                   schema::CollectionType collection_type, bool imported = false)
    {
        attributes.emplace(name, Attribute(schema::getTypeName(data_type),
                                           schema::getTypeName(collection_type),
                                           (imported ? "true" : "false")));
    }
    void property(const std::string &name, const std::string &val) {
        properties[name] = val;
    }
    void query_feature_type(const std::string &name, const std::string &type) {
        property(fmt("vespa.type.query.%s", name.c_str()), type);
    }
    void query_feature_default_value(const std::string &name, const std::string &expr) {
        property(fmt("query(%s)", name.c_str()), expr);
    }
    void rank_expr(const std::string &name, const std::string &expr) {
        property(fmt("rankingExpression(%s).rankingScript", name.c_str()), expr);
    }
    void ext_rank_expr(const std::string &name, const std::string &file) {
        auto expr_name = fmt("my_expr_%s", name.c_str());
        property(fmt("rankingExpression(%s).expressionName", name.c_str()), expr_name);
        ranking_expressions.insert_or_assign(expr_name, TEST_PATH(file));
    }
    void first_phase(const std::string &feature) {
        property(rank::FirstPhase::NAME, feature);
    }
    void second_phase(const std::string &feature) {
        property(rank::SecondPhase::NAME, feature);
    }
    void match_feature(const std::string &feature) {
        property(match::Feature::NAME, feature);
    }
    void summary_feature(const std::string &feature) {
        property(summary::Feature::NAME, feature);
    }
    void dump_feature(const std::string &feature) {
        property(dump::Feature::NAME, feature);
    }
    void good_profile() {
        extra_profiles.push_back(true);
    }
    void bad_profile() {
        extra_profiles.push_back(false);
    }
    void write_attributes(const Writer &out) {
        out.fmt("attribute[%zu]\n", attributes.size());
        auto pos = attributes.begin();
        for (size_t i = 0; pos != attributes.end(); ++pos, ++i) {
            out.fmt("attribute[%zu].name \"%s\"\n", i, pos->first.c_str());
            out.fmt("attribute[%zu].datatype %s\n", i, pos->second.dataType.c_str());
            out.fmt("attribute[%zu].collectiontype %s\n", i, pos->second.collectionType.c_str());
            out.fmt("attribute[%zu].imported %s\n", i, pos->second.imported.c_str());
        }
    }
    void write_indexschema(const Writer &out) {
        out.fmt("indexfield[%zu]\n", indexes.size());
        auto pos = indexes.begin();
        for (size_t i = 0; pos != indexes.end(); ++pos, ++i) {
            out.fmt("indexfield[%zu].name \"%s\"\n", i, pos->first.c_str());
            out.fmt("indexfield[%zu].datatype %s\n", i, pos->second.first.c_str());
            out.fmt("indexfield[%zu].collectiontype %s\n", i, pos->second.second.c_str());
        }
    }
    void write_vsmfield(const Writer &out, size_t idx, std::string name, std::string dataType) {
        out.fmt("fieldspec[%zu].name \"%s\"\n", idx, name.c_str());
        if (dataType == "STRING") {
            out.fmt("fieldspec[%zu].searchmethod AUTOUTF8\n", idx);
            out.fmt("fieldspec[%zu].normalize LOWERCASE\n", idx);
        } else {
            out.fmt("fieldspec[%zu].searchmethod %s\n", idx, dataType.c_str());
        }
    }
    void write_vsmfields(const Writer &out) {
        std::set<std::string> allFields;
        size_t i = 0;
        for (const auto & field : indexes) {
            write_vsmfield(out, i, field.first, field.second.first);
            out.fmt("fieldspec[%zu].fieldtype INDEX\n", i);
            i++;
            allFields.insert(field.first);
        }
        for (const auto & field : attributes) {
            if (allFields.count(field.first) != 0) continue;
            write_vsmfield(out, i, field.first, field.second.dataType);
            out.fmt("fieldspec[%zu].fieldtype ATTRIBUTE\n", i);
            i++;
            allFields.insert(field.first);
        }
        out.fmt("documenttype[0].name \"foobar\"\n");
        size_t j = 0;
        for (const auto & field : allFields) {
            out.fmt("documenttype[0].index[%zu].name \"%s\"\n", j, field.c_str());
            out.fmt("documenttype[0].index[%zu].field[0].name \"%s\"\n", j, field.c_str());
            j++;
        }
    }
    void write_rank_profiles(const Writer &out) {
        out.fmt("rankprofile[%zu]\n", extra_profiles.size() + 1);
        out.fmt("rankprofile[0].name \"default\"\n");
        auto pos = properties.begin();
        for (size_t i = 0; pos != properties.end(); ++pos, ++i) {
            out.fmt("rankprofile[0].fef.property[%zu]\n", properties.size());
            out.fmt("rankprofile[0].fef.property[%zu].name \"%s\"\n", i, pos->first.c_str());
            out.fmt("rankprofile[0].fef.property[%zu].value \"%s\"\n", i, pos->second.c_str());
        }
        for (size_t i = 1; i < (extra_profiles.size() + 1); ++i) {
            out.fmt("rankprofile[%zu].name \"extra_%zu\"\n", i, i);
            out.fmt("rankprofile[%zu].fef.property[%zu].name \"%s\"\n", i, i, rank::FirstPhase::NAME.c_str());
            out.fmt("rankprofile[%zu].fef.property[%zu].value \"%s\"\n", i, i, extra_profiles[i-1]?valid_feature:invalid_feature);
        }
    }
    void write_ranking_constants(const Writer &out) {
        size_t idx = 0;
        for (const auto &entry: constants) {
            out.fmt("constant[%zu].name \"%s\"\n", idx, entry.first.c_str());
            out.fmt("constant[%zu].fileref \"12345\"\n", idx);
            out.fmt("constant[%zu].type \"%s\"\n", idx, entry.second.c_str());
            ++idx;
        }
    }
    void write_ranking_expressions(const Writer &out) {
        size_t idx = 0;
        for (const auto &entry: ranking_expressions) {
            out.fmt("expression[%zu].name \"%s\"\n", idx, entry.first.c_str());
            out.fmt("expression[%zu].fileref \"expr_ref_%zu\"\n", idx, idx);
            ++idx;
        }
    }
    void write_onnx_models(const Writer &out) {
        size_t idx = 0;
        for (const auto &entry: onnx_models) {
            out.fmt("model[%zu].name \"%s\"\n", idx, entry.second.name().c_str());
            out.fmt("model[%zu].fileref \"onnx_ref_%zu\"\n", idx, idx);
            size_t idx2 = 0;
            for (const auto &input: entry.second.inspect_input_features()) {
                out.fmt("model[%zu].input[%zu].name \"%s\"\n", idx, idx2, input.first.c_str());
                out.fmt("model[%zu].input[%zu].source \"%s\"\n", idx, idx2, input.second.c_str());
                ++idx2;
            }
            idx2 = 0;
            for (const auto &output: entry.second.inspect_output_names()) {
                out.fmt("model[%zu].output[%zu].name \"%s\"\n", idx, idx2, output.first.c_str());
                out.fmt("model[%zu].output[%zu].as \"%s\"\n", idx, idx2, output.second.c_str());
                ++idx2;
            }
            out.fmt("model[%zu].dry_run_on_setup %s\n", idx, entry.second.dry_run_on_setup() ? "true" : "false");
            ++idx;
        }
    }
    void write_self_cfg(const Writer &out) {
        size_t idx = 0;
        for (const auto &entry: ranking_expressions) {
            out.fmt("file[%zu].ref \"expr_ref_%zu\"\n", idx, idx);
            out.fmt("file[%zu].path \"%s\"\n", idx, entry.second.c_str());
            ++idx;
        }
        idx = 0;
        for (const auto &entry: onnx_models) {
            out.fmt("file[%zu].ref \"onnx_ref_%zu\"\n", idx, idx);
            out.fmt("file[%zu].path \"%s\"\n", idx, entry.second.file_path().c_str());
            ++idx;
        }
    }
    void generate() {
        write_attributes(Writer(gen_dir + "/attributes.cfg"));
        write_indexschema(Writer(gen_dir + "/indexschema.cfg"));
        write_vsmfields(Writer(gen_dir + "/vsmfields.cfg"));
        write_rank_profiles(Writer(gen_dir + "/rank-profiles.cfg"));
        write_ranking_constants(Writer(gen_dir + "/ranking-constants.cfg"));
        write_ranking_expressions(Writer(gen_dir + "/ranking-expressions.cfg"));
        write_onnx_models(Writer(gen_dir + "/onnx-models.cfg"));
        write_self_cfg(Writer(gen_dir + "/verify-ranksetup.cfg"));
    }
    bool verify(SearchMode mode = SearchMode::BOTH) {
        if (mode == SearchMode::BOTH) {
            bool res_indexed = verify_mode(SearchMode::INDEXED);
            bool res_streaming = verify_mode(SearchMode::STREAMING);
            EXPECT_EQ(res_indexed, res_streaming);
            return res_indexed;
        } else {
            return verify_mode(mode);
        }
    }
    bool verify_mode(SearchMode mode) {
        generate();
        vespalib::Process process(fmt("%s dir:%s%s", prog, gen_dir.c_str(),
                                      (mode == SearchMode::STREAMING ? " -S" : "")),
                                  true);
        for (auto line = process.read_line(); !line.empty(); line = process.read_line()) {
            fprintf(stderr, "> %s\n", line.c_str());
        }
        return (process.join() == 0);
    }
    void verify_valid(std::initializer_list<std::string> features, SearchMode mode = SearchMode::BOTH) {
        for (const std::string &f : features) {
            first_phase(f);
            EXPECT_TRUE(verify(mode)) << "--> feature '" << f << "' was invalid (should be valid)";
        }
    }
    void verify_invalid(std::initializer_list<std::string> features, SearchMode mode = SearchMode::BOTH) {
        for (const std::string &f: features) {
            first_phase(f);
            EXPECT_TRUE(!verify(mode)) << "--> feature '" << f << "' was valid (should be invalid)";
        }
    }
};

CommonSetup::CommonSetup()
    : indexes(),
      attributes(),
      properties(),
      extra_profiles()
{
}

CommonSetup::~CommonSetup() = default;

//-----------------------------------------------------------------------------

struct EmptySetup : CommonSetup {};

struct SimpleSetup : CommonSetup {
    SimpleSetup() : CommonSetup() {
        index("title", DataType::STRING, CollectionType::SINGLE);
        index("list", DataType::STRING, CollectionType::ARRAY);
        index("keywords", DataType::STRING, CollectionType::WEIGHTEDSET);
        attribute("date", DataType::INT32, CollectionType::SINGLE);
        attribute("pos_zcurve", DataType::INT64, CollectionType::SINGLE);
        attribute("imported_attr", DataType::INT32, CollectionType::SINGLE, true);
        constants["my_tensor"] = "tensor(x{},y{})";
    }
};

struct OnnxSetup : CommonSetup {
    OnnxSetup() : CommonSetup() {
        add_onnx_model(OnnxModel("simple", TEST_PATH("../../../../../eval/src/tests/tensor/onnx_wrapper/simple.onnx")));
        add_onnx_model(std::move(OnnxModel("mapped", TEST_PATH("../../../../../eval/src/tests/tensor/onnx_wrapper/simple.onnx"))
                       .input_feature("query_tensor", "rankingExpression(qt)")
                       .input_feature("attribute_tensor", "rankingExpression(at)")
                       .input_feature("bias_tensor", "rankingExpression(bt)")
                       .output_name("output", "result")));
        add_onnx_model(std::move(OnnxModel("fragile", TEST_PATH("../../../../../searchlib/src/tests/features/onnx_feature/fragile.onnx"))
                       .dry_run_on_setup(true)));
        add_onnx_model(std::move(OnnxModel("unfragile", TEST_PATH("../../../../../searchlib/src/tests/features/onnx_feature/fragile.onnx"))
                       .dry_run_on_setup(false)));
    }
};

struct ShadowSetup : CommonSetup {
    ShadowSetup() : CommonSetup() {
        index("both", DataType::STRING, CollectionType::SINGLE);
        attribute("both", DataType::STRING, CollectionType::SINGLE);
    }
};

struct VerifyRankSetupTest : public ::testing::Test {
    VerifyRankSetupTest();
    ~VerifyRankSetupTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

VerifyRankSetupTest::VerifyRankSetupTest()
    : ::testing::Test()
{
}

VerifyRankSetupTest::~VerifyRankSetupTest() = default;

void
VerifyRankSetupTest::SetUpTestSuite()
{
    verify_dir();
    std::filesystem::remove_all(gen_dir);
    std::filesystem::create_directory(gen_dir);
}

void
VerifyRankSetupTest::TearDownTestSuite()
{
    std::filesystem::remove_all(gen_dir);
}

TEST_F(VerifyRankSetupTest, print_usage) {
    EXPECT_TRUE(!vespalib::Process::run(fmt("%s", prog)));
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_empty_setup_passes_validation) {
    EmptySetup f;
    EXPECT_TRUE(f.verify());
}

TEST_F(VerifyRankSetupTest, require_that_we_can_verify_multiple_rank_profiles) {
    SimpleSetup f;
    f.first_phase(valid_feature);
    f.good_profile();
    EXPECT_TRUE(f.verify());
    f.bad_profile();
    EXPECT_TRUE(!f.verify());
}

TEST_F(VerifyRankSetupTest, require_that_first_phase_can_break_validation) {
    SimpleSetup f;
    f.first_phase(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F(VerifyRankSetupTest, require_that_second_phase_can_break_validation) {
    SimpleSetup f;
    f.second_phase(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F(VerifyRankSetupTest, require_that_match_features_can_break_validation) {
    SimpleSetup f;
    f.match_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F(VerifyRankSetupTest, require_that_summary_features_can_break_validation) {
    SimpleSetup f;
    f.summary_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F(VerifyRankSetupTest, require_that_dump_features_can_break_validation) {
    SimpleSetup f;
    f.dump_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_fieldMatch_feature_requires_single_value_field) {
    SimpleSetup f;
    f.verify_invalid({"fieldMatch(keywords)", "fieldMatch(list)"}, SearchMode::INDEXED);
    f.verify_valid({"fieldMatch(title)"});
}

TEST_F(VerifyRankSetupTest, require_that_age_feature_requires_attribute_parameter) {
    SimpleSetup f;
    f.verify_invalid({"age(unknown)", "age(title)"}, SearchMode::INDEXED);
    f.verify_valid({"age(date)"});
}

TEST_F(VerifyRankSetupTest, require_that_nativeRank_can_be_used_on_any_valid_field) {
    SimpleSetup f;
    f.verify_invalid({"nativeRank(unknown)"});
    f.verify_valid({"nativeRank", "nativeRank(title)", "nativeRank(date)", "nativeRank(title,date)"});
}

TEST_F(VerifyRankSetupTest, require_that_nativeAttributeMatch_requires_attribute_parameter) {
    SimpleSetup f;
    f.verify_invalid({"nativeAttributeMatch(unknown)", "nativeAttributeMatch(title)", "nativeAttributeMatch(title,date)"}, SearchMode::INDEXED);
    f.verify_valid({"nativeAttributeMatch", "nativeAttributeMatch(date)"});
}

TEST_F(VerifyRankSetupTest, require_that_shadowed_attributes_can_be_used) {
    ShadowSetup f;
    f.verify_valid({"attribute(both)"});
}

TEST_F(VerifyRankSetupTest, require_that_ranking_constants_can_be_used) {
    SimpleSetup f;
    f.verify_valid({"constant(my_tensor)"});
}

TEST_F(VerifyRankSetupTest, require_that_undefined_ranking_constants_cannot_be_used) {
    SimpleSetup f;
    f.verify_invalid({"constant(bogus_tensor)"});
}

TEST_F(VerifyRankSetupTest, require_that_ranking_expressions_can_be_verified) {
    SimpleSetup f;
    f.rank_expr("my_expr", "constant(my_tensor)+attribute(date)");
    f.verify_valid({"rankingExpression(my_expr)"});
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_tensor_join_is_supported) {
    SimpleSetup f;
    f.rank_expr("my_expr", "join(constant(my_tensor),attribute(date),f(t,d)(t+d))");
    f.verify_valid({"rankingExpression(my_expr)"});
}

TEST_F(VerifyRankSetupTest, require_that_nested_tensor_join_is_not_supported) {
    SimpleSetup f;
    f.rank_expr("my_expr", "join(constant(my_tensor),attribute(date),f(t,d)(join(t,d,f(x,y)(x+y))))");
    f.verify_invalid({"rankingExpression(my_expr)"});
}

TEST_F(VerifyRankSetupTest, require_that_imported_attribute_field_can_be_used_by_rank_feature) {
    SimpleSetup f;
    f.verify_valid({"attribute(imported_attr)"});
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_external_ranking_expression_can_be_verified) {
    SimpleSetup f;
    f.ext_rank_expr("my_expr", "good_ranking_expression");
    f.verify_valid({"rankingExpression(my_expr)"});
}

TEST_F(VerifyRankSetupTest, require_that_external_ranking_expression_can_fail_verification) {
    SimpleSetup f;
    f.ext_rank_expr("my_expr", "bad_ranking_expression");
    f.verify_invalid({"rankingExpression(my_expr)"});
}

TEST_F(VerifyRankSetupTest, require_that_missing_expression_file_fails_verification) {
    SimpleSetup f;
    f.ext_rank_expr("my_expr", "missing_ranking_expression_file");
    f.verify_invalid({"rankingExpression(my_expr)"});
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_onnx_model_can_be_verified) {
    OnnxSetup f;
    f.rank_expr("query_tensor", "tensor<float>(a[1],b[4]):[[1,2,3,4]]");
    f.rank_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_valid({"onnx(simple)"});
}

TEST_F(VerifyRankSetupTest, require_that_onnx_model_can_be_verified_with_old_name) {
    OnnxSetup f;
    f.rank_expr("query_tensor", "tensor<float>(a[1],b[4]):[[1,2,3,4]]");
    f.rank_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_valid({"onnxModel(simple)"});
}

TEST_F(VerifyRankSetupTest, require_that_input_type_mismatch_makes_onnx_model_fail_verification) {
    OnnxSetup f;
    f.rank_expr("query_tensor", "tensor<float>(a[1],b[3]):[[1,2,3]]"); // <- 3 vs 4
    f.rank_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_invalid({"onnx(simple)"});
}

TEST_F(VerifyRankSetupTest, require_that_onnx_model_can_have_inputs_and_outputs_mapped) {
    OnnxSetup f;
    f.rank_expr("qt", "tensor<float>(a[1],b[4]):[[1,2,3,4]]");
    f.rank_expr("at", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bt", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_valid({"onnx(mapped).result"});
}

TEST_F(VerifyRankSetupTest, require_that_fragile_model_can_pass_verification) {
    OnnxSetup f;
    f.rank_expr("in1", "tensor<float>(a[2]):[1,2]");
    f.rank_expr("in2", "tensor<float>(a[2]):[3,4]");
    f.verify_valid({"onnx(fragile)"});
}

TEST_F(VerifyRankSetupTest, require_that_broken_fragile_model_fails_verification) {
    OnnxSetup f;
    f.rank_expr("in1", "tensor<float>(a[2]):[1,2]");
    f.rank_expr("in2", "tensor<float>(a[3]):[3,4,31515]");
    f.verify_invalid({"onnx(fragile)"});
}

TEST_F(VerifyRankSetupTest, require_that_broken_fragile_model_without_dry_run_passes_verification) {
    OnnxSetup f;
    f.rank_expr("in1", "tensor<float>(a[2]):[1,2]");
    f.rank_expr("in2", "tensor<float>(a[3]):[3,4,31515]");
    f.verify_valid({"onnx(unfragile)"});
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_query_tensor_can_have_default_value) {
    SimpleSetup f;
    f.query_feature_type("foo", "tensor(x[3])");
    f.query_feature_default_value("foo", "tensor(x[3])(x+1)");
    f.verify_valid({"query(foo)"});
}

TEST_F(VerifyRankSetupTest, require_that_query_tensor_default_value_must_have_appropriate_type) {
    SimpleSetup f;
    f.query_feature_type("foo", "tensor(y[3])");
    f.query_feature_default_value("foo", "tensor(x[3])(x+1)");
    f.verify_invalid({"query(foo)"});
}

TEST_F(VerifyRankSetupTest, require_that_query_tensor_default_value_must_be_a_valid_expression) {
    SimpleSetup f;
    f.query_feature_type("foo", "tensor(x[3])");
    f.query_feature_default_value("foo", "this expression is not parseable");
    f.verify_invalid({"query(foo)"});
}

TEST_F(VerifyRankSetupTest, require_that_query_tensor_default_value_expression_does_not_need_parameters) {
    SimpleSetup f;
    f.query_feature_type("foo", "tensor(x[3])");
    f.query_feature_default_value("foo", "externalSymbol");
    f.verify_invalid({"query(foo)"});
}

//-----------------------------------------------------------------------------

TEST_F(VerifyRankSetupTest, require_that_zcurve_distance_can_be_set_up) {
    SimpleSetup f;
    f.verify_valid({"distance(pos)"});
}

TEST_F(VerifyRankSetupTest, require_that_zcurve_distance_must_be_backed_by_an_attribute) {
    SimpleSetup f;
    f.verify_invalid({"distance(unknown)"});
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
