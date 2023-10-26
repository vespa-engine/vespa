// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>

using namespace vespalib;

void assertProgress(uint32_t pass, uint32_t fail) {
    TEST_FLUSH(); // sync progress to shared state
    if ((TEST_MASTER.getProgress().passCnt != pass) ||
        (TEST_MASTER.getProgress().failCnt != fail))
    {
        TEST_FATAL(make_string("expected (pass/fail) %d/%d, but was %zu/%zu",
                              pass, fail, TEST_MASTER.getProgress().passCnt,
                              TEST_MASTER.getProgress().failCnt).c_str());
    }
}

void testGeneric() {
    uint32_t a = 10;
    uint32_t b = 20;

    std::string x("xxx");
    std::string y("yyy");

    { // test ==
        EXPECT_EQUAL(a, a);     // OK
        assertProgress(1, 0);
        EXPECT_EQUAL(a, b);     // FAIL
        assertProgress(1, 1);
        EXPECT_EQUAL(b, a);     // FAIL
        assertProgress(1, 2);

        EXPECT_EQUAL(x, x);     // OK
        assertProgress(2, 2);
        EXPECT_EQUAL(x, y);     // FAIL
        assertProgress(2, 3);
        EXPECT_EQUAL(y, x);     // FAIL
        assertProgress(2, 4);
    }
    { // test !=
        EXPECT_NOT_EQUAL(a, a); // FAIL
        assertProgress(2, 5);
        EXPECT_NOT_EQUAL(a, b); // OK
        assertProgress(3, 5);
        EXPECT_NOT_EQUAL(b, a); // OK
        assertProgress(4, 5);

        EXPECT_NOT_EQUAL(x, x); // FAIL
        assertProgress(4, 6);
        EXPECT_NOT_EQUAL(x, y); // OK
        assertProgress(5, 6);
        EXPECT_NOT_EQUAL(y, x); // OK
        assertProgress(6, 6);
    }
    { // test <
        EXPECT_LESS(a, a); // FAIL
        assertProgress(6, 7);
        EXPECT_LESS(a, b); // OK
        assertProgress(7, 7);
        EXPECT_LESS(b, a); // FAIL
        assertProgress(7, 8);

        EXPECT_LESS(x, x); // FAIL
        assertProgress(7, 9);
        EXPECT_LESS(x, y); // OK
        assertProgress(8, 9);
        EXPECT_LESS(y, x); // FAIL
        assertProgress(8, 10);
    }
    { // test <=
        EXPECT_LESS_EQUAL(a, a); // OK
        assertProgress(9, 10);
        EXPECT_LESS_EQUAL(a, b); // OK
        assertProgress(10, 10);
        EXPECT_LESS_EQUAL(b, a); // FAIL
        assertProgress(10, 11);

        EXPECT_LESS_EQUAL(x, x); // OK
        assertProgress(11, 11);
        EXPECT_LESS_EQUAL(x, y); // OK
        assertProgress(12, 11);
        EXPECT_LESS_EQUAL(y, x); // FAIL
        assertProgress(12, 12);
    }
    { // test >
        EXPECT_GREATER(a, a); // FAIL
        assertProgress(12, 13);
        EXPECT_GREATER(a, b); // FAIL
        assertProgress(12, 14);
        EXPECT_GREATER(b, a); // OK
        assertProgress(13, 14);

        EXPECT_GREATER(x, x); // FAIL
        assertProgress(13, 15);
        EXPECT_GREATER(x, y); // FAIL
        assertProgress(13, 16);
        EXPECT_GREATER(y, x); // OK
        assertProgress(14, 16);
    }
    { // test >=
        EXPECT_GREATER_EQUAL(a, a); // OK
        assertProgress(15, 16);
        EXPECT_GREATER_EQUAL(a, b); // FAIL
        assertProgress(15, 17);
        EXPECT_GREATER_EQUAL(b, a); // OK
        assertProgress(16, 17);

        EXPECT_GREATER_EQUAL(x, x); // OK
        assertProgress(17, 17);
        EXPECT_GREATER_EQUAL(x, y); // FAIL
        assertProgress(17, 18);
        EXPECT_GREATER_EQUAL(y, x); // OK
        assertProgress(18, 18);
    }
    { // test ~=
        EXPECT_APPROX(1.0f, 1.1, 0.2);  // OK
        assertProgress(19, 18);
        EXPECT_APPROX(1.0f, 1.1, 0.05); // FAIL
        assertProgress(19, 19);
        EXPECT_APPROX(5, 5, 0);         // OK
        assertProgress(20, 19);
        EXPECT_APPROX(5, 6, 1);         // OK
        assertProgress(21, 19);

        EXPECT_APPROX(1.1, 1.0f, 0.2);  // OK
        assertProgress(22, 19);
        EXPECT_APPROX(1.1, 1.0f, 0.05); // FAIL
        assertProgress(22, 20);
        EXPECT_APPROX(5, 5, 0);         // OK
        assertProgress(23, 20);
        EXPECT_APPROX(6, 5, 1);         // OK
        assertProgress(24, 20);
    }
    { // test !~=
        EXPECT_NOT_APPROX(1.0f, 1.1, 0.2);  // FAIL
        assertProgress(24, 21);
        EXPECT_NOT_APPROX(1.0f, 1.1, 0.05); // OK
        assertProgress(25, 21);
        EXPECT_NOT_APPROX(5, 5, 0);         // FAIL
        assertProgress(25, 22);
        EXPECT_NOT_APPROX(5, 6, 1);         // FAIL
        assertProgress(25, 23);

        EXPECT_NOT_APPROX(1.1, 1.0f, 0.2);  // FAIL
        assertProgress(25, 24);
        EXPECT_NOT_APPROX(1.1, 1.0f, 0.05); // OK
        assertProgress(26, 24);
        EXPECT_NOT_APPROX(5, 5, 0);         // FAIL
        assertProgress(26, 25);
        EXPECT_NOT_APPROX(6, 5, 1);         // FAIL
        assertProgress(26, 26);
    }
    { // test throwing exceptions
        EXPECT_EXCEPTION({}, std::runtime_error, "foo");  // FAIL
        assertProgress(26, 27);
        try {
            EXPECT_EXCEPTION(throw std::logic_error("foo"), std::runtime_error,
                        "foo");  // FAIL
        } catch (std::logic_error &) {
        }
        assertProgress(26, 28);
        try {
            EXPECT_EXCEPTION(throw std::runtime_error("bar"), std::runtime_error,
                        "foo");  // FAIL
        } catch (std::runtime_error &) {
        }
        assertProgress(26, 29);
        EXPECT_EXCEPTION(throw std::runtime_error("foo"), std::runtime_error,
                    "foo");  // OK
        assertProgress(27, 29);
    }
    { // test implicit approx for double
        double foo = 1.0;
        double bar = 1.0 + 1e-9;
        double baz = 1.0 + 1e-5;
        EXPECT_TRUE(foo != bar); // OK
        EXPECT_EQUAL(foo, bar);  // OK
        EXPECT_EQUAL(bar, foo);  // OK
        assertProgress(30, 29);
        EXPECT_EQUAL(foo, baz);  // FAIL
        EXPECT_EQUAL(baz, foo);  // FAIL
        assertProgress(30, 31);
    }
}

TEST_MAIN() {
    testGeneric();
    TEST_MASTER.discardFailedChecks(31);
}
