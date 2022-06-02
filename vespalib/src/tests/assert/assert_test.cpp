// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/assert.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vespa/defaults.h>
#include <filesystem>

using namespace vespalib;

TEST("that it borks the first time.") {
    std::string assertName;
    const char * assertDir = "var/db/vespa/tmp";
    std::filesystem::remove_all(std::filesystem::path("var"));
    ASSERT_TRUE(std::filesystem::create_directories(std::filesystem::path(assertDir)));
    {
        Process proc("ulimit -c 0 && exec env VESPA_HOME=./ ./vespalib_asserter_app myassert 10000");
        ASSERT_EQUAL(proc.join() & 0x7f, 6);
    }
    {
        Process proc("ulimit -c 0 && exec env VESPA_HOME=./ ./vespalib_asserter_app myassert 10000");
        assertName = proc.read_line();
        ASSERT_EQUAL(proc.join() & 0x7f, 0);
    }
    ASSERT_EQUAL(0, unlink(assertName.c_str()));
    {
        Process proc("ulimit -c 0 && exec env VESPA_HOME=./ ./vespalib_asserter_app myassert 10000");
        ASSERT_EQUAL(proc.join() & 0x7f, 6);
    }
    ASSERT_EQUAL(0, unlink(assertName.c_str()));
    ASSERT_LESS(0u, std::filesystem::remove_all(std::filesystem::path("var")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
