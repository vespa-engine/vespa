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
class StringList : public std::vector<std::string> {
public:
    StringList &add(std::string_view str) { emplace_back(str); return *this; }
    StringList &clear()  { std::vector<std::string>::clear(); return *this; }
};

//---------------------------------------------------------------------------------------------------------------------
// StringMap
//---------------------------------------------------------------------------------------------------------------------
class StringMap : public std::map<std::string, std::string> {
public:
    StringMap &add(const std::string &key, const std::string &val) {
        iterator it = insert(std::make_pair(key, val)).first;
        it->second = val;
        return *this;
    }
    StringMap &clear() {
        std::map<std::string, std::string>::clear();
        return *this;
    }
};

//---------------------------------------------------------------------------------------------------------------------
// StringSet
//---------------------------------------------------------------------------------------------------------------------
class StringSet : public std::set<std::string> {
public:
    StringSet & add(const std::string & str) { insert(str); return *this; }
    StringSet & clear() { std::set<std::string>::clear(); return *this; }
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
    std::vector<std::string> _features;

public:
    FtDumpFeatureVisitor();
    void visitDumpFeature(const std::string & name) override { _features.push_back(name); }
    const std::vector<std::string> & features() const { return _features; }
};

//---------------------------------------------------------------------------------------------------------------------
// FtTestRunner
//---------------------------------------------------------------------------------------------------------------------
class FtFeatureTest {
public:
    FtFeatureTest(search::fef::BlueprintFactory &factory, const std::string &feature);
    FtFeatureTest(search::fef::BlueprintFactory &factory, const std::vector<std::string> &features);
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
    FtQueryTerm(const std::string t, uint32_t tw = 100, feature_t co = 0.1f, feature_t si = 0.1f) :
        term(t), termWeight(tw), connexity(co), significance(si) {}
    FtQueryTerm() noexcept : term(), termWeight(100), connexity(0.1f), significance(0.1f) {}
    std::string term;
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
using StringVectorMap = std::map<std::string, std::vector<std::string> >;

//---------------------------------------------------------------------------------------------------------------------
// FtUtil
//---------------------------------------------------------------------------------------------------------------------
class FtUtil {
public:
    static std::vector<std::string> tokenize(const std::string & str, const std::string & separator = " ");
    static FtQuery toQuery(const std::string & query, const std::string & separator = " ");
    static search::fef::test::RankResult toRankResult(const std::string & baseName,
                                                      const std::string & result,
                                                      const std::string & separator = " ");
};

//---------------------------------------------------------------------------------------------------------------------
// FtIndex
//---------------------------------------------------------------------------------------------------------------------
struct FtIndex {
    struct Element {
        using Tokens = std::vector<std::string>;
        int32_t weight;
        Tokens  tokens;
        Element(int32_t w, const Tokens &t)
            : weight(w), tokens(t) {}
    };
    using Field = std::vector<Element>;
    using FieldMap = std::map<std::string, Field>;
    FieldMap index; // raw content of all fields
    std::string cursor; // last referenced field
    FtIndex() : index(), cursor() {}
    ~FtIndex();
    FtIndex &field(const std::string &name) {
        cursor = name;
        index[name];
        return *this;
    }
    FtIndex &element(const std::string &content, int32_t weight = 1) {
        assert(!cursor.empty());
        index[cursor].push_back(Element(weight, FtUtil::tokenize(content, " ")));
        return *this;
    }
};
