// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/child_process.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/assert.h>
#include <vespa/vespalib/io/fileutil.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vespa/defaults.h>

using namespace vespalib;

TEST("that it borks the first time.") {
    std::string assertName;
    const char * assertDir = "var/db/vespa/tmp";
    vespalib::rmdir("var", true);
    ASSERT_TRUE(vespalib::mkdir(assertDir, true));
    {
        ChildProcess proc("ulimit -c 0 && exec env VESPA_HOME=./ ./vespalib_asserter_app myassert 10000");
        proc.wait();
        ASSERT_EQUAL(proc.getExitCode() & 0x7f, 6);
    }
    {
        ChildProcess proc("ulimit -c 0 && exec env VESPA_HOME=./ ./vespalib_asserter_app myassert 10000");
        proc.readLine(assertName);
        proc.wait();
        ASSERT_EQUAL(proc.getExitCode() & 0x7f, 0);
    }
    ASSERT_EQUAL(0, unlink(assertName.c_str()));
    {
        ChildProcess proc("ulimit -c 0 && exec env VESPA_HOME=./ ./vespalib_asserter_app myassert 10000");
        proc.wait();
        ASSERT_EQUAL(proc.getExitCode() & 0x7f, 6);
    }
    ASSERT_EQUAL(0, unlink(assertName.c_str()));
    ASSERT_TRUE(vespalib::rmdir("var", true));
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
