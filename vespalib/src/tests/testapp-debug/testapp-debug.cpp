// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

TEST_MAIN() {
    int status = system("./vespalib_debug_test_app");
    ASSERT_FALSE(WIFSIGNALED(status));
    EXPECT_NOT_EQUAL(0, WEXITSTATUS(status));
    status = system("diff lhs.out rhs.out > diff.out");
    ASSERT_FALSE(WIFSIGNALED(status));
    EXPECT_NOT_EQUAL(0, WEXITSTATUS(status));


    std::string diff_cmd("diff diff.out ");
    diff_cmd += TEST_PATH("diff.ref");
    EXPECT_EQUAL(system(diff_cmd.c_str()), 0);
}
