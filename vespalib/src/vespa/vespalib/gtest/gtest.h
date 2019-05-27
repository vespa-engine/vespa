// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
