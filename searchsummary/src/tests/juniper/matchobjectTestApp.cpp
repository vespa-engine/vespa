// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchobjectTest.h"
#include "testenv.h"
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/juniper/wildcard_match.h>
#include <iostream>

namespace {
void test(const char * word, const char * pattern, bool expect) {
    EXPECT_EQUAL(expect, fast::util::wildcard_match(word, pattern));
}
}

void
test_wildcard()
{
    test("a", "b", false);
    test("b", "b", true);
    test("abc", "def", false);
    test("def", "def", true);
    test("def", "d?f", true);
    test("def", "d?d", false);
    test("def", "??d", false);
    test("def", "d??", true);
    test("abcdef", "a*e", false);
    test("abcdef", "a*f", true);
    test("abcdef", "a?c*f", true);
    test("abcdef", "a?b*f", false);
    test("abcdef", "a*b*f", true);
    test("abcdef", "abc*", true);
    test("abcdef", "*def", true);
}

int main(int argc, char **argv) {
    test_wildcard();
    juniper::TestEnv te(argc, argv, TEST_PATH("./testclient.rc").c_str());
    MatchObjectTest test;
    test.SetStream(&std::cout);
    test.Run(argc, argv);
    return (int)test.Report();
}
