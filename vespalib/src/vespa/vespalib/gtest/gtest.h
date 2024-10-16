// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <gtest/gtest.h>

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

#define VESPA_EXPECT_EXCEPTION(TRY_BLOCK, EXCEPTION_TYPE, MESSAGE) \
    try {                                                                 \
        TRY_BLOCK;                                                        \
        FAIL() << "exception '" << MESSAGE << "' not thrown at all!";     \
    } catch(EXCEPTION_TYPE& e) {                                          \
        EXPECT_TRUE(std::string_view(e.what()).contains(std::string_view(MESSAGE))) << \
            " e.what(): " << e.what() << "\n";                            \
    } catch(...) {                                                        \
        FAIL() << "wrong exception type thrown";                          \
        throw;                                                            \
    }

