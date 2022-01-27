// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
#include <vespa/searchlib/util/stringenum.h>

LOG_SETUP("stringenum");
#include <vespa/vespalib/testkit/testapp.h>

using namespace vespalib;

void
CheckLookup( search::util::StringEnum *strEnum, const char *str, int value)
{
    EXPECT_EQUAL(0, strcmp(str, strEnum->Lookup(value)));
    EXPECT_EQUAL(value, strEnum->Lookup(str));
}


TEST("test StringEnum Add and Lookup")
{

    search::util::StringEnum enum1;

    // check number of entries
    EXPECT_EQUAL(enum1.GetNumEntries(), 0u);

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

    // clear
    enum1.Clear();

    // check number of entries
    EXPECT_EQUAL(enum1.GetNumEntries(), 0u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
