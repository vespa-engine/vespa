// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/process/process.h>

using namespace vespalib;

bool runPrint(const vespalib::string &cmd) {
    vespalib::string out;
    bool res = Process::run(cmd, out);
    fprintf(stderr, "%s", out.c_str());
    return res;
}

TEST("make fixture macros") {
    EXPECT_FALSE(runPrint("../../apps/make_fixture_macros/vespalib_make_fixture_macros_app"));
    EXPECT_TRUE(runPrint("../../apps/make_fixture_macros/vespalib_make_fixture_macros_app 9 > macros.tmp"));

    vespalib::string diffCmd("diff -u ");
    diffCmd += TEST_PATH("../../vespa/vespalib/testkit/generated_fixture_macros.h macros.tmp");
    EXPECT_TRUE(runPrint(diffCmd.c_str()));
}

TEST_MAIN() { TEST_RUN_ALL(); }
