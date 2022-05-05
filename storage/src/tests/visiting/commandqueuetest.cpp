// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/visiting/commandqueue.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::string;
using document::test::makeBucketSpace;
using namespace ::testing;

namespace storage {

namespace {

std::shared_ptr<api::CreateVisitorCommand> getCommand(
        vespalib::stringref name, vespalib::duration timeout,
        uint8_t priority = 0)
{
    vespalib::asciistream ost;
    ost << name << " t=" << vespalib::count_ms(timeout) << " p=" << static_cast<unsigned int>(priority);
    // Piggyback name in document selection
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "", "", ost.str());
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

TEST(CommandQueueTest, fifo) {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    ASSERT_TRUE(queue.empty());
    // Use all default priorities, meaning what comes out should be in the same order
    // as what went in
    queue.add(getCommand("first",     1ms));
    queue.add(getCommand("second",   10ms));
    queue.add(getCommand("third",     5ms));
    queue.add(getCommand("fourth",    0ms));
    queue.add(getCommand("fifth",     3ms));
    queue.add(getCommand("sixth",    14ms));
    queue.add(getCommand("seventh",   7ms));

    ASSERT_FALSE(queue.empty());
    EXPECT_EQ(7, queue.size());
    EXPECT_EQ(7, queue.relaxed_atomic_size());
    std::vector<std::shared_ptr<api::CreateVisitorCommand>> commands;
    for (;;) {
        auto cmd = queue.releaseNextCommand().first;
        if (!cmd) {
            break;
        }
        commands.emplace_back(std::move(cmd));
    }
    ASSERT_EQ(7, commands.size());
    EXPECT_EQ(0, queue.size());
    EXPECT_EQ(0, queue.relaxed_atomic_size());
    EXPECT_EQ("first t=1 p=0",   getCommandString(commands[0]));
    EXPECT_EQ("second t=10 p=0", getCommandString(commands[1]));
    EXPECT_EQ("third t=5 p=0",   getCommandString(commands[2]));
    EXPECT_EQ("fourth t=0 p=0",  getCommandString(commands[3]));
    EXPECT_EQ("fifth t=3 p=0",   getCommandString(commands[4]));
    EXPECT_EQ("sixth t=14 p=0",  getCommandString(commands[5]));
    EXPECT_EQ("seventh t=7 p=0", getCommandString(commands[6]));
}

TEST(CommandQueueTest, fifo_with_priorities) {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    ASSERT_TRUE(queue.empty());

    queue.add(getCommand("first",     1ms, 10));
    EXPECT_EQ("first t=1 p=10", getCommandString(queue.peekLowestPriorityCommand()));
    queue.add(getCommand("second",   10ms, 22));
    queue.add(getCommand("third",     5ms, 9));
    EXPECT_EQ("second t=10 p=22", getCommandString(queue.peekLowestPriorityCommand()));
    queue.add(getCommand("fourth",    0ms, 22));
    queue.add(getCommand("fifth",     3ms, 22));
    EXPECT_EQ("fifth t=3 p=22", getCommandString(queue.peekLowestPriorityCommand()));
    queue.add(getCommand("sixth",    14ms, 50));
    queue.add(getCommand("seventh",   7ms, 0));

    EXPECT_EQ("sixth t=14 p=50", getCommandString(queue.peekLowestPriorityCommand()));

    ASSERT_FALSE(queue.empty());
    EXPECT_EQ(7, queue.size());
    EXPECT_EQ(7, queue.relaxed_atomic_size());
    std::vector<std::shared_ptr<api::CreateVisitorCommand>> commands;
    for (;;) {
        auto cmdPeek = queue.peekNextCommand();
        auto cmd = queue.releaseNextCommand().first;
        if (!cmd || cmdPeek != cmd) {
            break;
        }
        commands.emplace_back(std::move(cmd));
    }
    ASSERT_EQ(7, commands.size());
    EXPECT_EQ(0, queue.size());
    EXPECT_EQ(0, queue.relaxed_atomic_size());
    EXPECT_EQ("seventh t=7 p=0",  getCommandString(commands[0]));
    EXPECT_EQ("third t=5 p=9",    getCommandString(commands[1]));
    EXPECT_EQ("first t=1 p=10",   getCommandString(commands[2]));
    EXPECT_EQ("second t=10 p=22", getCommandString(commands[3]));
    EXPECT_EQ("fourth t=0 p=22",  getCommandString(commands[4]));
    EXPECT_EQ("fifth t=3 p=22",   getCommandString(commands[5]));
    EXPECT_EQ("sixth t=14 p=50",  getCommandString(commands[6]));
}

TEST(CommandQueueTest, release_oldest) {
    framework::defaultimplementation::FakeClock clock(framework::defaultimplementation::FakeClock::FAKE_ABSOLUTE);
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    ASSERT_TRUE(queue.empty());
    queue.add(getCommand("first",    10ms));
    queue.add(getCommand("second",  100ms));
    queue.add(getCommand("third",  1000ms));
    queue.add(getCommand("fourth",    5ms));
    queue.add(getCommand("fifth",  3000ms));
    queue.add(getCommand("sixth",   400ms));
    queue.add(getCommand("seventh", 700ms));
    ASSERT_EQ(7u, queue.size());

    auto timedOut = queue.releaseTimedOut();
    ASSERT_TRUE(timedOut.empty());
    clock.addMilliSecondsToTime(400);
    timedOut = queue.releaseTimedOut();
    ASSERT_EQ(4, timedOut.size());
    std::ostringstream ost;
    for (const auto& timed_out_entry : timedOut) {
        ost << getCommandString(timed_out_entry._command) << "\n";
    }
    EXPECT_EQ("fourth t=5 p=0\n"
              "first t=10 p=0\n"
              "second t=100 p=0\n"
              "sixth t=400 p=0\n", ost.str());
    EXPECT_EQ(3u, queue.size());
    EXPECT_EQ(3u, queue.relaxed_atomic_size());
}

TEST(CommandQueueTest, release_lowest_priority) {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    ASSERT_TRUE(queue.empty());

    queue.add(getCommand("first",     1ms, 10));
    queue.add(getCommand("second",   10ms, 22));
    queue.add(getCommand("third",     5ms, 9));
    queue.add(getCommand("fourth",    0ms, 22));
    queue.add(getCommand("fifth",     3ms, 22));
    queue.add(getCommand("sixth",    14ms, 50));
    queue.add(getCommand("seventh",   7ms, 0));
    ASSERT_EQ(7u, queue.size());
    EXPECT_EQ(7u, queue.relaxed_atomic_size());

    std::vector<std::shared_ptr<api::CreateVisitorCommand>> commands;
    for (;;) {
        auto cmdPeek = queue.peekLowestPriorityCommand();
        auto cmd = queue.releaseLowestPriorityCommand().first;
        if (!cmd || cmdPeek != cmd) {
            break;
        }
        commands.emplace_back(std::move(cmd));
    }
    ASSERT_EQ(7, commands.size());
    EXPECT_EQ("sixth t=14 p=50",  getCommandString(commands[0]));
    EXPECT_EQ("fifth t=3 p=22",   getCommandString(commands[1]));
    EXPECT_EQ("fourth t=0 p=22",  getCommandString(commands[2]));
    EXPECT_EQ("second t=10 p=22", getCommandString(commands[3]));
    EXPECT_EQ("first t=1 p=10",   getCommandString(commands[4]));
    EXPECT_EQ("third t=5 p=9",    getCommandString(commands[5]));
    EXPECT_EQ("seventh t=7 p=0",  getCommandString(commands[6]));
}

TEST(CommandQueueTest, delete_iterator) {
    framework::defaultimplementation::FakeClock clock;
    CommandQueue<api::CreateVisitorCommand> queue(clock);
    ASSERT_TRUE(queue.empty());
    queue.add(getCommand("first",    10ms));
    queue.add(getCommand("second",  100ms));
    queue.add(getCommand("third",  1000ms));
    queue.add(getCommand("fourth",    5ms));
    queue.add(getCommand("fifth",  3000ms));
    queue.add(getCommand("sixth",   400ms));
    queue.add(getCommand("seventh", 700ms));
    ASSERT_EQ(7u, queue.size());

    auto it = queue.begin();
    ++it; ++it;
    queue.erase(it);
    ASSERT_EQ(6u, queue.size());
    ASSERT_EQ(6u, queue.relaxed_atomic_size());

    std::vector<std::shared_ptr<api::CreateVisitorCommand>> cmds;
    for (;;) {
        auto cmd = queue.releaseNextCommand().first;
        if (!cmd) {
            break;
        }
        cmds.emplace_back(std::move(cmd));
    }
    ASSERT_EQ(6, cmds.size());
    EXPECT_EQ("first t=10 p=0",    getCommandString(cmds[0]));
    EXPECT_EQ("second t=100 p=0",  getCommandString(cmds[1]));
    EXPECT_EQ("fourth t=5 p=0",    getCommandString(cmds[2]));
    EXPECT_EQ("fifth t=3000 p=0",  getCommandString(cmds[3]));
    EXPECT_EQ("sixth t=400 p=0",   getCommandString(cmds[4]));
    EXPECT_EQ("seventh t=700 p=0", getCommandString(cmds[5]));
}

}

