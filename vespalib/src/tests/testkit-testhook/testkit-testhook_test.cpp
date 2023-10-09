// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <stdexcept>

//-----------------------------------------------------------------------------

struct Fixture {
    int number;
    Fixture(int a) : number(a) {}
    Fixture(int a, int b) : number(a * b) {}
    Fixture(int a, int b, int c) : number(a * b * c) {}
};

struct FixtureRef {
    Fixture &ref;
    FixtureRef(Fixture &r) : ref(r) {}
};

struct FixtureRef2 {
    Fixture &ref1;
    FixtureRef &ref2;
    FixtureRef2(Fixture &r1, FixtureRef &r2) : ref1(r1), ref2(r2) {}
};

struct Fixture1 {
    int number;
    Fixture1() : number(1) {}
};

struct Fixture2 {
    int number;
    Fixture2() : number(2) {}
};

struct Fixture3 {
    int number;
    Fixture3() : number(3) {}
};

//-----------------------------------------------------------------------------

TEST("first test; pass count should be 0") {
    TEST_FLUSH();
    EXPECT_EQUAL(0u, TEST_MASTER.getProgress().passCnt);
    EXPECT_EQUAL(0u, TEST_MASTER.getProgress().failCnt);
}

//-----------------------------------------------------------------------------

IGNORE_TEST("ignored test with a single non-fatal error") {
    EXPECT_EQUAL(1, 10);
}

TEST("verify that failure from previous test was ignored") {
    EXPECT_EQUAL(0u, TEST_MASTER.getProgress().failCnt);
}

//-----------------------------------------------------------------------------

TEST("a fatal failure should unwind the test") {
    TEST_FATAL("fatal failure!");
    TEST_FATAL("should not reach this!");
}

TEST("verify that previous test only produced a single failure") {
    EXPECT_EQUAL(1u, TEST_MASTER.getProgress().failCnt);
    TEST_MASTER.discardFailedChecks(1);
}

//-----------------------------------------------------------------------------

TEST_F("single fixture", Fixture1) {
    EXPECT_EQUAL(1, f.number);
}

TEST_FF("double fixture", Fixture1, Fixture2) {
    EXPECT_EQUAL(1, f1.number);
    EXPECT_EQUAL(2, f2.number);
}

TEST_FFF("triple fixture", Fixture1, Fixture2, Fixture3) {
    EXPECT_EQUAL(1, f1.number);
    EXPECT_EQUAL(2, f2.number);
    EXPECT_EQUAL(3, f3.number);
}

//-----------------------------------------------------------------------------

TEST_F("single parameterized fixture", Fixture(2)) {
    EXPECT_EQUAL(2, f.number);
}

TEST_FF("double parameterized fixture", Fixture(2), Fixture(2, 3)) {
    EXPECT_EQUAL(2, f1.number);
    EXPECT_EQUAL(6, f2.number);
}

TEST_FFF("triple parameterized fixture",
         Fixture(2), Fixture(2, 3), Fixture(2, 3, 5))
{
    EXPECT_EQUAL(2, f1.number);
    EXPECT_EQUAL(6, f2.number);
    EXPECT_EQUAL(30, f3.number);
}

//-----------------------------------------------------------------------------

TEST_FF("double parameterized fixture with backref",
        Fixture(42), FixtureRef(f1))
{
    EXPECT_EQUAL(42, f1.number);
    EXPECT_EQUAL(&f1, &f2.ref);
}

TEST_FFF("triple parameterized fixture with backref",
         Fixture(42), FixtureRef(f1), FixtureRef2(f1, f2))
{
    EXPECT_EQUAL(42, f1.number);
    EXPECT_EQUAL(&f1, &f2.ref);
    EXPECT_EQUAL(&f1, &f3.ref1);
    EXPECT_EQUAL(&f2, &f3.ref2);
}

//-----------------------------------------------------------------------------

TEST_F("unused fixture", Fixture1) {}
TEST_FF("unused double fixture", Fixture1, Fixture2) {}
TEST_FFF("unused triple fixture", Fixture1, Fixture2, Fixture3) {}

//-----------------------------------------------------------------------------

TEST("non-fatal failures should not unwind the test") {
    EXPECT_TRUE(false);
    TEST_ERROR("unfatal error");
    EXPECT_EQUAL(1, 10);
}

TEST("verify that all failures from previous test was counted") {
    EXPECT_EQUAL(3u, TEST_MASTER.getProgress().failCnt);
    TEST_MASTER.discardFailedChecks(3);
}

//-----------------------------------------------------------------------------

IGNORE_TEST("passed tests can also be ignored") {
    EXPECT_EQUAL(1, 1);
}

//-----------------------------------------------------------------------------

TEST("std::exception unwind will result in 1 failed test and 1 failed check") {
    throw std::runtime_error("something failed");
}

TEST("random unwind will result in 1 failed test and 1 failed check") {
    throw 1;
}

TEST("verify and ignore check failures from previous tests") {
    EXPECT_EQUAL(3u, TEST_MASTER.getProgress().failCnt);
    TEST_MASTER.discardFailedChecks(3);
}

//-----------------------------------------------------------------------------

TEST("verify that all appropriate tests have been executed") {
    TEST_FLUSH();
    EXPECT_EQUAL(24u, TEST_MASTER.getProgress().passCnt);
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
