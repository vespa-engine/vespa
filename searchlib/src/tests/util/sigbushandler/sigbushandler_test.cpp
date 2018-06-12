// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/util/statefile.h>
#include <vespa/searchlib/util/sigbushandler.h>
#include <iostream>
#include <fstream>
#include <vespa/searchlib/test/statefile.h>
#include <vespa/searchlib/test/statestring.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>

#include <vespa/log/log.h>
LOG_SETUP("sigbushandler_test");

using namespace search::test::statefile;
using namespace search::test::statestring;

namespace search
{

using strvec = std::vector<vespalib::string>;

namespace
{

bool
assertHistory(std::vector<vespalib::string> &exp,
              std::vector<vespalib::string> &act)
{
    if (!EXPECT_EQUAL(exp.size(), act.size())) {
        return false;
    }
    for (size_t i = 0; i < exp.size(); ++i) {
        if (!EXPECT_EQUAL(exp[i], act[i])) {
            return false;
        }
    }
    return true;
}

}


TEST("Test that sigbus handler can be instantated")
{
    StateFile::erase("state");
    StateFile sf("state");
    SigBusHandler sbh(&sf);
    EXPECT_FALSE(sbh.fired());
}


TEST("Test that sigbus handler can trap synthetic sigbus")
{
    StateFile::erase("state");
    StateFile sf("state");
    SigBusHandler sbh(&sf);
    EXPECT_FALSE(sbh.fired());
    sigjmp_buf sjb;
    if (sigsetjmp(sjb, 1) == 0) {
        sbh.setUnwind(&sjb);
        kill(getpid(), SIGBUS);
        LOG(error, "Should never get here");
        LOG_ABORT("should not be reached");
    }
    EXPECT_TRUE(sbh.fired());
    {
        vespalib::string act = readState(sf);
        normalizeTimestamp(act);
        EXPECT_EQUAL("state=down ts=0.0 operation=sigbus errno=0 code=0\n",
                     act);
    }
    {
        strvec exp({"state=down ts=0.0 operation=sigbus errno=0 code=0\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        normalizeTimestamps(act);
        TEST_DO(assertHistory(exp, act));
    }
}

TEST("Test that sigbus handler can trap normal sigbus")
{
    StateFile::erase("state");
    StateFile sf("state");
    SigBusHandler sbh(&sf);
    EXPECT_FALSE(sbh.fired());

    int fd = open("mmapfile", O_CREAT | O_TRUNC | O_RDWR, 0644);
    assert(fd >= 0);
    void *mmapres = mmap(nullptr, 4096, PROT_READ | PROT_WRITE,
                         MAP_SHARED, fd, 0);
    assert(mmapres != nullptr);
    assert(mmapres != reinterpret_cast<void *>(-1l));
    char *p = reinterpret_cast<char *>(mmapres) + 42;
    volatile char r = 0;
    sigjmp_buf sjb;
    if (sigsetjmp(sjb, 1) == 0) {
        sbh.setUnwind(&sjb);
        r = *p;
        LOG(error, "Should never get here");
        LOG_ABORT("should not be reached");
    }
    EXPECT_TRUE(sbh.fired());
    EXPECT_TRUE(r == '\0');
    {
        vespalib::string act = readState(sf);
        vespalib::string exp ="state=down ts=0.0 operation=sigbus errno=0 "
                         "code=2 addr=0x0000000000000000\n";
        normalizeAddr(exp, p);
        normalizeTimestamp(act);
        EXPECT_EQUAL(exp, act);
    }
    {
        strvec exp({"state=down ts=0.0 operation=sigbus errno=0 code=2 "
                            "addr=0x0000000000000000\n" });
        normalizeAddrs(exp, p);
        std::vector<vespalib::string> act(readHistory("state.history"));
        normalizeTimestamps(act);
        TEST_DO(assertHistory(exp, act));
    }
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
    search::StateFile::erase("state");
    unlink("mmapfile");
}
