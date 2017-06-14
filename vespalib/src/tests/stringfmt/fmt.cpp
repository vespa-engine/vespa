// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include <vespa/vespalib/testkit/testapp.h>

using vespalib::make_string;
using vespalib::make_vespa_string;

class Test : public vespalib::TestApp
{
public:
    void testSimple();
    int Main() override;
};

static bool eq(const vespalib::string& a, const vespalib::string& b)
{
	if (a == b) {
		return true;
	} else {
		return false;
	}
}


void
Test::testSimple()
{
    int i=7;
    int j=0x666;
    const char *s = "a test ";

    std::string foo = make_string("%d/%x", i, j);
    std::string bar = make_string("%d/%x", i, j).c_str();

    vespalib::string tst("7/666");

    EXPECT_TRUE(tst == foo);
    EXPECT_TRUE(tst == bar);

    vespalib::string foo_v = make_vespa_string("%d/%x", i, j);
    vespalib::string bar_v = make_vespa_string("%d/%x", i, j).c_str();
    vespalib::string tst_v = tst;

    EXPECT_TRUE(tst_v == foo_v);
    EXPECT_TRUE(tst_v == bar_v);

    EXPECT_TRUE(tst == make_string("%d/%x", i, j));
    EXPECT_TRUE(tst_v == make_vespa_string("%d/%x", i, j));

    tst = "a test ";
    tst_v = tst;
    EXPECT_TRUE(tst == make_string("%s", s));
    EXPECT_TRUE(tst_v == make_vespa_string("%s", s));

    tst = "a t";
    EXPECT_TRUE(tst == make_string("%.3s", s));
    tst_v = tst;
    foo_v = make_vespa_string("%.3s", s);
    EXPECT_TRUE(eq(tst, make_string("%.3s", s)));
    EXPECT_TRUE(eq(tst_v, make_vespa_string("%.3s", s)));

    const char *p = "really really really really "
              "very very very very very "
              "extremely extremely extremely extremely "
              "very very very very very "
              "really really really really "
              "insanely insanely insanely insanely "
              "hugely hugely hugely hugely "
              "bloated fat long string";
    tst = p;
    EXPECT_TRUE(eq(tst, make_string("%s", p)));
    tst_v = tst;
    EXPECT_TRUE(eq(tst_v, make_vespa_string("%s", p)));
}


int
Test::Main()
{
    TEST_INIT("stringfmt_test");
    testSimple();
    TEST_DONE();
}

TEST_APPHOOK(Test)
