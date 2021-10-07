// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("stringenum");
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/util/stringenum.h>


#include <vespa/vespalib/testkit/testapp.h>

using namespace vespalib;

class MyApp : public vespalib::TestApp
{
public:
    void CheckLookup( search::util::StringEnum *strEnum, const char *str, int value);
    int Main() override;
    MyApp() {}
};


void
MyApp::CheckLookup( search::util::StringEnum *strEnum, const char *str, int value)
{
    EXPECT_EQUAL(0, strcmp(str, strEnum->Lookup(value)));
    EXPECT_EQUAL(value, strEnum->Lookup(str));
}


int
MyApp::Main()
{
    TEST_INIT("stringenum_test");

    search::util::StringEnum enum1;
    search::util::StringEnum enum2;

    // check number of entries
    EXPECT_EQUAL(enum1.GetNumEntries(), 0u);
    EXPECT_EQUAL(enum2.GetNumEntries(), 0u);

    // check add non-duplicates
    EXPECT_EQUAL(enum1.Add("zero"),   0);
    EXPECT_EQUAL(enum1.Add("one"),    1);
    EXPECT_EQUAL(enum1.Add("two"),    2);
    EXPECT_EQUAL(enum1.Add("three"),  3);
    EXPECT_EQUAL(enum1.Add("four"),   4);
    EXPECT_EQUAL(enum1.Add("five"),   5);
    EXPECT_EQUAL(enum1.Add("six"),    6);
    EXPECT_EQUAL(enum1.Add("seven"),  7);
    EXPECT_EQUAL(enum1.Add("eight"),  8);
    EXPECT_EQUAL(enum1.Add("nine"),   9);

    // check add duplicates
    EXPECT_EQUAL(enum1.Add("four"),   4);
    EXPECT_EQUAL(enum1.Add("eight"),  8);
    EXPECT_EQUAL(enum1.Add("six"),    6);
    EXPECT_EQUAL(enum1.Add("seven"),  7);
    EXPECT_EQUAL(enum1.Add("one"),    1);
    EXPECT_EQUAL(enum1.Add("nine"),   9);
    EXPECT_EQUAL(enum1.Add("five"),   5);
    EXPECT_EQUAL(enum1.Add("zero"),   0);
    EXPECT_EQUAL(enum1.Add("two"),    2);
    EXPECT_EQUAL(enum1.Add("three"),  3);

    // check add non-duplicate
    EXPECT_EQUAL(enum1.Add("ten"),   10);

    // check mapping and reverse mapping
    EXPECT_EQUAL(enum1.GetNumEntries(), 11u);
    TEST_DO(CheckLookup(&enum1, "zero",   0));
    TEST_DO(CheckLookup(&enum1, "one",    1));
    TEST_DO(CheckLookup(&enum1, "two",    2));
    TEST_DO(CheckLookup(&enum1, "three",  3));
    TEST_DO(CheckLookup(&enum1, "four",   4));
    TEST_DO(CheckLookup(&enum1, "five",   5));
    TEST_DO(CheckLookup(&enum1, "six",    6));
    TEST_DO(CheckLookup(&enum1, "seven",  7));
    TEST_DO(CheckLookup(&enum1, "eight",  8));
    TEST_DO(CheckLookup(&enum1, "nine",   9));
    TEST_DO(CheckLookup(&enum1, "ten",   10));

    TEST_FLUSH();

    // save/load
    EXPECT_TRUE(enum1.Save("tmp.enum"));
    EXPECT_TRUE(enum2.Load("tmp.enum"));

    // check mapping and reverse mapping
    EXPECT_EQUAL(enum2.GetNumEntries(), 11u);
    TEST_DO(CheckLookup(&enum2, "zero",   0));
    TEST_DO(CheckLookup(&enum2, "one",    1));
    TEST_DO(CheckLookup(&enum2, "two",    2));
    TEST_DO(CheckLookup(&enum2, "three",  3));
    TEST_DO(CheckLookup(&enum2, "four",   4));
    TEST_DO(CheckLookup(&enum2, "five",   5));
    TEST_DO(CheckLookup(&enum2, "six",    6));
    TEST_DO(CheckLookup(&enum2, "seven",  7));
    TEST_DO(CheckLookup(&enum2, "eight",  8));
    TEST_DO(CheckLookup(&enum2, "nine",   9));
    TEST_DO(CheckLookup(&enum2, "ten",   10));

    // add garbage
    enum2.Add("sfsdffgdfh");
    enum2.Add("sf24dfsgg3");
    enum2.Add("sfwertfgdh");
    enum2.Add("sfewrgtsfh");
    enum2.Add("sfgdsdgdfh");

    TEST_FLUSH();

    // reload
    EXPECT_TRUE(enum2.Load("tmp.enum"));

    // check garbage lost
    EXPECT_EQUAL(enum2.GetNumEntries(), 11u);
    EXPECT_EQUAL(-1, enum2.Lookup("sfewrgtsfh"));
    // check mapping and reverse mapping
    TEST_DO(CheckLookup(&enum2, "zero",   0));
    TEST_DO(CheckLookup(&enum2, "one",    1));
    TEST_DO(CheckLookup(&enum2, "two",    2));
    TEST_DO(CheckLookup(&enum2, "three",  3));
    TEST_DO(CheckLookup(&enum2, "four",   4));
    TEST_DO(CheckLookup(&enum2, "five",   5));
    TEST_DO(CheckLookup(&enum2, "six",    6));
    TEST_DO(CheckLookup(&enum2, "seven",  7));
    TEST_DO(CheckLookup(&enum2, "eight",  8));
    TEST_DO(CheckLookup(&enum2, "nine",   9));
    TEST_DO(CheckLookup(&enum2, "ten",   10));

    // clear
    enum1.Clear();
    enum2.Clear();

    // check number of entries
    EXPECT_EQUAL(enum1.GetNumEntries(), 0u);
    EXPECT_EQUAL(enum2.GetNumEntries(), 0u);

    TEST_DONE();
}

TEST_APPHOOK(MyApp);
