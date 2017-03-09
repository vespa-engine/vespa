// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/slaveproc.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <string>
#include <vector>
#include <map>
#include <initializer_list>

const char *prog = "../../../apps/verify_ranksetup/verify_ranksetup-bin";
const std::string gen_dir("generated");

const char *valid_feature = "value(0)";
const char *invalid_feature = "invalid_feature_name and format";

using namespace search::fef::indexproperties;
using namespace search::index;

struct Writer {
    FILE *file;
    Writer(const std::string &file_name) {
        file = fopen(file_name.c_str(), "w");
        ASSERT_TRUE(file != 0);
    }
    void fmt(const char *format, ...) const
#ifdef __GNUC__
        __attribute__ ((format (printf,2,3)))
#endif
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

struct Model {
    std::map<std::string,std::pair<std::string,std::string> > indexes;
    std::map<std::string,std::pair<std::string,std::string> > attributes;
    std::map<std::string,std::string>                         properties;
    std::map<std::string,std::string>                         constants;
    std::vector<bool>                                         extra_profiles;
    std::vector<std::string>                                  imported_attributes;
    Model();
    ~Model();
    void index(const std::string &name, schema::DataType data_type,
               schema::CollectionType collection_type)
    {
        indexes[name].first = schema::getTypeName(data_type);
        indexes[name].second = schema::getTypeName(collection_type);
    }
    void attribute(const std::string &name, schema::DataType data_type,
                   schema::CollectionType collection_type)
    {
        attributes[name].first = schema::getTypeName(data_type);
        attributes[name].second = schema::getTypeName(collection_type);
    }
    void property(const std::string &name, const std::string &val) {
        properties[name] = val;
    }
    void first_phase(const std::string &feature) {
        property(rank::FirstPhase::NAME, feature);
    }
    void second_phase(const std::string &feature) {
        property(rank::SecondPhase::NAME, feature);
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
    void imported_attribute(const std::string &name) {
        imported_attributes.emplace_back(name);
    }
    void write_attributes(const Writer &out) {
        out.fmt("attribute[%zu]\n", attributes.size());
        std::map<std::string,std::pair<std::string,std::string> >::const_iterator pos = attributes.begin();
        for (size_t i = 0; pos != attributes.end(); ++pos, ++i) {
            out.fmt("attribute[%zu].name \"%s\"\n", i, pos->first.c_str());
            out.fmt("attribute[%zu].datatype %s\n", i, pos->second.first.c_str());
            out.fmt("attribute[%zu].collectiontype %s\n", i, pos->second.second.c_str());
        }
    }
    void write_indexschema(const Writer &out) {
        out.fmt("indexfield[%zu]\n", indexes.size());
        std::map<std::string,std::pair<std::string,std::string> >::const_iterator pos = indexes.begin();
        for (size_t i = 0; pos != indexes.end(); ++pos, ++i) {
            out.fmt("indexfield[%zu].name \"%s\"\n", i, pos->first.c_str());
            out.fmt("indexfield[%zu].datatype %s\n", i, pos->second.first.c_str());
            out.fmt("indexfield[%zu].collectiontype %s\n", i, pos->second.second.c_str());
        }
    }
    void write_rank_profiles(const Writer &out) {
        out.fmt("rankprofile[%zu]\n", extra_profiles.size() + 1);
        out.fmt("rankprofile[0].name \"default\"\n");
        std::map<std::string,std::string>::const_iterator pos = properties.begin();
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
    void write_imported_attributes(const Writer &out) {
        size_t idx = 0;
        for (const auto &attr : imported_attributes) {
            out.fmt("attribute[%zu].name \"%s\"\n", idx, attr.c_str());
            out.fmt("attribute[%zu].referencefield \"%s_ref\"\n", idx, attr.c_str());
            out.fmt("attribute[%zu].targetfield \"%s_target\"\n", idx, attr.c_str());
            ++idx;
        }
    }
    void generate() {
        write_attributes(Writer(gen_dir + "/attributes.cfg"));
        write_indexschema(Writer(gen_dir + "/indexschema.cfg"));
        write_rank_profiles(Writer(gen_dir + "/rank-profiles.cfg"));
        write_ranking_constants(Writer(gen_dir + "/ranking-constants.cfg"));
        write_imported_attributes(Writer(gen_dir + "/imported-fields.cfg"));
    }
    bool verify() {
        generate();
        return vespalib::SlaveProc::run(vespalib::make_string("%s dir:%s", prog, gen_dir.c_str()).c_str());
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

Model::Model()
    : indexes(),
      attributes(),
      properties(),
      extra_profiles()
{
    verify_dir();
}
Model::~Model() {}

//-----------------------------------------------------------------------------

struct EmptyModel : Model {};

struct SimpleModel : Model {
    SimpleModel() : Model() {
        index("title", schema::STRING, schema::SINGLE);
        index("list", schema::STRING, schema::ARRAY);
        index("keywords", schema::STRING, schema::WEIGHTEDSET);
        attribute("date", schema::INT32, schema::SINGLE);
        imported_attribute("imported_attr");
        constants["my_tensor"] = "tensor(x{},y{})";
    }
};

struct ShadowModel : Model {
    ShadowModel() : Model() {
        index("both", schema::STRING, schema::SINGLE);
        attribute("both", schema::STRING, schema::SINGLE);
    }
};

TEST_F("print usage", Model()) {
    EXPECT_TRUE(!vespalib::SlaveProc::run(vespalib::make_string("%s", prog).c_str()));
}

TEST_F("setup output directory", Model()) {
    ASSERT_TRUE(vespalib::SlaveProc::run(vespalib::make_string("rm -rf %s", gen_dir.c_str()).c_str()));
    ASSERT_TRUE(vespalib::SlaveProc::run(vespalib::make_string("mkdir %s", gen_dir.c_str()).c_str()));
}

//-----------------------------------------------------------------------------

TEST_F("require that empty setup passes validation", EmptyModel()) {
    EXPECT_TRUE(f.verify());
}

TEST_F("require that we can verify multiple rank profiles", SimpleModel()) {
    f.first_phase(valid_feature);
    f.good_profile();
    EXPECT_TRUE(f.verify());
    f.bad_profile();
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that first phase can break validation", SimpleModel()) {
    f.first_phase(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that second phase can break validation", SimpleModel()) {
    f.second_phase(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that summary features can break validation", SimpleModel()) {
    f.summary_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that dump features can break validation", SimpleModel()) {
    f.dump_feature(invalid_feature);
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that fieldMatch feature requires single value field", SimpleModel()) {
    f.first_phase("fieldMatch(keywords)");
    EXPECT_TRUE(!f.verify());
    f.first_phase("fieldMatch(list)");
    EXPECT_TRUE(!f.verify());
    f.first_phase("fieldMatch(title)");
    EXPECT_TRUE(f.verify());
}

TEST_F("require that age feature requires attribute parameter", SimpleModel()) {
    f.first_phase("age(unknown)");
    EXPECT_TRUE(!f.verify());
    f.first_phase("age(title)");
    EXPECT_TRUE(!f.verify());
    f.first_phase("age(date)");
    EXPECT_TRUE(f.verify());
}

TEST_F("require that nativeRank can be used on any valid field", SimpleModel()) {
    f.verify_invalid({"nativeRank(unknown)"});
    f.verify_valid({"nativeRank", "nativeRank(title)", "nativeRank(date)", "nativeRank(title,date)"});
}

TEST_F("require that nativeAttributeMatch requires attribute parameter", SimpleModel()) {
    f.verify_invalid({"nativeAttributeMatch(unknown)", "nativeAttributeMatch(title)", "nativeAttributeMatch(title,date)"});
    f.verify_valid({"nativeAttributeMatch", "nativeAttributeMatch(date)"});
}

TEST_F("require that shadowed attributes can be used", ShadowModel()) {
    f.first_phase("attribute(both)");
    EXPECT_TRUE(f.verify());
}

TEST_F("require that ranking constants can be used", SimpleModel()) {
    f.first_phase("constant(my_tensor)");
    EXPECT_TRUE(f.verify());
}

TEST_F("require that undefined ranking constants cannot be used", SimpleModel()) {
    f.first_phase("constant(bogus_tensor)");
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that ranking expressions can be verified", SimpleModel()) {
    f.first_phase("rankingExpression(\\\"constant(my_tensor)+attribute(date)\\\")");
    EXPECT_TRUE(f.verify());
}

//-----------------------------------------------------------------------------

TEST_F("require that tensor join is supported", SimpleModel()) {
    f.first_phase("rankingExpression(\\\"join(constant(my_tensor),attribute(date),f(t,d)(t+d))\\\")");
    EXPECT_TRUE(f.verify());
}

TEST_F("require that nested tensor join is not supported", SimpleModel()) {
    f.first_phase("rankingExpression(\\\"join(constant(my_tensor),attribute(date),f(t,d)(join(t,d,f(x,y)(x+y))))\\\")");
    EXPECT_TRUE(!f.verify());
}

TEST_F("require that imported attribute field can be used by rank feature", SimpleModel()) {
    f.first_phase("attribute(imported_attr)");
    EXPECT_TRUE(f.verify());
}

//-----------------------------------------------------------------------------

TEST_F("cleanup files", Model()) {
    ASSERT_TRUE(vespalib::SlaveProc::run(vespalib::make_string("rm -rf %s", gen_dir.c_str()).c_str()));
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
