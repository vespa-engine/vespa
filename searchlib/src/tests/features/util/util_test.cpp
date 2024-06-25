// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using namespace search::features::util;

namespace search::features::util {

void PrintTo(const DocumentFrequency& document_frequency, std::ostream* os) {
    *os << "{" << document_frequency.frequency << "," << document_frequency.count << "}";
}

}

SimpleTermData make_term(uint32_t uid) {
    SimpleTermData term;
    term.setUniqueId(uid);
    return term;
}

struct TermLabelFixture {
    IndexEnvironment indexEnv;
    QueryEnvironment queryEnv;
    TermLabelFixture() : indexEnv(), queryEnv(&indexEnv) {
        queryEnv.getTerms().push_back(make_term(5));
        queryEnv.getTerms().push_back(make_term(0));
        queryEnv.getTerms().push_back(make_term(10));
        queryEnv.getProperties().add("vespa.label.foo.id", "5");
        queryEnv.getProperties().add("vespa.label.bar.id", "0"); // undefined uid
        queryEnv.getProperties().add("vespa.label.baz.id", "10");
        queryEnv.getProperties().add("vespa.label.fox.id", "7"); // non-existing
    }
};

TEST(UtilsTest, require_that_label_can_be_mapped_to_term)
{
    TermLabelFixture f1;
    EXPECT_EQ((ITermData*)&f1.queryEnv.getTerms()[0], getTermByLabel(f1.queryEnv, "foo"));
    EXPECT_EQ((ITermData*)0, getTermByLabel(f1.queryEnv, "bar"));
    EXPECT_EQ((ITermData*)&f1.queryEnv.getTerms()[2], getTermByLabel(f1.queryEnv, "baz"));
    EXPECT_EQ((ITermData*)0, getTermByLabel(f1.queryEnv, "fox"));
    EXPECT_EQ((ITermData*)0, getTermByLabel(f1.queryEnv, "unknown"));
}

template <typename T>
void verifyStrToNum(const std::string& label) {
    SCOPED_TRACE(label);
    EXPECT_EQ(-17, static_cast<long>(strToNum<T>("-17")));
    EXPECT_EQ(-1, static_cast<long>(strToNum<T>("-1")));
    EXPECT_EQ(0, static_cast<long>(strToNum<T>("0")));
    EXPECT_EQ(1, static_cast<long>(strToNum<T>("1")));
    EXPECT_EQ(17, static_cast<long>(strToNum<T>("17")));
    EXPECT_EQ(0, static_cast<long>(strToNum<T>("0x0")));
    EXPECT_EQ(1, static_cast<long>(strToNum<T>("0x1")));
    EXPECT_EQ(27, static_cast<long>(strToNum<T>("0x1b")));
}

TEST(UtilsTest, verify_str2Num)
{
    verifyStrToNum<int8_t>("int8_t");
    verifyStrToNum<int16_t>("int16_t");
    verifyStrToNum<int32_t>("int32_t");
    verifyStrToNum<int64_t>("int64_t");
}

TEST(UtilsTest, lookup_document_frequency)
{
    using OptDF = std::optional<DocumentFrequency>;
    IndexEnvironment index_env;;
    QueryEnvironment query_env(&index_env);
    query_env.getTerms() = std::vector<SimpleTermData>{make_term(0), make_term(5), make_term(6), make_term(10)};
    // Properties not used due to bad unique id
    query_env.getProperties().add("vespa.term.0.docfreq", "11");
    query_env.getProperties().add("vespa.term.0.docfreq", "17");
    // Incomplete properties, thus not used
    query_env.getProperties().add("vespa.term.6.docfreq", "5");
    // Complete properties
    query_env.getProperties().add("vespa.term.10.docfreq", "10");
    query_env.getProperties().add("vespa.term.10.docfreq", "15");
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, 0)); // bad unique id
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, 1)); // missing properties
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, 2)); // incomplete properties
    EXPECT_EQ(OptDF({10, 15}), lookup_document_frequency(query_env, 3));
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, 4)); // term not found
}

GTEST_MAIN_RUN_ALL_TESTS()
