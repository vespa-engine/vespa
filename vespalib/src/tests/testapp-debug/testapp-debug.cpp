// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

TEST_MAIN() {
    system("./vespalib_debug_test_app");
    system("diff lhs.out rhs.out > diff.out");


    const std::string src_dir = getenv("SOURCE_DIRECTORY") ? getenv("SOURCE_DIRECTORY") : ".";
    std::string diff_cmd("diff diff.out ");
    diff_cmd += src_dir + "/diff.ref";
    EXPECT_EQUAL(system(diff_cmd.c_str()), 0);
}
