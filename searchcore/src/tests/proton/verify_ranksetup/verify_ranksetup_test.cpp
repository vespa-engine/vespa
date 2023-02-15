// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/onnx_model.h>
#include <string>
#include <vector>
#include <map>
#include <initializer_list>

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

struct Writer {
    FILE *file;
    explicit Writer(const std::string &file_name) {
        file = fopen(file_name.c_str(), "w");
        ASSERT_TRUE(file != nullptr);
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
    ASSERT_NOT_EQUAL(pwd.find("searchcore/src/tests/proton/verify_ranksetup"), pwd.npos);
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

struct Setup {
    std::map<std::string,std::pair<std::string,std::string> > indexes;
    std::map<std::string,Attribute>                           attributes;
    std::map<std::string,std::string>                         properties;
    std::map<std::string,std::string>                         constants;
    std::vector<bool>                                         extra_profiles;
    std::map<std::string,std::string>                         ranking_expressions;
    std::map<std::string,OnnxModel>                           onnx_models;
    Setup();
    ~Setup();
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
        write_rank_profiles(Writer(gen_dir + "/rank-profiles.cfg"));
        write_ranking_constants(Writer(gen_dir + "/ranking-constants.cfg"));
        write_ranking_expressions(Writer(gen_dir + "/ranking-expressions.cfg"));
        write_onnx_models(Writer(gen_dir + "/onnx-models.cfg"));
        write_self_cfg(Writer(gen_dir + "/verify-ranksetup.cfg"));
    }
    bool verify() {
        generate();
        vespalib::Process process(fmt("%s dir:%s", prog, gen_dir.c_str()), true);
        for (auto line = process.read_line(); !line.empty(); line = process.read_line()) {
            fprintf(stderr, "> %s\n", line.c_str());
        }
        return (process.join() == 0);
    }
    void verify_valid(std::initializer_list<std::string> features) {
        for (const std::string &f: features) {
            first_phase(f);
            if (!EXPECT_TRUE(verify())) {
                fprintf(stderr, "--> feature '%s' was invalid (should be valid)\n", f.c_str());
            }
        }
    }
    void verify_invalid(std::initializer_list<std::string> features) {
        for (const std::string &f: features) {
            first_phase(f);
            if (!EXPECT_TRUE(!verify())) {
                fprintf(stderr, "--> feature '%s' was valid (should be invalid)\n", f.c_str());
            }
        }
    }
};

Setup::Setup()
    : indexes(),
      attributes(),
      properties(),
      extra_profiles()
{
    verify_dir();
}
Setup::~Setup() = default;

//-----------------------------------------------------------------------------

struct EmptySetup : Setup {};

struct SimpleSetup : Setup {
    SimpleSetup() : Setup() {
        index("title", DataType::STRING, CollectionType::SINGLE);
        index("list", DataType::STRING, CollectionType::ARRAY);
        index("keywords", DataType::STRING, CollectionType::WEIGHTEDSET);
        attribute("date", DataType::INT32, CollectionType::SINGLE);
        attribute("pos_zcurve", DataType::INT64, CollectionType::SINGLE);
        attribute("imported_attr", DataType::INT32, CollectionType::SINGLE, true);
        constants["my_tensor"] = "tensor(x{},y{})";
    }
};

struct OnnxSetup : Setup {
    OnnxSetup() : Setup() {
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

struct ShadowSetup : Setup {
    ShadowSetup() : Setup() {
        index("both", DataType::STRING, CollectionType::SINGLE);
        attribute("both", DataType::STRING, CollectionType::SINGLE);
    }
};

TEST_F("print usage", Setup()) {
    EXPECT_TRUE(!vespalib::Process::run(fmt("%s", prog)));
}

TEST_F("setup output directory", Setup()) {
    ASSERT_TRUE(vespalib::Process::run(fmt("rm -rf %s", gen_dir.c_str())));
    ASSERT_TRUE(vespalib::Process::run(fmt("mkdir %s", gen_dir.c_str())));
}

//-----------------------------------------------------------------------------

TEST_F("require that empty setup passes validation", EmptySetup()) {
    EXPECT_TRUE(f.verify());
}

TEST_F("require that we can verify multiple rank profiles", SimpleSetup()) {
    f.first_phase(valid_feature);
    f.good_profile();
    EXPECT_TRUE(f.verify());
    f.bad_profile();
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that first phase can break validation", SimpleSetup()) {
    f.first_phase(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that second phase can break validation", SimpleSetup()) {
    f.second_phase(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that match features can break validation", SimpleSetup()) {
    f.match_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that summary features can break validation", SimpleSetup()) {
    f.summary_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that dump features can break validation", SimpleSetup()) {
    f.dump_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

//-----------------------------------------------------------------------------

TEST_F("require that fieldMatch feature requires single value field", SimpleSetup()) {
    f.verify_invalid({"fieldMatch(keywords)", "fieldMatch(list)"});
    f.verify_valid({"fieldMatch(title)"});
}

TEST_F("require that age feature requires attribute parameter", SimpleSetup()) {
    f.verify_invalid({"age(unknown)", "age(title)"});
    f.verify_valid({"age(date)"});
}

TEST_F("require that nativeRank can be used on any valid field", SimpleSetup()) {
    f.verify_invalid({"nativeRank(unknown)"});
    f.verify_valid({"nativeRank", "nativeRank(title)", "nativeRank(date)", "nativeRank(title,date)"});
}

TEST_F("require that nativeAttributeMatch requires attribute parameter", SimpleSetup()) {
    f.verify_invalid({"nativeAttributeMatch(unknown)", "nativeAttributeMatch(title)", "nativeAttributeMatch(title,date)"});
    f.verify_valid({"nativeAttributeMatch", "nativeAttributeMatch(date)"});
}

TEST_F("require that shadowed attributes can be used", ShadowSetup()) {
    f.verify_valid({"attribute(both)"});
}

TEST_F("require that ranking constants can be used", SimpleSetup()) {
    f.verify_valid({"constant(my_tensor)"});
}

TEST_F("require that undefined ranking constants cannot be used", SimpleSetup()) {
    f.verify_invalid({"constant(bogus_tensor)"});
}

TEST_F("require that ranking expressions can be verified", SimpleSetup()) {
    f.rank_expr("my_expr", "constant(my_tensor)+attribute(date)");
    f.verify_valid({"rankingExpression(my_expr)"});
}

//-----------------------------------------------------------------------------

TEST_F("require that tensor join is supported", SimpleSetup()) {
    f.rank_expr("my_expr", "join(constant(my_tensor),attribute(date),f(t,d)(t+d))");
    f.verify_valid({"rankingExpression(my_expr)"});
}

TEST_F("require that nested tensor join is not supported", SimpleSetup()) {
    f.rank_expr("my_expr", "join(constant(my_tensor),attribute(date),f(t,d)(join(t,d,f(x,y)(x+y))))");
    f.verify_invalid({"rankingExpression(my_expr)"});
}

TEST_F("require that imported attribute field can be used by rank feature", SimpleSetup()) {
    f.verify_valid({"attribute(imported_attr)"});
}

//-----------------------------------------------------------------------------

TEST_F("require that external ranking expression can be verified", SimpleSetup()) {
    f.ext_rank_expr("my_expr", "good_ranking_expression");
    f.verify_valid({"rankingExpression(my_expr)"});
}

TEST_F("require that external ranking expression can fail verification", SimpleSetup()) {
    f.ext_rank_expr("my_expr", "bad_ranking_expression");
    f.verify_invalid({"rankingExpression(my_expr)"});
}

TEST_F("require that missing expression file fails verification", SimpleSetup()) {
    f.ext_rank_expr("my_expr", "missing_ranking_expression_file");
    f.verify_invalid({"rankingExpression(my_expr)"});
}

//-----------------------------------------------------------------------------

TEST_F("require that onnx model can be verified", OnnxSetup()) {
    f.rank_expr("query_tensor", "tensor<float>(a[1],b[4]):[[1,2,3,4]]");
    f.rank_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_valid({"onnx(simple)"});
}

TEST_F("require that onnx model can be verified with old name", OnnxSetup()) {
    f.rank_expr("query_tensor", "tensor<float>(a[1],b[4]):[[1,2,3,4]]");
    f.rank_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_valid({"onnxModel(simple)"});
}

TEST_F("require that input type mismatch makes onnx model fail verification", OnnxSetup()) {
    f.rank_expr("query_tensor", "tensor<float>(a[1],b[3]):[[1,2,3]]"); // <- 3 vs 4
    f.rank_expr("attribute_tensor", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bias_tensor", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_invalid({"onnx(simple)"});
}

TEST_F("require that onnx model can have inputs and outputs mapped", OnnxSetup()) {
    f.rank_expr("qt", "tensor<float>(a[1],b[4]):[[1,2,3,4]]");
    f.rank_expr("at", "tensor<float>(a[4],b[1]):[[5],[6],[7],[8]]");
    f.rank_expr("bt", "tensor<float>(a[1],b[1]):[[9]]");
    f.verify_valid({"onnx(mapped).result"});
}

TEST_F("require that fragile model can pass verification", OnnxSetup()) {
    f.rank_expr("in1", "tensor<float>(a[2]):[1,2]");
    f.rank_expr("in2", "tensor<float>(a[2]):[3,4]");
    f.verify_valid({"onnx(fragile)"});
}

TEST_F("require that broken fragile model fails verification", OnnxSetup()) {
    f.rank_expr("in1", "tensor<float>(a[2]):[1,2]");
    f.rank_expr("in2", "tensor<float>(a[3]):[3,4,31515]");
    f.verify_invalid({"onnx(fragile)"});
}

TEST_F("require that broken fragile model without dry-run passes verification", OnnxSetup()) {
    f.rank_expr("in1", "tensor<float>(a[2]):[1,2]");
    f.rank_expr("in2", "tensor<float>(a[3]):[3,4,31515]");
    f.verify_valid({"onnx(unfragile)"});
}

//-----------------------------------------------------------------------------

TEST_F("require that query tensor can have default value", SimpleSetup()) {
    f.query_feature_type("foo", "tensor(x[3])");
    f.query_feature_default_value("foo", "tensor(x[3])(x+1)");
    f.verify_valid({"query(foo)"});
}

TEST_F("require that query tensor default value must have appropriate type", SimpleSetup()) {
    f.query_feature_type("foo", "tensor(y[3])");
    f.query_feature_default_value("foo", "tensor(x[3])(x+1)");
    f.verify_invalid({"query(foo)"});
}

TEST_F("require that query tensor default value must be a valid expression", SimpleSetup()) {
    f.query_feature_type("foo", "tensor(x[3])");
    f.query_feature_default_value("foo", "this expression is not parseable");
    f.verify_invalid({"query(foo)"});
}

TEST_F("require that query tensor default value expression does not need parameters", SimpleSetup()) {
    f.query_feature_type("foo", "tensor(x[3])");
    f.query_feature_default_value("foo", "externalSymbol");
    f.verify_invalid({"query(foo)"});
}

//-----------------------------------------------------------------------------

TEST_F("require that zcurve distance can be set up", SimpleSetup()) {
    f.verify_valid({"distance(pos)"});
}

TEST_F("require that zcurve distance must be backed by an attribute", SimpleSetup()) {
    f.verify_invalid({"distance(unknown)"});
}

//-----------------------------------------------------------------------------

TEST_F("cleanup files", Setup()) {
    ASSERT_TRUE(vespalib::Process::run(fmt("rm -rf %s", gen_dir.c_str())));
}

TEST_MAIN() { TEST_RUN_ALL(); }
