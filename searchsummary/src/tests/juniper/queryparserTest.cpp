// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author Knut Omang
 */
#include "queryparserTest.h"
#include "fakerewriter.h"


// Comment out cerr below to ignore unimplemented tests
#define NOTEST(name) \
std::cerr << std::endl << __FILE__ << ':' << __LINE__ << ": " \
          << "No test for method '" << (name) << "'" << std::endl;

/*************************************************************************
 *                      Test methods
 *
 * This section contains boolean methods for testing each public method
 * in the class being tested
 *************************************************************************/

/**
 * Test of the UsefulIndex method.
 */
void QueryParserTest::testUsefulIndex() {
//  NOTEST("UsefulIndex");
}


/**
 * Test of the Index method (also implicit test of integration with
 * expander interface)
 */
void QueryParserTest::testIndex() {
    FakeRewriter fexp;
    // Add as rewriter for query and not for document
    juniper::_Juniper->AddRewriter("ourindex", &fexp, true, false);
    juniper::QueryParser p("AND(ourindex:cake,myindex:eat)");
    _test(p.ParseError() == 0);
    if (p.ParseError()) return;

    juniper::QueryHandle qh(p, NULL, juniper::_Juniper->getModifier());
    std::string stk;
    qh.MatchObj(0)->Query()->Dump(stk);
    _test_equal(stk, "Node<a:2>[Node<a:4>[cake0:100,cake1:100,cake2:100,cake3:100],eat:100]");

    std::string stk1;
    qh.MatchObj(6)->Query()->Dump(stk1);
    _test_equal(stk1, "Node<a:2>[cake:100,eat:100]");

    // Then let's add a reducer rewriter (should not affect anything..)
    juniper::_Juniper->AddRewriter("myindex", &fexp, false, true);
    std::string stk2;
    qh.MatchObj(0)->Query()->Dump(stk2);
    _test_equal(stk2, "Node<a:2>[Node<a:4>[cake0:100,cake1:100,cake2:100,cake3:100],eat:100]");
}


/**
 * Test of the Creator method.
 */
void QueryParserTest::testCreator() {
//  NOTEST("Creator");
}


/**
 * Test of the Weight method.
 */
void QueryParserTest::testWeight() {
    {
        // Complex nested query (bug example from datasearch 4.0)
        juniper::QueryParser p2("OR(ANDNOT(AND(a,b),c),OR(d,e))");
        _test(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, NULL, juniper::_Juniper->getModifier());
        std::string stk2;
        qh2.MatchObj(0)->Query()->Dump(stk2);
        _test_equal(stk2, "Node<a:2>[Node<a:2>[a:100,b:100],Node<a:2>[d:100,e:100]]");
    }
    {
        // Another complex nested query (bug example from datasearch 4.0)
        juniper::QueryParser p2("OR(ANDNOT(RANK(a,OR(b,c)),d),OR(e,f))");
        _test(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, NULL, juniper::_Juniper->getModifier());
        std::string stk2;
        qh2.MatchObj(0)->Query()->Dump(stk2);
        _test_equal(stk2, "Node<a:2>[a:100,Node<a:2>[e:100,f:100]]");
    }
}


/**
 * Test of the Traverse method.
 */
void QueryParserTest::testTraverse() {
    // simple OR query
    juniper::QueryParser p1("OR(a,b,c)");
    _test(p1.ParseError() == 0);

    juniper::QueryHandle qh1(p1, NULL, juniper::_Juniper->getModifier());
    std::string stk1;
    qh1.MatchObj(0)->Query()->Dump(stk1);
    _test(strcmp(stk1.c_str(),"Node<a:3>[a:100,b:100,c:100]") == 0);

    {
        // Complex query with phrases
        juniper::QueryParser p2("OR(AND(xx,yy),PHRASE(junip*,proximity),PHRASE(data,search))");
        _test(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, NULL, juniper::_Juniper->getModifier());
        std::string stk2;
        qh2.MatchObj(0)->Query()->Dump(stk2);
        _test(strcmp(stk2.c_str(),
                     "Node<a:3,v>["
                     "Node<a:2>[xx:100,yy:100],"
                     "Node<a:2,o,l:0,e,v,c>[junip*:100,proximity:100],"
                     "Node<a:2,o,l:0,e,v,c>[data:100,search:100]]") == 0);
    }

    {
        // Triggering bug ticket 5690 Dev Data Search:
        juniper::QueryParser p2("ANDNOT(ANDNOT(AND(cmsm,OR(cidus,ntus),"
                                "OR(jtft,jtct,jtin,jtfp),"
                                "OR(PHRASE(strategic,marketing),"
                                "PHRASE(marketing,strategy))),a))");
        _test(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, NULL, juniper::_Juniper->getModifier());
        std::string stk2;
        qh2.MatchObj(0)->Query()->Dump(stk2);
        std::string s(stk2.c_str());
        _test_equal(s,
                    "Node<a:4,v>[cmsm:100,Node<a:2>[cidus:100,ntus:100],"
                    "Node<a:4>[jtft:100,jtct:100,jtin:100,jtfp:100],"
                    "Node<a:2,v>[Node<a:2,o,l:0,e,v,c>[strategic:100,marketing:100],"
                    "Node<a:2,o,l:0,e,v,c>[marketing:100,strategy:100]]]");
    }

    // Query with NEAR and WITHIN
    juniper::QueryParser p3("OR(NEAR/1(linux,kernel),WITHIN/3(linus,torvalds))");
    _test(p3.ParseError() == 0);

    juniper::QueryHandle qh3(p3, NULL, juniper::_Juniper->getModifier());
    std::string stk3;
    qh3.MatchObj(0)->Query()->Dump(stk3);
    _test(strcmp(stk3.c_str(),
                 "Node<a:2,v>["
                 "Node<a:2,l:1,v,c>[linux:100,kernel:100],"
                 "Node<a:2,o,l:3,v,c>[linus:100,torvalds:100]]") == 0);

    // Query with ONEAR
    juniper::QueryParser p4("OR(ONEAR/3(linus,torvalds))");
    _test(p4.ParseError() == 0);

    juniper::QueryHandle qh4(p4, NULL, juniper::_Juniper->getModifier());
    std::string stk4;
    qh4.MatchObj(0)->Query()->Dump(stk4);
    _test(strcmp(stk4.c_str(),
                 "Node<a:2,o,l:3,v,c>[linus:100,torvalds:100]") == 0);
}


/*************************************************************************
 *                      Test administration methods
 *************************************************************************/

/**
 * Set up common stuff for all test methods.
 * This method is called immediately before each test method is called
 */
bool QueryParserTest::setUp() {
    return true;
}

/**
 * Tear down common stuff for all test methods.
 * This method is called immediately after each test method is called
 */
void QueryParserTest::tearDown() {
}

/**
 * Build up a map with all test methods
 */
void QueryParserTest::init() {
    test_methods_["testUsefulIndex"] =
        &QueryParserTest::testUsefulIndex;
    test_methods_["testIndex"] =
        &QueryParserTest::testIndex;
    test_methods_["testCreator"] =
        &QueryParserTest::testCreator;
    test_methods_["testWeight"] =
        &QueryParserTest::testWeight;
    test_methods_["testTraverse"] =
        &QueryParserTest::testTraverse;
}

/*************************************************************************
 *                         main entry points
 *************************************************************************/


void QueryParserTest::Run(MethodContainer::iterator &itr) {
    try {
        if (setUp()) {
            (this->*itr->second)();
            tearDown();
        }
    } catch (...) {
        _fail("Got unknown exception in test method " + itr->first);
    }
}

void QueryParserTest::Run(const char* method) {
    MethodContainer::iterator pos(test_methods_.find(method));
    if (pos != test_methods_.end()) {
        Run(pos);
    } else {
        std::cerr << "ERROR: No test method named \""
                  << method << "\"" << std::endl;
        _fail("No such method");
    }
}

void QueryParserTest::Run() {
    for (MethodContainer::iterator itr(test_methods_.begin());
         itr != test_methods_.end();
         ++itr)
        Run(itr);
}

/*
 * Parse runtime arguments before running.
 * If the -m METHOD parameter is given, run only that method
 */
void QueryParserTest::Run(int argc, char* argv[]) {
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "-m") == 0 && argc > i + 1)
        {
            Run(argv[++i]);
            return;
        }
    }
    Run();
}
