// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/util/statefile.h>
#include <vespa/searchlib/util/ioerrorhandler.h>
#include <vespa/searchlib/test/statefile.h>
#include <vespa/searchlib/test/statestring.h>
#include <vespa/fastos/file.h>
#include <atomic>
#include <iostream>
#include <fstream>
#include <string>
#include <unistd.h>
#include <vespa/fastos/file_rw_ops.h>

#include <vespa/log/log.h>
LOG_SETUP("ioerrorhandler_test");

using namespace search::test::statefile;
using namespace search::test::statestring;

int injectErrno;
std::atomic<int> injectreadErrnoTrigger;
std::atomic<int> injectpreadErrnoTrigger;
std::atomic<int> injectwriteErrnoTrigger;
std::atomic<int> injectpwriteErrnoTrigger;

ssize_t error_injecting_read(int fd, void* buf, size_t count)
{
    if (--injectreadErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    return read(fd, buf, count);
}

ssize_t error_injecting_write(int fd, const void* buf, size_t count)
{
    if (--injectwriteErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    return write(fd, buf, count);
}

ssize_t error_injecting_pread(int fd, void* buf, size_t count, off_t offset)
{
    if (--injectpreadErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    return pread(fd, buf, count, offset);
}


ssize_t error_injecting_pwrite(int fd, const void* buf, size_t count, off_t offset)
{
    if (--injectpwriteErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    return pwrite(fd, buf, count, offset);
}

void setup_error_injections()
{
    using Ops = fastos::File_RW_Ops;
    Ops::_read = error_injecting_read;
    Ops::_write = error_injecting_write;
    Ops::_pread = error_injecting_pread;
    Ops::_pwrite = error_injecting_pwrite;
}

namespace search
{

const char *testStringBase = "This is a test\n";

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


class Fixture
{
public:

    std::unique_ptr<StateFile> sf;
    std::unique_ptr<FastOS_File> file;
    char buf[8192];
    char *testString;

    Fixture();

    void openFile();

    void openFileDIO();

    void writeTestString();
};


Fixture::Fixture()
    : sf(),
      file()
{
    unlink("testfile");
    StateFile::erase("state");
    sf.reset(new StateFile("state"));
    testString = &buf[0];
    int off = reinterpret_cast<unsigned long>(testString) & 4095;
    if (off != 0) {
        testString += 4096 - off;
    }
    assert(testString + strlen(testStringBase) < &buf[0] + sizeof(buf));
    strcpy(testString, testStringBase);
}


void
Fixture::openFile()
{
    file.reset(new FastOS_File);
    file->OpenReadWrite("testfile");
}

void
Fixture::openFileDIO()
{
    file.reset(new FastOS_File);
    file->EnableDirectIO();
    file->OpenReadWrite("testfile");
}

void
Fixture::writeTestString()
{
    file->WriteBuf(testString, strlen(testString));
    file->SetPosition(0);
}


}


TEST("Test that ioerror handler can be instantated")
{
    StateFile::erase("state");
    StateFile sf("state");
    IOErrorHandler ioeh(&sf);
    EXPECT_FALSE(ioeh.fired());
}


TEST_F("Test that ioerror handler can process read error", Fixture)
{
    IOErrorHandler ioeh(f.sf.get());
    EXPECT_FALSE(ioeh.fired());
    f.openFile();
    f.writeTestString();
    uint64_t fileSize = f.file->GetSize();
    EXPECT_EQUAL(strlen(f.testString), fileSize);
    char buf[1024];
    assert(fileSize <= sizeof(buf));
    try {
        f.file->SetPosition(0);
        injectErrno = EIO;
        injectreadErrnoTrigger = 1;
        f.file->ReadBuf(buf, fileSize);
        LOG_ABORT("Should never get here");
    } catch (std::runtime_error &e) {
        LOG(info, "Caught std::runtime_error exception: %s", e.what());
        EXPECT_TRUE(strstr(e.what(), "Input/output error") != nullptr);
    }
    EXPECT_TRUE(ioeh.fired());
    {
        vespalib::string act = readState(*f.sf);
        normalizeTimestamp(act);
        vespalib::string exp = "state=down ts=0.0 operation=read "
                               "file=testfile error=5 offset=0 len=15 "
                               "rlen=-1\n";
        EXPECT_EQUAL(exp, act);
    }
    {
        strvec exp({ "state=down ts=0.0 operation=read "
                            "file=testfile error=5 offset=0 len=15 "
                            "rlen=-1\n"});
        std::vector<vespalib::string> act(readHistory("state.history"));
        normalizeTimestamps(act);
        TEST_DO(assertHistory(exp, act));
    }
}

TEST_F("Test that ioerror handler can process pread error", Fixture)
{
    IOErrorHandler ioeh(f.sf.get());
    EXPECT_FALSE(ioeh.fired());
    f.openFile();
    f.writeTestString();
    uint64_t fileSize = f.file->GetSize();
    EXPECT_EQUAL(strlen(f.testString), fileSize);
    char buf[1024];
    assert(fileSize <= sizeof(buf));
    try {
        f.file->SetPosition(0);
        injectErrno = EIO;
        injectpreadErrnoTrigger = 1;
        f.file->ReadBuf(buf, fileSize, 0);
        LOG_ABORT("Should never get here");
    } catch (std::runtime_error &e) {
        LOG(info, "Caught std::runtime_error exception: %s", e.what());
        EXPECT_TRUE(strstr(e.what(), "Input/output error") != nullptr);
    }
    EXPECT_TRUE(ioeh.fired());
    {
        vespalib::string act = readState(*f.sf);
        normalizeTimestamp(act);
        vespalib::string exp = "state=down ts=0.0 operation=read "
                               "file=testfile error=5 offset=0 len=15 "
                               "rlen=-1\n";
        EXPECT_EQUAL(exp, act);
    }
    {
        strvec exp({ "state=down ts=0.0 operation=read "
                            "file=testfile error=5 offset=0 len=15 "
                            "rlen=-1\n"});
        std::vector<vespalib::string> act(readHistory("state.history"));
        normalizeTimestamps(act);
        TEST_DO(assertHistory(exp, act));
    }
}

TEST_F("Test that ioerror handler can process write error", Fixture)
{
    IOErrorHandler ioeh(f.sf.get());
    EXPECT_FALSE(ioeh.fired());
    f.openFile();
    try {
        injectErrno = EIO;
        injectwriteErrnoTrigger = 1;
        f.writeTestString();
        LOG_ABORT("Should never get here");
    } catch (std::runtime_error &e) {
        LOG(info, "Caught std::runtime_error exception: %s", e.what());
        EXPECT_TRUE(strstr(e.what(), "Input/output error") != nullptr);
    }
    EXPECT_TRUE(ioeh.fired());
    {
        vespalib::string act = readState(*f.sf);
        normalizeTimestamp(act);
        vespalib::string exp = "state=down ts=0.0 operation=write "
                               "file=testfile error=5 offset=0 len=15 "
                               "rlen=-1\n";
        EXPECT_EQUAL(exp, act);
    }
    {
        strvec exp({ "state=down ts=0.0 operation=write "
                            "file=testfile error=5 offset=0 len=15 "
                            "rlen=-1\n"});
        std::vector<vespalib::string> act(readHistory("state.history"));
        normalizeTimestamps(act);
        TEST_DO(assertHistory(exp, act));
    }
}


TEST_F("Test that ioerror handler can process pwrite error", Fixture)
{
    IOErrorHandler ioeh(f.sf.get());
    EXPECT_FALSE(ioeh.fired());
    f.openFileDIO();
    try {
        injectErrno = EIO;
        injectpwriteErrnoTrigger = 1;
        f.writeTestString();
        LOG_ABORT("Should never get here");
    } catch (std::runtime_error &e) {
        LOG(info, "Caught std::runtime_error exception: %s", e.what());
        EXPECT_TRUE(strstr(e.what(), "Input/output error") != nullptr);
    }
    EXPECT_TRUE(ioeh.fired());
    {
        vespalib::string act = readState(*f.sf);
        normalizeTimestamp(act);
        vespalib::string exp = "state=down ts=0.0 operation=write "
                               "file=testfile error=5 offset=0 len=15 "
                               "rlen=-1\n";
        EXPECT_EQUAL(exp, act);
    }
    {
        strvec exp({ "state=down ts=0.0 operation=write "
                            "file=testfile error=5 offset=0 len=15 "
                            "rlen=-1\n"});
        std::vector<vespalib::string> act(readHistory("state.history"));
        normalizeTimestamps(act);
        TEST_DO(assertHistory(exp, act));
    }
}

}

TEST_MAIN()
{
    setup_error_injections();
    TEST_RUN_ALL();
    search::StateFile::erase("state");
    unlink("testfile");
}
