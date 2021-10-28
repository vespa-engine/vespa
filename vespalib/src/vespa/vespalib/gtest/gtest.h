// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <gtest/gtest.h>
#include <vespa/vespalib/stllike/string.h>
#include <iostream>

namespace vespalib {
// Tell google test how to print vespalib::string values:
static inline void PrintTo(const vespalib::string & value, std::ostream * os) {
    *os << value;
}
}

/**
 * Macro for creating a main function that runs all gtests.
 */
#define GTEST_MAIN_RUN_ALL_TESTS()          \
int                                         \
main(int argc, char* argv[])                \
{                                           \
    ::testing::InitGoogleTest(&argc, argv); \
    return RUN_ALL_TESTS();                 \
}

#ifdef INSTANTIATE_TEST_SUITE_P
#define VESPA_GTEST_INSTANTIATE_TEST_SUITE_P INSTANTIATE_TEST_SUITE_P
#else
#define VESPA_GTEST_INSTANTIATE_TEST_SUITE_P INSTANTIATE_TEST_CASE_P
#endif

#ifdef TYPED_TEST_SUITE
#define VESPA_GTEST_TYPED_TEST_SUITE TYPED_TEST_SUITE
#else
#define VESPA_GTEST_TYPED_TEST_SUITE TYPED_TEST_CASE
#endif

#define VESPA_EXPECT_EXCEPTION(TRY_BLOCK, EXCEPTION_TYPE, MESSAGE) \
    try {                                                                 \
        TRY_BLOCK;                                                        \
        FAIL() << "exception '" << MESSAGE << "' not thrown at all!";     \
    } catch(EXCEPTION_TYPE& e) {                                          \
        EXPECT_TRUE(contains(stringref(e.what()), stringref(MESSAGE))) << \
            " e.what(): " << e.what() << "\n";                            \
    } catch(...) {                                                        \
        FAIL() << "wrong exception type thrown";                          \
        throw;                                                            \
    }

