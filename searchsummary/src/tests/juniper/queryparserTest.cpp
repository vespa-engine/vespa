// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author Knut Omang
 */

#include "testenv.h"
#include "queryparser.h"
#include "fakerewriter.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <map>

/**
 * Test of the Weight method.
 */
TEST(QueryParserTest, testWeight) {
    {
        // Complex nested query (bug example from datasearch 4.0)
        juniper::QueryParser p2("OR(ANDNOT(AND(a,b),c),OR(d,e))");
        EXPECT_TRUE(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, nullptr);
        std::string          stk2;
        qh2.MatchObj()->Query()->Dump(stk2);
        EXPECT_EQ(stk2, "Node<a:2>[Node<a:2>[a:100,b:100],Node<a:2>[d:100,e:100]]");
    }
    {
        // Another complex nested query (bug example from datasearch 4.0)
        juniper::QueryParser p2("OR(ANDNOT(RANK(a,OR(b,c)),d),OR(e,f))");
        EXPECT_TRUE(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, nullptr);
        std::string          stk2;
        qh2.MatchObj()->Query()->Dump(stk2);
        EXPECT_EQ(stk2, "Node<a:2>[a:100,Node<a:2>[e:100,f:100]]");
    }
}

/**
 * Test of the Traverse method.
 */
TEST(QueryParserTest, testTraverse) {
    // simple OR query
    juniper::QueryParser p1("OR(a,b,c)");
    EXPECT_TRUE(p1.ParseError() == 0);

    juniper::QueryHandle qh1(p1, nullptr);
    std::string          stk1;
    qh1.MatchObj()->Query()->Dump(stk1);
    EXPECT_TRUE(strcmp(stk1.c_str(), "Node<a:3>[a:100,b:100,c:100]") == 0);

    {
        // Complex query with phrases
        juniper::QueryParser p2("OR(AND(xx,yy),PHRASE(junip*,proximity),PHRASE(data,search))");
        EXPECT_TRUE(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, nullptr);
        std::string          stk2;
        qh2.MatchObj()->Query()->Dump(stk2);
        EXPECT_TRUE(strcmp(stk2.c_str(), "Node<a:3,v>["
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
        EXPECT_TRUE(p2.ParseError() == 0);

        juniper::QueryHandle qh2(p2, nullptr);
        std::string          stk2;
        qh2.MatchObj()->Query()->Dump(stk2);
        std::string s(stk2.c_str());
        EXPECT_EQ(s, "Node<a:4,v>[cmsm:100,Node<a:2>[cidus:100,ntus:100],"
                       "Node<a:4>[jtft:100,jtct:100,jtin:100,jtfp:100],"
                       "Node<a:2,v>[Node<a:2,o,l:0,e,v,c>[strategic:100,marketing:100],"
                       "Node<a:2,o,l:0,e,v,c>[marketing:100,strategy:100]]]");
    }

    // Query with NEAR and WITHIN
    juniper::QueryParser p3("OR(NEAR/1(linux,kernel),WITHIN/3(linus,torvalds))");
    EXPECT_TRUE(p3.ParseError() == 0);

    juniper::QueryHandle qh3(p3, nullptr);
    std::string          stk3;
    qh3.MatchObj()->Query()->Dump(stk3);
    EXPECT_TRUE(strcmp(stk3.c_str(), "Node<a:2,v>["
                               "Node<a:2,l:1,v,c>[linux:100,kernel:100],"
                               "Node<a:2,o,l:3,v,c>[linus:100,torvalds:100]]") == 0);

    // Query with ONEAR
    juniper::QueryParser p4("OR(ONEAR/3(linus,torvalds))");
    EXPECT_TRUE(p4.ParseError() == 0);

    juniper::QueryHandle qh4(p4, nullptr);
    std::string          stk4;
    qh4.MatchObj()->Query()->Dump(stk4);
    EXPECT_TRUE(strcmp(stk4.c_str(), "Node<a:2,o,l:3,v,c>[linus:100,torvalds:100]") == 0);
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    juniper::TestEnv te(argc, argv, TEST_PATH("testclient.rc").c_str());
    return RUN_ALL_TESTS();
}
