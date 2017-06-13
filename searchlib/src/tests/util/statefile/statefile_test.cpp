// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("statefile_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/util/statefile.h>
#include <atomic>
#include <iostream>
#include <fstream>
#include <string>
#include <vespa/searchlib/test/statefile.h>


using namespace search::test::statefile;

namespace search
{

namespace
{

bool
hasFile(const char *name)
{
    return access(name, R_OK | W_OK) == 0;
}


void
addState(StateFile &sf, const char *buf)
{
    size_t bufLen = strlen(buf);
    sf.addState(buf, bufLen, false);
}

void
addSignalState(StateFile &sf, const char *buf)
{
    size_t bufLen = strlen(buf);
    sf.addState(buf, bufLen, true);
}


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


int64_t
getSize(const char *name)
{
    struct stat stbuf;
    if (stat(name, &stbuf) != 0)
        return 0;
    return stbuf.st_size;
}


void
setSize(const char *name, int64_t newSize)
{
    int truncRes = truncate(name, newSize);
    (void) truncRes;
    assert(truncRes == 0);
}


}


TEST("Test lock free atomic int used by async signal safe lock primitive")
{
    std::atomic<int> f;
    ASSERT_TRUE(f.is_lock_free());
}


TEST("Test that statefile can be created")
{
    StateFile::erase("state");
    EXPECT_FALSE(hasFile("state"));
    EXPECT_FALSE(hasFile("state.history"));
    StateFile sf("state");
    EXPECT_TRUE(hasFile("state"));
    EXPECT_TRUE(hasFile("state.history"));
    EXPECT_EQUAL(0, sf.getGen());
    StateFile::erase("state");
    EXPECT_FALSE(hasFile("state"));
    EXPECT_FALSE(hasFile("state.history"));
    StateFile::erase("state");
    EXPECT_FALSE(hasFile("state"));
    EXPECT_FALSE(hasFile("state.history"));
}


TEST("Test that statefile can add event")
{
    StateFile::erase("state");
    StateFile sf("state");

    addState(sf, "Hello world\n");
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("Hello world\n", check);
    EXPECT_EQUAL(1, sf.getGen());
}

TEST("Test that history is appended to")
{
    StateFile::erase("state");
    StateFile sf("state");

    addState(sf, "Hello world\n");
    addState(sf, "Foo bar\n");
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("Foo bar\n", check);
    EXPECT_EQUAL(2, sf.getGen());
    {
        std::vector<vespalib::string> exp({ "Hello world\n", "Foo bar\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }
}


TEST("Test that truncated history is truncated at event boundary")
{
    StateFile::erase("state");
    int64_t histSize = 1;
    {
        StateFile sf("state");
        addState(sf, "Hello world\n");
        addState(sf, "Foo bar\n");
        EXPECT_EQUAL(2, sf.getGen());
        histSize = getSize("state.history");
        EXPECT_EQUAL(20, histSize);
        addState(sf, "zap\n");
        EXPECT_EQUAL(3, sf.getGen());
    }
    // Lose 2 last events in history
    setSize("state.history", histSize - 1);
    // Last event is restored to history from main state file
    StateFile sf("state");
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("zap\n", check);
    EXPECT_EQUAL(0, sf.getGen());
    {
        std::vector<vespalib::string> exp({ "Hello world\n", "zap\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }
}


TEST("Test that async signal safe path adds event")
{
    StateFile::erase("state");
    StateFile sf("state");

    addSignalState(sf, "Hello world\n");
    addSignalState(sf, "Foo bar\n");
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("Foo bar\n", check);
    EXPECT_EQUAL(2, sf.getGen());
    {
        std::vector<vespalib::string> exp({ "Hello world\n", "Foo bar\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }
}


TEST("Test that state file can be restored from history")
{
    StateFile::erase("state");
    {
        StateFile sf("state");
        addState(sf, "Hello world\n");
        addState(sf, "Foo bar\n");
        EXPECT_EQUAL(2, sf.getGen());
    }
    // Lose event in main state file
    setSize("state", 0);
    EXPECT_EQUAL(0, getSize("state"));
    // Last event is restored to history from main state file
    StateFile sf("state");
    EXPECT_NOT_EQUAL(0, getSize("state"));
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("Foo bar\n", check);
    {
        std::vector<vespalib::string> exp({ "Hello world\n", "Foo bar\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }
}


TEST("Test that different entry is added to history")
{
    StateFile::erase("state");
    {
        StateFile sf("state");
        addState(sf, "Hello world\n");
        EXPECT_EQUAL(1, sf.getGen());
    }
    // Write changed entry to main state file
    {
        std::ofstream of("state");
        of << "zap\n";
    }
    // Add changed event to history
    StateFile sf("state");
    EXPECT_NOT_EQUAL(0, getSize("state"));
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("zap\n", check);
    {
        std::vector<vespalib::string> exp({ "Hello world\n", "zap\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }
}


TEST("Test that state history stops at NUL byte")
{
    StateFile::erase("state");
    {
        StateFile sf("state");
        addState(sf, "Hello world\n");
        addState(sf, "Foo bar\n");
        EXPECT_EQUAL(2, sf.getGen());
    }
    // Corrupt history state file
    {
        char buf[1];
        buf[0] = '\0';
        std::ofstream of("state.history");
        of.write(&buf[0], 1);
    }
    StateFile sf("state");
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("Foo bar\n", check);
    {
        std::vector<vespalib::string> exp({ "Foo bar\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }

}

TEST("Test that main state stops at NUL byte")
{
    StateFile::erase("state");
    {
        StateFile sf("state");
        addState(sf, "Hello world\n");
        addState(sf, "Foo bar\n");
        EXPECT_EQUAL(2, sf.getGen());
    }
    // Corrupt history state file
    {
        char buf[10];
        strcpy(buf, "zap");
        std::ofstream of("state");
        of.write(&buf[0], strlen(buf) + 1);
    }
    StateFile sf("state");
    vespalib::string check = readState(sf);
    EXPECT_EQUAL("Foo bar\n", check);
    {
        std::vector<vespalib::string> exp({ "Hello world\n", "Foo bar\n" });
        std::vector<vespalib::string> act(readHistory("state.history"));
        TEST_DO(assertHistory(exp, act));
    }

}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
    search::StateFile::erase("state");
}
