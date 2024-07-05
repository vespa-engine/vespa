// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "featuretest.h"
#include "indexenvironment.h"
#include "indexenvironmentbuilder.h"
#include "matchdatabuilder.h"
#include "queryenvironment.h"
#include "queryenvironmentbuilder.h"
#include "rankresult.h"
#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/fef/fef.h>

using search::feature_t;

//---------------------------------------------------------------------------------------------------------------------
// StringList
//---------------------------------------------------------------------------------------------------------------------
class StringList : public std::vector<vespalib::string> {
public:
    StringList &add(std::string_view str) { push_back(str); return *this; }
    StringList &clear()  { std::vector<vespalib::string>::clear(); return *this; }
};

//---------------------------------------------------------------------------------------------------------------------
// StringMap
//---------------------------------------------------------------------------------------------------------------------
class StringMap : public std::map<vespalib::string, vespalib::string> {
public:
    StringMap &add(const vespalib::string &key, const vespalib::string &val) {
        iterator it = insert(std::make_pair(key, val)).first;
        it->second = val;
        return *this;
    }
    StringMap &clear() {
        std::map<vespalib::string, vespalib::string>::clear();
        return *this;
    }
};

//---------------------------------------------------------------------------------------------------------------------
// StringSet
//---------------------------------------------------------------------------------------------------------------------
class StringSet : public std::set<vespalib::string> {
public:
    StringSet & add(const vespalib::string & str) { insert(str); return *this; }
    StringSet & clear() { std::set<vespalib::string>::clear(); return *this; }
};


//---------------------------------------------------------------------------------------------------------------------
// FtIndexEnvironment
//---------------------------------------------------------------------------------------------------------------------
class FtIndexEnvironment : public search::fef::test::IndexEnvironment {
public:
    FtIndexEnvironment();

    search::fef::test::IndexEnvironmentBuilder &getBuilder() { return _builder; }

private:
    search::fef::test::IndexEnvironmentBuilder _builder;
};

//---------------------------------------------------------------------------------------------------------------------
// FtQueryEnvironment
//---------------------------------------------------------------------------------------------------------------------
class FtQueryEnvironment : public search::fef::test::QueryEnvironment {
public:
    FtQueryEnvironment(search::fef::test::IndexEnvironment &indexEnv);
    ~FtQueryEnvironment();

    search::fef::test::QueryEnvironmentBuilder &getBuilder() { return _builder; }
    search::fef::MatchDataLayout               &getLayout()  { return _layout; }

private:
    search::fef::MatchDataLayout               _layout;
    search::fef::test::QueryEnvironmentBuilder _builder;
};

//---------------------------------------------------------------------------------------------------------------------
// FtDumpFeatureVisitor
//---------------------------------------------------------------------------------------------------------------------
class FtDumpFeatureVisitor : public search::fef::IDumpFeatureVisitor
{
private:
    std::vector<vespalib::string> _features;

public:
    FtDumpFeatureVisitor();
    void visitDumpFeature(const vespalib::string & name) override { _features.push_back(name); }
    const std::vector<vespalib::string> & features() const { return _features; }
};

//---------------------------------------------------------------------------------------------------------------------
// FtTestRunner
//---------------------------------------------------------------------------------------------------------------------
class FtFeatureTest {
public:
    FtFeatureTest(search::fef::BlueprintFactory &factory, const vespalib::string &feature);
    FtFeatureTest(search::fef::BlueprintFactory &factory, const std::vector<vespalib::string> &features);
    ~FtFeatureTest();

    bool setup()                                                                    { return _test.setup(); }
    bool execute(feature_t expected, double epsilon = 0, uint32_t docId = 1)        { return _test.execute(expected, epsilon, docId); }
    bool execute(const search::fef::test::RankResult &expected, uint32_t docId = 1) { return _test.execute(expected, docId); }
    bool executeOnly(search::fef::test::RankResult &result, uint32_t docId = 1)     { return _test.executeOnly(result, docId); }
    search::fef::test::MatchDataBuilder::UP createMatchDataBuilder()                { return _test.createMatchDataBuilder(); }
    vespalib::eval::Value::CREF resolveObjectFeature(uint32_t docid = 1) { return _test.resolveObjectFeature(docid); }

    FtIndexEnvironment &getIndexEnv() { return _indexEnv; }
    FtQueryEnvironment &getQueryEnv() { return _queryEnv; }
    search::fef::Properties &getOverrides() { return _overrides; }

private:
    FtIndexEnvironment                   _indexEnv;
    FtQueryEnvironment                   _queryEnv;
    search::fef::Properties              _overrides;
    search::fef::test::FeatureTest       _test;
};

//---------------------------------------------------------------------------------------------------------------------
// FtQueryTerm
//---------------------------------------------------------------------------------------------------------------------
struct FtQueryTerm {
    FtQueryTerm(const vespalib::string t, uint32_t tw = 100, feature_t co = 0.1f, feature_t si = 0.1f) :
        term(t), termWeight(tw), connexity(co), significance(si) {}
    FtQueryTerm() noexcept : term(), termWeight(100), connexity(0.1f), significance(0.1f) {}
    vespalib::string term;
    search::query::Weight termWeight;
    feature_t connexity;
    feature_t significance;
    bool operator<(const FtQueryTerm & rhs) const {
        return term < rhs.term;
    }
    bool operator==(const FtQueryTerm & rhs) const {
        return term == rhs.term;
    }
};

using FtQuery = std::vector<FtQueryTerm>;
using StringVectorMap = std::map<vespalib::string, std::vector<vespalib::string> >;

//---------------------------------------------------------------------------------------------------------------------
// FtUtil
//---------------------------------------------------------------------------------------------------------------------
class FtUtil {
public:
    static std::vector<vespalib::string> tokenize(const vespalib::string & str, const vespalib::string & separator = " ");
    static FtQuery toQuery(const vespalib::string & query, const vespalib::string & separator = " ");
    static search::fef::test::RankResult toRankResult(const vespalib::string & baseName,
                                                      const vespalib::string & result,
                                                      const vespalib::string & separator = " ");
};

//---------------------------------------------------------------------------------------------------------------------
// FtIndex
//---------------------------------------------------------------------------------------------------------------------
struct FtIndex {
    struct Element {
        using Tokens = std::vector<vespalib::string>;
        int32_t weight;
        Tokens  tokens;
        Element(int32_t w, const Tokens &t)
            : weight(w), tokens(t) {}
    };
    using Field = std::vector<Element>;
    using FieldMap = std::map<vespalib::string, Field>;
    FieldMap index; // raw content of all fields
    vespalib::string cursor; // last referenced field
    FtIndex() : index(), cursor() {}
    ~FtIndex();
    FtIndex &field(const vespalib::string &name) {
        cursor = name;
        index[name];
        return *this;
    }
    FtIndex &element(const vespalib::string &content, int32_t weight = 1) {
        assert(!cursor.empty());
        index[cursor].push_back(Element(weight, FtUtil::tokenize(content, " ")));
        return *this;
    }
};
