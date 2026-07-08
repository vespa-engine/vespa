// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/issue.h>

using vespalib::Issue;
using namespace search;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using namespace search::features::util;

namespace search::features::util {

void PrintTo(const DocumentFrequency& document_frequency, std::ostream* os) {
    *os << "{" << document_frequency.frequency << "," << document_frequency.count << "}";
}

} // namespace search::features::util

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
        queryEnv.getProperties().add("vespa.label.multi.id", "5");
        queryEnv.getProperties().add("vespa.label.multi.id", "10");
        queryEnv.getProperties().add("vespa.label.dup.id", "10"); // duplicated uid values
        queryEnv.getProperties().add("vespa.label.dup.id", "10");
        queryEnv.getProperties().add("vespa.label.zeroed.id", "0"); // undefined uid among valid ones
        queryEnv.getProperties().add("vespa.label.zeroed.id", "10");
        queryEnv.getProperties().add("vespa.label.partial.id", "5"); // non-existing uid among valid ones
        queryEnv.getProperties().add("vespa.label.partial.id", "7");
        queryEnv.getProperties().add("vespa.label.dupmissing.id", "5"); // duplicated non-existing uid
        queryEnv.getProperties().add("vespa.label.dupmissing.id", "7");
        queryEnv.getProperties().add("vespa.label.dupmissing.id", "7");
    }
    const ITermData* term(size_t idx) { return &queryEnv.getTerms()[idx]; }
};

struct MyIssues : Issue::Handler {
    std::vector<std::string> list;
    Issue::Binding           capture;
    MyIssues() : list(), capture(Issue::listen(*this)) {}
    ~MyIssues() override;
    void handle(const Issue& issue) override { list.push_back(issue.message()); }
};

MyIssues::~MyIssues() = default;

TEST(UtilsTest, require_that_label_can_be_mapped_to_term) {
    TermLabelFixture f1;
    EXPECT_EQ((ITermData*)&f1.queryEnv.getTerms()[0], getTermByLabel(f1.queryEnv, "foo"));
    EXPECT_EQ(nullptr, getTermByLabel(f1.queryEnv, "bar"));
    EXPECT_EQ((ITermData*)&f1.queryEnv.getTerms()[2], getTermByLabel(f1.queryEnv, "baz"));
    EXPECT_EQ(nullptr, getTermByLabel(f1.queryEnv, "fox"));
    EXPECT_EQ(nullptr, getTermByLabel(f1.queryEnv, "unknown"));
}

TEST(UtilsTest, require_that_multi_valued_label_maps_to_the_first_value_term) {
    TermLabelFixture f1;
    EXPECT_EQ(f1.term(0), getTermByLabel(f1.queryEnv, "multi"));
}

TEST(UtilsTest, require_that_label_can_be_mapped_to_term_set) {
    using TermVector = std::vector<const ITermData*>;
    TermLabelFixture f1;
    MyIssues         issues;
    EXPECT_EQ(TermVector({f1.term(0)}), getTermsByLabel(f1.queryEnv, "foo"));
    EXPECT_EQ(0u, issues.list.size());
    EXPECT_EQ(TermVector({f1.term(2)}), getTermsByLabel(f1.queryEnv, "baz"));
    EXPECT_EQ(0u, issues.list.size());
    // terms are returned in query environment order
    EXPECT_EQ(TermVector({f1.term(0), f1.term(2)}), getTermsByLabel(f1.queryEnv, "multi"));
    EXPECT_EQ(0u, issues.list.size());
    // unknown label -> empty result, no issue
    EXPECT_EQ(TermVector(), getTermsByLabel(f1.queryEnv, "unknown"));
    EXPECT_EQ(0u, issues.list.size());
}

TEST(UtilsTest, require_that_invalid_uid_values_are_skipped_by_term_set_lookup) {
    using TermVector = std::vector<const ITermData*>;
    TermLabelFixture f1;
    MyIssues         issues;
    // uid 0 is reported and skipped
    EXPECT_EQ(TermVector(), getTermsByLabel(f1.queryEnv, "bar"));
    EXPECT_EQ(1u, issues.list.size());
    // uid 0 is reported and skipped, the valid uid is still resolved
    EXPECT_EQ(TermVector({f1.term(2)}), getTermsByLabel(f1.queryEnv, "zeroed"));
    EXPECT_EQ(2u, issues.list.size());
}

TEST(UtilsTest, require_that_non_existing_uids_are_reported_by_term_set_lookup) {
    using TermVector = std::vector<const ITermData*>;
    TermLabelFixture f1;
    MyIssues         issues;
    // non-existing uid is reported and contributes nothing
    EXPECT_EQ(TermVector(), getTermsByLabel(f1.queryEnv, "fox"));
    EXPECT_EQ(1u, issues.list.size());
    // non-existing uid does not affect the valid one
    EXPECT_EQ(TermVector({f1.term(0)}), getTermsByLabel(f1.queryEnv, "partial"));
    EXPECT_EQ(2u, issues.list.size());
    // duplicated non-existing uid is reported at most once
    EXPECT_EQ(TermVector({f1.term(0)}), getTermsByLabel(f1.queryEnv, "dupmissing"));
    EXPECT_EQ(3u, issues.list.size());
}

TEST(UtilsTest, require_that_duplicated_uid_values_are_deduped_by_term_set_lookup) {
    using TermVector = std::vector<const ITermData*>;
    TermLabelFixture f1;
    MyIssues         issues;
    EXPECT_EQ(TermVector({f1.term(2)}), getTermsByLabel(f1.queryEnv, "dup"));
    EXPECT_EQ(0u, issues.list.size());
}

template <typename T> void verifyStrToNum(const std::string& label) {
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

TEST(UtilsTest, verify_str2Num) {
    verifyStrToNum<int8_t>("int8_t");
    verifyStrToNum<int16_t>("int16_t");
    verifyStrToNum<int32_t>("int32_t");
    verifyStrToNum<int64_t>("int64_t");
}

TEST(UtilsTest, lookup_document_frequency) {
    using OptDF = std::optional<DocumentFrequency>;
    IndexEnvironment index_env;
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
    auto& terms = query_env.getTerms();
    EXPECT_EQ(4, terms.size());
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, terms[0])); // bad unique id
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, terms[1])); // missing properties
    EXPECT_EQ(OptDF(), lookup_document_frequency(query_env, terms[2])); // incomplete properties
    EXPECT_EQ(OptDF({10, 15}), lookup_document_frequency(query_env, terms[3]));
}

GTEST_MAIN_RUN_ALL_TESTS()
