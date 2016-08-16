// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/slaveproc.h>

using namespace vespalib;

bool runPrint(const char *cmd) {
    std::string out;
    bool res = SlaveProc::run(cmd, out);
    fprintf(stderr, "%s", out.c_str());
    return res;
}

TEST("make fixture macros") {
    EXPECT_FALSE(runPrint("../../apps/make_fixture_macros/vespalib_make_fixture_macros_app"));
    EXPECT_TRUE(runPrint("../../apps/make_fixture_macros/vespalib_make_fixture_macros_app 9 > macros.tmp"));

    const std::string srcDir = getenv("SOURCE_DIRECTORY") ? getenv("SOURCE_DIRECTORY") : ".";
    std::string diffCmd("diff -u ");
    diffCmd += srcDir;
    diffCmd += "/../../vespa/vespalib/testkit/generated_fixture_macros.h macros.tmp";
    EXPECT_TRUE(runPrint(diffCmd.c_str()));
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
