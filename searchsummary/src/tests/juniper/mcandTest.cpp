// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author: Knut Omang
 */

#include "testenv.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <map>
#include <vespa/juniper/mcand.h>
#include <vespa/log/log.h>
LOG_SETUP(".mcandtest");

/**
 * Test that the empty query is handled properly even for Analyse and
 * GetTeaser/GetRelevancy/GetLog calls.. (Fastserver < 4.21 semantics)
 */
TEST(MatchCandidateTest, testLog) {
    TestQuery   q("");
    std::string content("Here we go hepp and then some words away hoi some silly text here");

    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content.c_str(), content.size(), 0);
    EXPECT_TRUE(static_cast<bool>(res)); // We get a result handle
    EXPECT_TRUE(!res->_mo);              // but it is empty

    juniper::Summary* sum = juniper::GetTeaser(*res);
    std::string       s(sum->Text());
    EXPECT_EQ(s, std::string(""));

    long relevance = juniper::GetRelevancy(*res);
    EXPECT_EQ(relevance, PROXIMITYBOOST_NOCONSTRAINT_OFFSET);

    sum = juniper::GetLog(*res);
    s = sum->Text();
    EXPECT_EQ(s, std::string(""));
}

/**
 * Test of proximity metric = 0
 */
TEST(MatchCandidateTest, testDump) {
    std::string content("Here we go hepp and then some words away hoi");

    {
        TestQuery q("NEAR/1(hepp,hoi)");
        auto      res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content.c_str(), content.size(), 0);
        EXPECT_TRUE(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // zero value since there are no hits and constraints are enabled..
        EXPECT_EQ(relevance, 0);
    }

    {
        TestQuery q("OR(NEAR/1(hepp,hoi),bananas)");
        auto      res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content.c_str(), content.size(), 0);
        EXPECT_TRUE(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // Check that X_CONSTR propagates as intended
        EXPECT_EQ(relevance, 0);
    }

    {
        TestQuery q("PHRASE(hepp,hoi)");
        auto      res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content.c_str(), content.size(), 0);
        EXPECT_TRUE(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // constant value since there are no hits but this is
        // also not a constrained search..
        EXPECT_EQ(relevance, PROXIMITYBOOST_NOCONSTRAINT_OFFSET);
    }

    {
        TestQuery q("AND(hepp,hoi)");
        auto      res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content.c_str(), content.size(), 0);
        EXPECT_TRUE(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // Relevance may change, but nice to discover such changes..
        // The important is that we get a nonzero value here as a hit
        EXPECT_EQ(relevance, 4470);
    }
}

/**
 * Test of the order method.
 */
TEST(MatchCandidateTest, testorder) {
    TestQuery q("PHRASE(test,phrase)");

    const char* content = "This is a simple text where a phrase match can be found not"
                          " quite adjacent to a test phrase work";
    size_t      content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Do the scanning manually. Scan calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    EXPECT_TRUE(m.TotalHits() == 3); // 3 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    EXPECT_TRUE(ms.size() == 1);
}

/**
 * Test of the matches_limit method.
 */
TEST(MatchCandidateTest, testMatches_limit) {
    TestQuery q("OR(PHRASE(phrase,match),PHRASE(test,word))");

    const char* content = "This is a simple text where a phrase match can be found not"
                          " quite adjacent to a test word";
    size_t      content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    EXPECT_TRUE(m.TotalHits() == 4); // 3 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    EXPECT_TRUE(ms.size() == 2); // The first (complete) match and the second starting at "test"

    // Check if we get the correct teaser as well..
    juniper::Summary* sum = juniper::GetTeaser(*res);
    EXPECT_TRUE(strcmp(sum->Text(), "This is a simple text where a <b>phrase</b> <b>match</b> can be found not"
                              " quite adjacent to a <b>test</b> <b>word</b>") == 0);
}

/**
 * Test of the accept method.
 */
TEST(MatchCandidateTest, testAccept) {
    TestQuery q("AND(simple,test)");

    const char* content = "This is a simple test where we should get a perfect match";
    size_t      content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    EXPECT_TRUE(m.TotalHits() == 2);  // 2 overlapping candidate starting points
    EXPECT_TRUE(m.QueryTerms() == 2); // 2 query terms

    match_candidate_set& ms = m.OrderedMatchSet();

    EXPECT_TRUE(ms.size() > 0);

    if (!ms.size()) {
        return; // No point in continuing..
    }

    MatchCandidate& mc = *(*(ms.begin()));

    EXPECT_TRUE(mc.elems() == 2);
    EXPECT_TRUE(mc.startpos() == 10);
    EXPECT_TRUE(mc.endpos() == 21);
    EXPECT_TRUE(!mc.order()); // Unordered for AND op
    EXPECT_TRUE(mc.ctxt_startpos() == 0);

    mc.make_keylist();
    EXPECT_TRUE(mc._klist.size() == 2); // Two occurrence elements in list

    // Just for the sake of it, verify that we get a proper teaser out of this also..
    juniper::Summary* sum = juniper::GetTeaser(*res);
    EXPECT_TRUE(strcmp(sum->Text(), "This is a <b>simple</b> <b>test</b> where we should get a perfect match") == 0);
}

/**
 * Test of simple nested query
 */
TEST(MatchCandidateTest, testMake_keylist) {
    TestQuery q("OR(AND(phrase,match),AND(test,phrase))");

    const char* content = "This is a simple text where a phrase match can be found not"
                          " quite adjacent to a test phrase";
    size_t      content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    EXPECT_TRUE(m.TotalHits() == 4); // 3 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    EXPECT_EQ(static_cast<size_t>(ms.size()), 6u);
}

/**
 * Test of the add_to_keylist method.
 */
TEST(MatchCandidateTest, testAdd_to_keylist) {
    // Nested NEAR-test (triggered if nested NEAR with PHRASE) Ticket Dev Data Search 6109
    TestQuery q("NEAR/4(PHRASE(phr1,phr2),PHRASE(phr3,phr4))");

    const char* content = "connect truende. phr1 phr2 www www www phr3 phr4 acuicola 8844";
    size_t      content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    EXPECT_TRUE(m.TotalHits() == 4); // 4 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    EXPECT_EQ(static_cast<size_t>(ms.size()), 1u); // Single result

    // Bug triggered when result is fetched..
    juniper::Summary* sum = juniper::GetTeaser(*res);
    std::string       s(sum->Text());
    EXPECT_EQ(s, "connect truende. <b>phr1</b> <b>phr2</b> www www www <b>phr3</b>"
                   " <b>phr4</b> acuicola 8844");
}

/**
 * Test of the length method.
 */
TEST(MatchCandidateTest, testLength) {
    const char* content = "this simple text with adjacent words of a certain pattern must"
                          " be matched according to specific rules to be detailed in this test.";
    size_t      content_len = strlen(content);

    {
        // Nested complex NEAR-test with double matches at same pos
        TestQuery q("NEAR/4(pattern,NEAR/1(simple,with),NEAR/2(simple,adjacent))");

        // Fetch a result descriptor:
        auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);

        juniper::Summary*    sum = juniper::GetTeaser(*res);
        Matcher&             m = *res->_matcher;
        match_candidate_set& ms = m.OrderedMatchSet();
        EXPECT_EQ(static_cast<size_t>(ms.size()), 1u);

        std::string s(sum->Text());
        EXPECT_EQ(s, "this <b>simple</b> text <b>with</b> <b>adjacent</b> words of "
                       "a certain <b>pattern</b> must be matched according to specific"
                       " rules to be detailed in this test.");
    }

    {
        // Nested complex NEAR-test with double matches at same pos should not yield hit with ONEAR
        TestQuery q("ONEAR/4(pattern,NEAR/1(simple,with),NEAR/2(simple,adjacent))");

        // Fetch a result descriptor:
        auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);

        res->Scan();
        Matcher&             m = *res->_matcher;
        match_candidate_set& ms = m.OrderedMatchSet();
        EXPECT_EQ(static_cast<size_t>(ms.size()), 0u);
    }

    {
        // Likewise nested complex NEAR-test with double matches at same pos but just outside limit
        // should not match:
        TestQuery q("NEAR/4(pattern,NEAR/1(simple,with),NEAR/1(simple,adjacent))");

        // Fetch a result descriptor:
        auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);

        res->Scan();
        Matcher&             m = *res->_matcher;
        match_candidate_set& ms = m.OrderedMatchSet();
        EXPECT_EQ(static_cast<size_t>(ms.size()), 0u);
    }
}

struct MyTokenProcessor : public ITokenProcessor {
    Matcher&            _m;
    std::vector<size_t> _cands;
    MyTokenProcessor(Matcher& m) : _m(m), _cands() {}
    ~MyTokenProcessor() override;
    void handle_token(Token& token) override {
        _m.handle_token(token);
        const match_sequence* ms = _m.GetWorkSet();
        _cands.push_back(ms[0].size());
        LOG(info, "match_sequence[0].size(%zu)", _cands.back());
    }
    void handle_end(Token& token) override { _m.handle_end(token); }
};

MyTokenProcessor::~MyTokenProcessor() = default;

/**
 * Test that max number of match candidates can be controlled.
 */
TEST(MatchCandidateTest, requireThatMaxNumberOfMatchCandidatesCanBeControlled) {
    TestQuery q("PHRASE(re,re,re,re,foo,re,re,re,re,bar)");
    q._qhandle._max_match_candidates = 4;

    const char* content = "re re re re foo re re re re bar re re re re foo re re re re bar";
    size_t      content_len = strlen(content);

    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Deflect tokens to my processor
    Matcher&         m = *res->_matcher;
    MyTokenProcessor proc(m);
    res->_tokenizer->SetSuccessor(&proc);
    res->Scan();

    EXPECT_EQ(proc._cands.size(), 20u);
    for (size_t i = 0; i < proc._cands.size(); ++i) { EXPECT_TRUE(proc._cands[i] <= 4u); }
    EXPECT_EQ(m.TotalHits(), 20);
    match_candidate_set& mcs = m.OrderedMatchSet();
    EXPECT_EQ(static_cast<size_t>(mcs.size()), 2u);
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    juniper::TestEnv te(argc, argv, TEST_PATH("testclient.rc").c_str());
    return RUN_ALL_TESTS();
}
