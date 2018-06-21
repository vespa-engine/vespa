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
#include <setjmp.h>
#include <dlfcn.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("ioerrorhandler_test");

extern "C" {

ssize_t read(int fd, void *buf, size_t count);
ssize_t write(int fd, const void *buf, size_t count);
ssize_t pread(int fd, void *buf, size_t count, off_t offset);
ssize_t pwrite(int fd, const void *buf, size_t count, off_t offset);

}

using ReadFunc = ssize_t (*)(int fd, void *buf, size_t count);
using WriteFunc = ssize_t (*)(int fd, const void *buf, size_t count);
using PreadFunc = ssize_t (*)(int fd, void *buf, size_t count, off_t offset);
using PwriteFunc = ssize_t (*)(int fd, const void *buf, size_t count, off_t offset);

using namespace search::test::statefile;
using namespace search::test::statestring;

namespace {

ReadFunc libc_read;
WriteFunc libc_write;
PreadFunc libc_pread;
PwriteFunc libc_pwrite;

}

int injectErrno;
std::atomic<int> injectreadErrnoTrigger;
std::atomic<int> injectpreadErrnoTrigger;
std::atomic<int> injectwriteErrnoTrigger;
std::atomic<int> injectpwriteErrnoTrigger;

ssize_t read(int fd, void *buf, size_t count)
{
    if (--injectreadErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    if (!libc_read) {
        libc_read = reinterpret_cast<ReadFunc>(dlsym(RTLD_NEXT, "read"));
    }
    return libc_read(fd, buf, count);
}

ssize_t write(int fd, const void *buf, size_t count)
{
    if (--injectwriteErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    if (!libc_write) {
        libc_write = reinterpret_cast<WriteFunc>(dlsym(RTLD_NEXT, "write"));
    }
    return libc_write(fd, buf, count);
}

ssize_t pread(int fd, void *buf, size_t count, off_t offset)
{
    if (--injectpreadErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    if (!libc_pread) {
        libc_pread = reinterpret_cast<PreadFunc>(dlsym(RTLD_NEXT, "pread"));
    }
    return libc_pread(fd, buf, count, offset);
}


ssize_t pwrite(int fd, const void *buf, size_t count, off_t offset)
{
    if (--injectpwriteErrnoTrigger == 0) {
        errno = injectErrno;
        return -1;
    }
    if (!libc_pwrite) {
        libc_pwrite = reinterpret_cast<PwriteFunc>(dlsym(RTLD_NEXT, "pwrite"));
    }
    return libc_pwrite(fd, buf, count, offset);
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
    TEST_RUN_ALL();
    search::StateFile::erase("state");
    unlink("testfile");
}
