// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/visiting/commandqueue.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/test/make_bucket_space.h>


using vespalib::string;
using document::test::makeBucketSpace;

namespace storage {

struct CommandQueueTest : public CppUnit::TestFixture
{
    void testFIFO();
    void testFIFOWithPriorities();
    void testReleaseOldest();
    void testReleaseLowestPriority();
    void testDeleteIterator();

    CPPUNIT_TEST_SUITE(CommandQueueTest);
    CPPUNIT_TEST(testFIFO);
    CPPUNIT_TEST(testFIFOWithPriorities);
    CPPUNIT_TEST(testReleaseOldest);
    CPPUNIT_TEST(testReleaseLowestPriority);
    CPPUNIT_TEST(testDeleteIterator);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(CommandQueueTest);

namespace {
    std::shared_ptr<api::CreateVisitorCommand> getCommand(
            vespalib::stringref name, int timeout,
            uint8_t priority = 0)
    {
        vespalib::asciistream ost;
        ost << name << " t=" << timeout << " p=" << static_cast<unsigned int>(priority);
        // Piggyback name in document selection
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "", "", ost.str()));
        cmd->setQueueTimeout(timeout);
        cmd->setPriority(priority);
        return cmd;
    }

    const vespalib::string &
    getCommandString(const std::shared_ptr<api::CreateVisitorCommand>& cmd)
    {
        return cmd->getDocumentSelection();
    }

}

void CommandQueueTest::testFIFO() {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    CPPUNIT_ASSERT(queue.empty());
    // Use all default priorities, meaning what comes out should be in the same order
    // as what went in
    queue.add(getCommand("first",     1));
    queue.add(getCommand("second",   10));
    queue.add(getCommand("third",     5));
    queue.add(getCommand("fourth",    0));
    queue.add(getCommand("fifth",     3));
    queue.add(getCommand("sixth",    14));
    queue.add(getCommand("seventh",   7));

    CPPUNIT_ASSERT(!queue.empty());
    std::vector<std::shared_ptr<api::CreateVisitorCommand> > commands;
    for (;;) {
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                queue.releaseNextCommand().first);
        if (cmd.get() == 0) break;
        commands.push_back(cmd);
    }
    CPPUNIT_ASSERT_EQUAL(size_t(7), commands.size());
    CPPUNIT_ASSERT_EQUAL(string("first t=1 p=0"),   getCommandString(commands[0]));
    CPPUNIT_ASSERT_EQUAL(string("second t=10 p=0"), getCommandString(commands[1]));
    CPPUNIT_ASSERT_EQUAL(string("third t=5 p=0"),   getCommandString(commands[2]));
    CPPUNIT_ASSERT_EQUAL(string("fourth t=0 p=0"),  getCommandString(commands[3]));
    CPPUNIT_ASSERT_EQUAL(string("fifth t=3 p=0"),   getCommandString(commands[4]));
    CPPUNIT_ASSERT_EQUAL(string("sixth t=14 p=0"),  getCommandString(commands[5]));
    CPPUNIT_ASSERT_EQUAL(string("seventh t=7 p=0"), getCommandString(commands[6]));
}

void CommandQueueTest::testFIFOWithPriorities() {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    CPPUNIT_ASSERT(queue.empty());

    queue.add(getCommand("first",     1, 10));
    CPPUNIT_ASSERT_EQUAL(string("first t=1 p=10"), getCommandString(queue.peekLowestPriorityCommand()));
    queue.add(getCommand("second",   10, 22));
    queue.add(getCommand("third",     5, 9));
    CPPUNIT_ASSERT_EQUAL(string("second t=10 p=22"), getCommandString(queue.peekLowestPriorityCommand()));
    queue.add(getCommand("fourth",    0, 22));
    queue.add(getCommand("fifth",     3, 22));
    CPPUNIT_ASSERT_EQUAL(string("fifth t=3 p=22"), getCommandString(queue.peekLowestPriorityCommand()));
    queue.add(getCommand("sixth",    14, 50));
    queue.add(getCommand("seventh",   7, 0));

    CPPUNIT_ASSERT_EQUAL(string("sixth t=14 p=50"), getCommandString(queue.peekLowestPriorityCommand()));

    CPPUNIT_ASSERT(!queue.empty());
    std::vector<std::shared_ptr<api::CreateVisitorCommand> > commands;
    for (;;) {
        std::shared_ptr<api::CreateVisitorCommand> cmdPeek(queue.peekNextCommand());
        std::shared_ptr<api::CreateVisitorCommand> cmd(queue.releaseNextCommand().first);
        if (cmd.get() == 0 || cmdPeek != cmd) break;
        commands.push_back(cmd);
    }
    CPPUNIT_ASSERT_EQUAL(size_t(7), commands.size());
    CPPUNIT_ASSERT_EQUAL(string("seventh t=7 p=0"), getCommandString(commands[0]));
    CPPUNIT_ASSERT_EQUAL(string("third t=5 p=9"),   getCommandString(commands[1]));
    CPPUNIT_ASSERT_EQUAL(string("first t=1 p=10"),   getCommandString(commands[2]));
    CPPUNIT_ASSERT_EQUAL(string("second t=10 p=22"), getCommandString(commands[3]));
    CPPUNIT_ASSERT_EQUAL(string("fourth t=0 p=22"),  getCommandString(commands[4]));
    CPPUNIT_ASSERT_EQUAL(string("fifth t=3 p=22"),   getCommandString(commands[5]));
    CPPUNIT_ASSERT_EQUAL(string("sixth t=14 p=50"),  getCommandString(commands[6]));
}

void CommandQueueTest::testReleaseOldest() {
    framework::defaultimplementation::FakeClock clock(framework::defaultimplementation::FakeClock::FAKE_ABSOLUTE);
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    CPPUNIT_ASSERT(queue.empty());
    queue.add(getCommand("first",    10));
    queue.add(getCommand("second",  100));
    queue.add(getCommand("third",  1000));
    queue.add(getCommand("fourth",    5));
    queue.add(getCommand("fifth",  3000));
    queue.add(getCommand("sixth",   400));
    queue.add(getCommand("seventh", 700));
    CPPUNIT_ASSERT_EQUAL(7u, queue.size());

    typedef CommandQueue<api::CreateVisitorCommand>::CommandEntry CommandEntry;
    std::list<CommandEntry> timedOut(queue.releaseTimedOut());
    CPPUNIT_ASSERT(timedOut.empty());
    clock.addMilliSecondsToTime(400 * 1000);
    timedOut = queue.releaseTimedOut();
    CPPUNIT_ASSERT_EQUAL(size_t(4), timedOut.size());
    std::ostringstream ost;
    for (std::list<CommandEntry>::const_iterator it = timedOut.begin();
         it != timedOut.end(); ++it)
    {
        ost << getCommandString(it->_command) << "\n";
    }
    CPPUNIT_ASSERT_EQUAL(std::string(
                "fourth t=5 p=0\n"
                "first t=10 p=0\n"
                "second t=100 p=0\n"
                "sixth t=400 p=0\n"), ost.str());
    CPPUNIT_ASSERT_EQUAL(3u, queue.size());
}

void CommandQueueTest::testReleaseLowestPriority() {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    CPPUNIT_ASSERT(queue.empty());

    queue.add(getCommand("first",     1, 10));
    queue.add(getCommand("second",   10, 22));
    queue.add(getCommand("third",     5, 9));
    queue.add(getCommand("fourth",    0, 22));
    queue.add(getCommand("fifth",     3, 22));
    queue.add(getCommand("sixth",    14, 50));
    queue.add(getCommand("seventh",   7, 0));
    CPPUNIT_ASSERT_EQUAL(7u, queue.size());

    std::vector<std::shared_ptr<api::CreateVisitorCommand> > commands;
    for (;;) {
        std::shared_ptr<api::CreateVisitorCommand> cmdPeek(queue.peekLowestPriorityCommand());
        std::pair<std::shared_ptr<api::CreateVisitorCommand>, uint64_t> cmd(
                queue.releaseLowestPriorityCommand());
        if (cmd.first.get() == 0 || cmdPeek != cmd.first) break;
        commands.push_back(cmd.first);
    }
    CPPUNIT_ASSERT_EQUAL(size_t(7), commands.size());
    CPPUNIT_ASSERT_EQUAL(string("sixth t=14 p=50"),  getCommandString(commands[0]));
    CPPUNIT_ASSERT_EQUAL(string("fifth t=3 p=22"),   getCommandString(commands[1]));
    CPPUNIT_ASSERT_EQUAL(string("fourth t=0 p=22"),  getCommandString(commands[2]));
    CPPUNIT_ASSERT_EQUAL(string("second t=10 p=22"), getCommandString(commands[3]));
    CPPUNIT_ASSERT_EQUAL(string("first t=1 p=10"),   getCommandString(commands[4]));
    CPPUNIT_ASSERT_EQUAL(string("third t=5 p=9"),    getCommandString(commands[5]));
    CPPUNIT_ASSERT_EQUAL(string("seventh t=7 p=0"),  getCommandString(commands[6]));
}

void CommandQueueTest::testDeleteIterator() {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    CPPUNIT_ASSERT(queue.empty());
    queue.add(getCommand("first",    10));
    queue.add(getCommand("second",  100));
    queue.add(getCommand("third",  1000));
    queue.add(getCommand("fourth",    5));
    queue.add(getCommand("fifth",  3000));
    queue.add(getCommand("sixth",   400));
    queue.add(getCommand("seventh", 700));
    CPPUNIT_ASSERT_EQUAL(7u, queue.size());

    CommandQueue<api::CreateVisitorCommand>::iterator it = queue.begin();
    ++it; ++it;
    queue.erase(it);
    CPPUNIT_ASSERT_EQUAL(6u, queue.size());

    std::vector<std::shared_ptr<api::CreateVisitorCommand> > cmds;
    for (;;) {
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                std::dynamic_pointer_cast<api::CreateVisitorCommand>(
                    queue.releaseNextCommand().first));
        if (cmd.get() == 0) break;
        cmds.push_back(cmd);
    }
    CPPUNIT_ASSERT_EQUAL(size_t(6), cmds.size());
    CPPUNIT_ASSERT_EQUAL(string("first t=10 p=0"),    getCommandString(cmds[0]));
    CPPUNIT_ASSERT_EQUAL(string("second t=100 p=0"),  getCommandString(cmds[1]));
    CPPUNIT_ASSERT_EQUAL(string("fourth t=5 p=0"),    getCommandString(cmds[2]));
    CPPUNIT_ASSERT_EQUAL(string("fifth t=3000 p=0"),  getCommandString(cmds[3]));
    CPPUNIT_ASSERT_EQUAL(string("sixth t=400 p=0"),   getCommandString(cmds[4]));
    CPPUNIT_ASSERT_EQUAL(string("seventh t=700 p=0"), getCommandString(cmds[5]));
}

}

