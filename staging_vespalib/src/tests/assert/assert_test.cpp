// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/slaveproc.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastos/file.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vespa/defaults.h>

using namespace vespalib;

TEST("that it borks the first time.") {
    vespalib::string assertName = make_string("tmp/myassert.assert.%s", vespa::Defaults::vespaUser());
    FastOS_File::EmptyAndRemoveDirectory("tmp");
    ASSERT_EQUAL(0, mkdir("tmp", 0755));
    {
        SlaveProc proc("ulimit -c 0 && exec env VESPA_HOME=./ ./staging_vespalib_asserter_app myassert 10000");
        proc.wait();
        ASSERT_EQUAL(proc.getExitCode() & 0x7f, 6);
    }
    {
        SlaveProc proc("ulimit -c 0 && exec env VESPA_HOME=./ ./staging_vespalib_asserter_app myassert 10000");
        proc.wait();
        ASSERT_EQUAL(proc.getExitCode() & 0x7f, 0);
    }
    ASSERT_EQUAL(0, unlink(assertName.c_str()));
    ASSERT_EQUAL(0, rmdir("tmp"));
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
