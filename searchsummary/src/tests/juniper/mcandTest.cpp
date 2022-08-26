// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author: Knut Omang
 */

#include "mcandTest.h"

#include <vespa/log/log.h>
LOG_SETUP(".mcandtest");

// Comment out cerr below to ignore unimplemented tests
#define NOTEST(name) \
   std::cerr << std::endl << __FILE__ << ':' << __LINE__ << ": " \
         << "No test for method '" << (name) << "'" << std::endl;


MatchCandidateTest::MatchCandidateTest() :
    Test("MatchCandidate"), test_methods_()
{ init(); }

/*************************************************************************
 *                      Test methods
 *
 * This section contains boolean methods for testing each public method
 * in the class being tested
 *************************************************************************/

/**
 * Test of the SetDocid method.
 */
void MatchCandidateTest::testSetDocid() {
//  NOTEST("SetDocid");
}


/**
 * Test that the empty query is handled properly even for Analyse and
 * GetTeaser/GetRelevancy/GetLog calls.. (Fastserver < 4.21 semantics)
 */
void MatchCandidateTest::testLog() {
    TestQuery q("");
    std::string content("Here we go hepp and then some words away hoi some silly text here");

    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content.c_str(), content.size(),
                                0, 0);
    _test(static_cast<bool>(res)); // We get a result handle
    _test(!res->_mo); // but it is empty

    juniper::Summary* sum = juniper::GetTeaser(*res);
    std::string s(sum->Text());
    _test_equal(s, std::string(""));

    long relevance = juniper::GetRelevancy(*res);
    _test_equal(relevance, PROXIMITYBOOST_NOCONSTRAINT_OFFSET);

    sum = juniper::GetLog(*res);
    s = sum->Text();
    _test_equal(s, std::string(""));
}


/**
 * Test of proximity metric = 0
 */
void MatchCandidateTest::testDump() {
    std::string content("Here we go hepp and then some words away hoi");

    {
        TestQuery q("NEAR/1(hepp,hoi)");
        auto res = juniper::Analyse(*juniper::TestConfig,
                                    q._qhandle,
                                    content.c_str(), content.size(),
                                    0, 0);
        _test(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // zero value since there are no hits and constraints are enabled..
        _test_equal(relevance, 0);
    }

    {
        TestQuery q("OR(NEAR/1(hepp,hoi),bananas)");
        auto res = juniper::Analyse(*juniper::TestConfig,
                                    q._qhandle,
                                    content.c_str(), content.size(),
                                    0, 0);
        _test(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // Check that X_CONSTR propagates as intended
        _test_equal(relevance, 0);
    }

    {
        TestQuery q("PHRASE(hepp,hoi)");
        auto res = juniper::Analyse(*juniper::TestConfig,
                                    q._qhandle,
                                    content.c_str(), content.size(),
                                    0, 0);
        _test(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // constant value since there are no hits but this is
        // also not a constrained search..
        _test_equal(relevance, PROXIMITYBOOST_NOCONSTRAINT_OFFSET);
    }

    {
        TestQuery q("AND(hepp,hoi)");
        auto res = juniper::Analyse(*juniper::TestConfig,
                                    q._qhandle,
                                    content.c_str(), content.size(),
                                    0, 0);
        _test(static_cast<bool>(res));
        long relevance = juniper::GetRelevancy(*res);
        // Relevance may change, but nice to discover such changes..
        // The important is that we get a nonzero value here as a hit
        _test_equal(relevance, 4470);
    }
}


/**
 * Test of the order method.
 */
void MatchCandidateTest::testorder() {
    TestQuery q("PHRASE(test,phrase)");

    const char* content = "This is a simple text where a phrase match can be found not"
                          " quite adjacent to a test phrase work";
    size_t content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content, content_len,
                                0, 0);
    _test(static_cast<bool>(res));

    // Do the scanning manually. Scan calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    _test(m.TotalHits() == 3); // 3 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    _test(ms.size() == 1);
}


/**
 * Test of the matches_limit method.
 */
void MatchCandidateTest::testMatches_limit() {
    TestQuery q("OR(PHRASE(phrase,match),PHRASE(test,word))");

    const char* content = "This is a simple text where a phrase match can be found not"
                          " quite adjacent to a test word";
    size_t content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content, content_len,
                                0, 0);
    _test(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    _test(m.TotalHits() == 4);// 3 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    _test(ms.size() == 2); // The first (complete) match and the second starting at "test"

    // Check if we get the correct teaser as well..
    juniper::Summary* sum = juniper::GetTeaser(*res);
    _test(strcmp(sum->Text(),
                 "This is a simple text where a <b>phrase</b> <b>match</b> can be found not"
                 " quite adjacent to a <b>test</b> <b>word</b>") == 0);
}


/**
 * Test of the accept method.
 */
void MatchCandidateTest::testAccept() {
    TestQuery q("AND(simple,test)");

    const char* content = "This is a simple test where we should get a perfect match";
    size_t content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content, content_len,
                                0, 0);
    _test(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    _test(m.TotalHits() == 2); // 2 overlapping candidate starting points
    _test(m.QueryTerms() == 2); // 2 query terms

    match_candidate_set& ms = m.OrderedMatchSet();

    _test(ms.size() > 0);

    if (!ms.size()) {
        return; // No point in continuing..
    }

    MatchCandidate& mc = *(*(ms.begin()));

    _test(mc.elems() == 2);
    _test(mc.startpos() == 10);
    _test(mc.endpos() == 21);
    _test(!mc.order()); // Unordered for AND op
    _test(mc.ctxt_startpos() == 0);

    mc.make_keylist();
    _test(mc._klist.size() == 2); // Two occurrence elements in list

    // Just for the sake of it, verify that we get a proper teaser out of this also..
    juniper::Summary* sum = juniper::GetTeaser(*res);
    _test(strcmp(sum->Text(),
                 "This is a <b>simple</b> <b>test</b> where we should get a perfect match") == 0);
}


/**
 * Test of the rank method.
 */
void MatchCandidateTest::testRank() {
//  NOTEST("rank");
}


/**
 * Test of simple nested query
 */
void MatchCandidateTest::testMake_keylist() {
    TestQuery q("OR(AND(phrase,match),AND(test,phrase))");

    const char* content = "This is a simple text where a phrase match can be found not"
                          " quite adjacent to a test phrase";
    size_t content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content, content_len,
                                0, 0);
    _test(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    _test(m.TotalHits() == 4);// 3 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    _test_equal(static_cast<size_t>(ms.size()), 6u);
}


/**
 * Test of the add_to_keylist method.
 */
void MatchCandidateTest::testAdd_to_keylist() {
    // Nested NEAR-test (triggered if nested NEAR with PHRASE) Ticket Dev Data Search 6109
    TestQuery q("NEAR/4(PHRASE(phr1,phr2),PHRASE(phr3,phr4))");

    const char* content = "connect truende. phr1 phr2 www www www phr3 phr4 acuicola 8844";
    size_t content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content, content_len,
                                0, 0);
    _test(static_cast<bool>(res));

// Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    _test(m.TotalHits() == 4);// 4 occurrences

    match_candidate_set& ms = m.OrderedMatchSet();

    _test_equal(static_cast<size_t>(ms.size()), 1u); // Single result

    // Bug triggered when result is fetched..
    juniper::Summary* sum = juniper::GetTeaser(*res);
    std::string s(sum->Text());
    _test_equal(s,
                "connect truende. <b>phr1</b> <b>phr2</b> www www www <b>phr3</b>"
                " <b>phr4</b> acuicola 8844");

}


/**
 * Test of the length method.
 */
void MatchCandidateTest::testLength() {
    const char* content = "this simple text with adjacent words of a certain pattern must"
                          " be matched according to specific rules to be detailed in this test.";
    size_t content_len = strlen(content);

    {
        // Nested complex NEAR-test with double matches at same pos
        TestQuery q("NEAR/4(pattern,NEAR/1(simple,with),NEAR/2(simple,adjacent))");

        // Fetch a result descriptor:
        auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle,
                                    content, content_len,
                                    0, 0);

        juniper::Summary* sum = juniper::GetTeaser(*res);
        Matcher& m = *res->_matcher;
        match_candidate_set& ms = m.OrderedMatchSet();
        _test_equal(static_cast<size_t>(ms.size()), 1u);

        std::string s(sum->Text());
        _test_equal(s,
                    "this <b>simple</b> text <b>with</b> <b>adjacent</b> words of "
                    "a certain <b>pattern</b> must be matched according to specific"
                    " rules to be detailed in this test.");
    }

    {
        // Nested complex NEAR-test with double matches at same pos should not yield hit with ONEAR
        TestQuery q("ONEAR/4(pattern,NEAR/1(simple,with),NEAR/2(simple,adjacent))");

        // Fetch a result descriptor:
        auto res = juniper::Analyse(*juniper::TestConfig,
                                    q._qhandle
                                    ,content, content_len,
                                    0, 0);

        res->Scan();
        Matcher& m = *res->_matcher;
        match_candidate_set& ms = m.OrderedMatchSet();
        _test_equal(static_cast<size_t>(ms.size()), 0u);

    }

    {
        // Likewise nested complex NEAR-test with double matches at same pos but just outside limit
        // should not match:
        TestQuery q("NEAR/4(pattern,NEAR/1(simple,with),NEAR/1(simple,adjacent))");

        // Fetch a result descriptor:
        auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle,
                                    content, content_len,
                                    0, 0);

        res->Scan();
        Matcher& m = *res->_matcher;
        match_candidate_set& ms = m.OrderedMatchSet();
        _test_equal(static_cast<size_t>(ms.size()), 0u);
    }
}


struct MyTokenProcessor : public ITokenProcessor
{
    Matcher &_m;
    std::vector<size_t> _cands;
    MyTokenProcessor(Matcher &m) : _m(m), _cands() {}
    ~MyTokenProcessor() override;
    void handle_token(Token &token) override {
        _m.handle_token(token);
        const match_sequence *ms = _m.GetWorkSet();
        _cands.push_back(ms[0].size());
        LOG(info, "match_sequence[0].size(%zu)", _cands.back());
    }
    void handle_end(Token &token) override {
        _m.handle_end(token);
    }
};

MyTokenProcessor::~MyTokenProcessor() = default;

/**
 * Test that max number of match candidates can be controlled.
 */
void MatchCandidateTest::requireThatMaxNumberOfMatchCandidatesCanBeControlled()
{
    TestQuery q("PHRASE(re,re,re,re,foo,re,re,re,re,bar)");
    q._qhandle._max_match_candidates = 4;

    const char *content = "re re re re foo re re re re bar re re re re foo re re re re bar";
    size_t content_len = strlen(content);

    auto res = juniper::Analyse(*juniper::TestConfig,
                                q._qhandle,
                                content, content_len,
                                0, 0);
    _test(static_cast<bool>(res));

    // Deflect tokens to my processor
    Matcher &m = *res->_matcher;
    MyTokenProcessor proc(m);
    res->_tokenizer->SetSuccessor(&proc);
    res->Scan();

    _test_equal(proc._cands.size(), 20u);
    for (size_t i = 0; i < proc._cands.size(); ++i) {
        _test(proc._cands[i] <= 4u);
    }
    _test_equal(m.TotalHits(), 20);
    match_candidate_set& mcs = m.OrderedMatchSet();
    _test_equal(static_cast<size_t>(mcs.size()), 2u);
}


/**
 * Test of the order method.
 */
void MatchCandidateTest::testOrder() {
//  NOTEST("order");
}


/**
 * Test of the size method.
 */
void MatchCandidateTest::testSize() {
//  NOTEST("size");
}


/**
 * Test of the endpos method.
 */
void MatchCandidateTest::testEndpos() {
//  NOTEST("endpos");
}


/**
 * Test of the ctxt_startpos method.
 */
void MatchCandidateTest::testCtxt_startpos() {
//  NOTEST("ctxt_startpos");
}


/**
 * Test of the starttoken method.
 */
void MatchCandidateTest::testStarttoken() {
//  NOTEST("starttoken");
}


/**
 * Test of the word_distance method.
 */
void MatchCandidateTest::testWord_distance() {
//  NOTEST("word_distance");
}


/**
 * Test of the distance method.
 */
void MatchCandidateTest::testDistance() {
//  NOTEST("distance");
}


/**
 * Test of the elem_store_sz method.
 */
void MatchCandidateTest::testElem_store_sz() {
//  NOTEST("elem_store_sz");
}


/**
 * Test of the elems method.
 */
void MatchCandidateTest::testElems() {
//  NOTEST("elems");
}


/**
 * Test of the distance method.
 */
void MatchCandidateTest::testDistance1() {
//  NOTEST("distance");
}


/**
 * Test of the set_valid method.
 */
void MatchCandidateTest::testSet_valid() {
//  NOTEST("set_valid");
}


/*************************************************************************
 *                      Test administration methods
 *************************************************************************/

/**
 * Set up common stuff for all test methods.
 * This method is called immediately before each test method is called
 */
bool MatchCandidateTest::setUp() {
    return true;
}

/**
 * Tear down common stuff for all test methods.
 * This method is called immediately after each test method is called
 */
void MatchCandidateTest::tearDown() {
}

/**
 * Build up a map with all test methods
 */
void MatchCandidateTest::init() {
    test_methods_["testSetDocid"] =
        &MatchCandidateTest::testSetDocid;
    test_methods_["testLog"] =
        &MatchCandidateTest::testLog;
    test_methods_["testDump"] =
        &MatchCandidateTest::testDump;
    test_methods_["testorder"] =
        &MatchCandidateTest::testorder;
    test_methods_["testMatches_limit"] =
        &MatchCandidateTest::testMatches_limit;
    test_methods_["testAccept"] =
        &MatchCandidateTest::testAccept;
    test_methods_["testRank"] =
        &MatchCandidateTest::testRank;
    test_methods_["testMake_keylist"] =
        &MatchCandidateTest::testMake_keylist;
    test_methods_["testAdd_to_keylist"] =
        &MatchCandidateTest::testAdd_to_keylist;
    test_methods_["testLength"] =
        &MatchCandidateTest::testLength;
    test_methods_["requireThatMaxNumberOfMatchCandidatesCanBeControlled"] =
        &MatchCandidateTest::requireThatMaxNumberOfMatchCandidatesCanBeControlled;
    test_methods_["testOrder"] =
        &MatchCandidateTest::testOrder;
    test_methods_["testSize"] =
        &MatchCandidateTest::testSize;
    test_methods_["testEndpos"] =
        &MatchCandidateTest::testEndpos;
    test_methods_["testCtxt_startpos"] =
        &MatchCandidateTest::testCtxt_startpos;
    test_methods_["testStarttoken"] =
        &MatchCandidateTest::testStarttoken;
    test_methods_["testWord_distance"] =
        &MatchCandidateTest::testWord_distance;
    test_methods_["testDistance"] =
        &MatchCandidateTest::testDistance;
    test_methods_["testElem_store_sz"] =
        &MatchCandidateTest::testElem_store_sz;
    test_methods_["testElems"] =
        &MatchCandidateTest::testElems;
    test_methods_["testDistance1"] =
        &MatchCandidateTest::testDistance1;
    test_methods_["testSet_valid"] =
        &MatchCandidateTest::testSet_valid;
}

/*************************************************************************
 *                         main entry points
 *************************************************************************/


void MatchCandidateTest::Run(MethodContainer::iterator &itr) {
    try {
        if (setUp()) {
            (this->*itr->second)();
            tearDown();
        }
    } catch (...) {
        _fail("Got unknown exception in test method " + itr->first);
    }
}

void MatchCandidateTest::Run(const char* method) {
    MethodContainer::iterator pos(test_methods_.find(method));
    if (pos != test_methods_.end()) {
        Run(pos);
    } else {
        std::cerr << "ERROR: No test method named \""
                  << method << "\"" << std::endl;
        _fail("No such method");
    }
}

void MatchCandidateTest::Run() {
    for (MethodContainer::iterator itr(test_methods_.begin());
         itr != test_methods_.end();
         ++itr)
        Run(itr);
}

/*
 * Parse runtime arguments before running.
 * If the -m METHOD parameter is given, run only that method
 */
void MatchCandidateTest::Run(int argc, char* argv[]) {
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "-m") == 0 && argc > i + 1)
        {
            Run(argv[++i]);
            return;
        }
    }
    Run();
}
