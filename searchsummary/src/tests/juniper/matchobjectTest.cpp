// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author: Knut Omang
 */

#include "testenv.h"
#include "fakerewriter.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <map>

/**
 * Test of the Term method.
 */
TEST(MatchObjectTest, testTerm) {
    // Test that two equal keywords are matched properly:
    TestQuery q("NEAR/2(word,PHRASE(near,word))");

    const char* content = "This is a small text with word appearing near word";
    size_t      content_len = strlen(content);

    // Fetch a result descriptor:
    auto res = juniper::Analyse(*juniper::TestConfig, q._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    // Do the scanning manually. This calls accept several times
    res->Scan();
    Matcher& m = *res->_matcher;

    EXPECT_TRUE(m.TotalHits() == 3); // 3 occurrences
    match_candidate_set& ms = m.OrderedMatchSet();

    EXPECT_TRUE(ms.size() == 2);

    // printf("%d %d\n", m.TotalHits(),ms.size());
    TestQuery q1("t*t");
    TestQuery q2("*ea*");
    TestQuery q3("*d");
    TestQuery q4("*word");
    auto      r1 = juniper::Analyse(*juniper::TestConfig, q1._qhandle, content, content_len, 0);
    auto      r2 = juniper::Analyse(*juniper::TestConfig, q2._qhandle, content, content_len, 0);
    auto      r3 = juniper::Analyse(*juniper::TestConfig, q3._qhandle, content, content_len, 0);
    auto      r4 = juniper::Analyse(*juniper::TestConfig, q4._qhandle, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(r1));
    if (r1) {
        r1->Scan();
        EXPECT_TRUE(r1->_matcher->TotalHits() == 1);
    }
    EXPECT_TRUE(static_cast<bool>(r2));
    if (r2) {
        r2->Scan();
        EXPECT_TRUE(r2->_matcher->TotalHits() == 2);
    }

    if (r3) {
        r3->Scan();
        EXPECT_TRUE(r3->_matcher->TotalHits() == 2);
    } else {
        EXPECT_TRUE(static_cast<bool>(r3));
    }

    if (r4) {
        r4->Scan();
        EXPECT_EQ(r4->_matcher->TotalHits(), 2);
    } else {
        EXPECT_TRUE(static_cast<bool>(r4));
    }
}

/**
 * Test of the Match method.
 */
TEST(MatchObjectTest, testMatch) {
    // Check that we hit on the longest match first
    juniper::QueryParser p("AND(junipe,juniper)");
    juniper::QueryHandle qh(p, nullptr);

    MatchObject*    mo = qh.MatchObj();
    juniper::Result res(*juniper::TestConfig, qh, "", 0);
    unsigned        opts = 0;
    match_iterator  mi(mo, &res);
    ucs4_t          ucs4_str[10];
    Fast_UnicodeUtil::ucs4copy(ucs4_str, "junipers");
    Token token;
    token.token = ucs4_str;
    token.curlen = 8;
    int idx = mo->Match(mi, token, opts);
    EXPECT_TRUE(strcmp(mo->Term(idx)->term(), "juniper") == 0);

    {
        // This test would loop in v.2.2.2
        TestQuery q("(word,");
        EXPECT_TRUE(q._qparser.ParseError());
    }

    {
        // Test to trigger ticket #5734 Dev Data Search
        std::string       doc("A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit."
                                    "A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit."
                                    "A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit."
                                    "A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit."
                                    "A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit."
                                    "A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit."
                                    "A simple document with an extremelylongwordhit in the middle of it that is"
                                    "long enough to allow the error to be triggered extremelylongwordhit.");
        TestQuery         q("OR(OR(extremelylongwordhits,extremelylongwordhit,extremelylongwordhits,"
                                    "extremelylongwordhit,extremelylongwordhits,extremelylongwordhit,"
                                    "extremelylongwordhit,extremelylongwordhits,extremelylongwordhit,"
                                    "extremelylongwordhit,extremelylongwordhits,extremelylongwordhit,"
                                    "extremelylongwordhit,extremelylongwordhits,extremelylongwordhit,"
                                    "extremelylongwordhit,extremelylongwordhits,extremelylongwordhit,"
                                    "extremelylongwordhit))");
        QueryHandle&      qh1(q._qhandle);
        juniper::Result   res1(*juniper::TestConfig, qh1, doc.c_str(), doc.size());
        juniper::Summary* sum = res1.GetTeaser(nullptr);
        std::string       s(sum->Text());
        EXPECT_EQ(s, "A simple document with an <b>extremelylongwordhit</b> in the middle"
                       " of it that islong enough to allow...triggered "
                       "<b>extremelylongwordhit</b>.A simple document with an "
                       "<b>extremelylongwordhit</b> in the middle of it that islong enough to allow...");
    }
}

/**
 * Test matching in annotated buffers
 */
TEST(MatchObjectTest, testMatchAnnotated) {
    const char*       doc = "A big and ugly teaser about "
                            "\xEF\xBF\xB9"
                            "buying"
                            "\xEF\xBF\xBA"
                            "buy"
                            "\xEF\xBF\xBB"
                            " stuff";
    TestQuery         q("AND(big,buy)");
    QueryHandle&      qh1(q._qhandle);
    juniper::Result   res1(*juniper::TestConfig, qh1, doc, strlen(doc));
    juniper::Summary* sum = res1.GetTeaser(nullptr);
    std::string       s(sum->Text());

    EXPECT_EQ(s, "A <b>big</b> and ugly teaser about <b>"
                   "\xEF\xBF\xB9"
                   "buying"
                   "\xEF\xBF\xBA"
                   "buy"
                   "\xEF\xBF\xBB"
                   "</b> stuff");
}

/** Test parameter input via options
 */

TEST(MatchObjectTest, testParams) {
    {
        TestQuery    q("AND(a,b)", "near.1");
        QueryHandle& qh = q._qhandle;
        std::string  stk;
        qh.MatchObj()->Query()->Dump(stk);
        // Expect l:1 == limit:1 v: Validity check of keywords needed, c: Completeness req'ed
        EXPECT_EQ(stk, "Node<a:2,l:1,v,c>[a:100,b:100]");
    }

    {
        TestQuery    q("AND(a,b)", "onear.1");
        QueryHandle& qh = q._qhandle;
        std::string  stk;
        qh.MatchObj()->Query()->Dump(stk);
        // Expect l:1 == limit:1 o: ordered, v: Validity check of keywords needed,
        //        c: Completeness req'ed
        EXPECT_EQ(stk, "Node<a:2,o,l:1,v,c>[a:100,b:100]");
    }

    {
        TestQuery    q("AND(a,b)", "within.1");
        QueryHandle& qh = q._qhandle;
        std::string  stk;
        qh.MatchObj()->Query()->Dump(stk);
        // Expect l:1 == limit:1 o: ordered, v: Validity check of keywords needed,
        //        c: Completeness req'ed
        EXPECT_EQ(stk, "Node<a:2,o,l:1,v,c>[a:100,b:100]");
    }

    {
        // Check ONEAR.
        TestQuery    q("ONEAR/1(a,b)");
        QueryHandle& qh = q._qhandle;
        std::string  stk;
        qh.MatchObj()->Query()->Dump(stk);
        // Expect l:1 == limit:1 o: ordered, v: Validity check of keywords needed,
        //        c: Completeness req'ed
        EXPECT_EQ(stk, "Node<a:2,o,l:1,v,c>[a:100,b:100]");
    }

    {
        // Check that ANY works.
        TestQuery    q("ANY(a,b)");
        QueryHandle& qh = q._qhandle;
        std::string  stk;
        qh.MatchObj()->Query()->Dump(stk);
        // Expect l:1 == limit:1 o: ordered, v: Validity check of keywords needed,
        //        c: Completeness req'ed
        EXPECT_EQ(stk, "Node<a:2>[a:100,b:100]");
    }
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    juniper::TestEnv te(argc, argv, TEST_PATH("testclient.rc").c_str());
    return RUN_ALL_TESTS();
}
