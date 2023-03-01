// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/bufferedlogger.h>
#include <vespa/log/internal.h>

#include "bufferedlogtest.logger1.h"
#include "bufferedlogtest.logger2.h"

#include <fstream>
#include <iostream>
#include <unistd.h>
#include <cstdlib>

LOG_SETUP("bufferedlogtest");

using namespace std::literals::chrono_literals;


/** Test timer returning just a given time. Used in tests to fake time. */
struct TestTimer : public ns_log::Timer {
    uint64_t & _time;
    TestTimer(uint64_t & timeVar) : _time(timeVar) { }
    ns_log::system_time getTimestamp() const noexcept override {
        return ns_log::system_time(std::chrono::microseconds(_time));
    }
};

std::string readFile(const std::string& file) {
    std::ostringstream ost;
    std::ifstream is(file.c_str());
    std::string line;
    while (std::getline(is, line)) {
        std::string::size_type pos = line.find('\t');
        if (pos == std::string::npos) continue;
        std::string::size_type pos2 = line.find('\t', pos + 1);
        if (pos2 == std::string::npos) continue;
        std::string result = line.substr(0, pos)
                           + "\tlocalhost"
                           + line.substr(pos2);
        ost << result << "\n";
    }
    return ost.str();
}

void spamLog1(uint64_t& time, int diff) {
    for (int i=0; i<100; ++i) {
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: NOT CONNECTED");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: NOT_READY");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: BAAAH");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: RPC FAILURE");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: COSMIC RADIATION");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: ITS SATURDAY");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: Yeah, Right!!");
        time += diff;
        LOGBT(error,   "Failed to send to node 4",
                       "Failed to send message to node 4: NOT CONNECTED");
        time += diff;
        LOGBT(spam,    "Failed to send to node 4",
                       "Failed to send message to node 4: NOT CONNECTED");
        time += diff;
        LOGBT(warning, "Failed to send to node 4",
                       "Failed to send message to node 4: NOT CONNECTED");
        time += diff * 10;
        LOGBT(warning, "Failed to send to node 3",
                       "Failed to send message to node 3: NOT CONNECTED");
    }
}

void spamLog2(uint64_t& time, int diff) {
    for (int i=0; i<100; ++i) {
        time += diff;
        std::ostringstream ost;
        ost << "Message " << i;
        LOGBT(info, ost.str(), "%s", ost.str().c_str());
    }
}

void testThatEntriesWithHighCountIsKept(const std::string& file, uint64_t& timer)
{
    std::cerr << "testThatEntriesWithHighCountIsKept ...\n";
    timer = 10 * 1000000 + 4;

    LOGBM(info, "Starting up, using logfile %s", file.c_str());
    timer = 100 * 1000000 + 4;
    LOGBT(warning, "Token", "message");

    spamLog1(timer, 1);
    spamLog2(timer, 1);
    spamLog1(timer, 1);

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.highcountkept.log"));

    if (result != expected) {
        std::cerr << "Failed testThatEntriesWithHighCountIsKept\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file + " bufferedlogtest.highcountkept.log")
                .c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void testThatEntriesWithHighCountsAreEventuallyRemoved(
        const std::string& file, uint64_t& timer)
{
    std::cerr << "testThatEntriesWithHighCountsAreEventuallyRemoved ...\n";
        // Same as above, just increase timer more between each log entry.
        // Should eventually throw out the entries with high count
    timer = 10 * 1000000 + 4;
        // Make sure we don't remove due to age.
    ns_log::BufferedLogger::instance().setMaxEntryAge(1000000);
        // Let each count, count for 5 seconds.
    ns_log::BufferedLogger::instance().setCountFactor(5);

    LOGBM(info, "Starting up, using logfile %s", file.c_str());
    timer = 100 * 1000000 + 4;
    LOGBT(warning, "Token", "message");

    spamLog1(timer, 1);
    spamLog2(timer, 10 * 1000000);
    spamLog1(timer, 1);

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.highcountexpire.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testThatEntriesWithHighCountsAreEventuallyRemoved\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file + " bufferedlogtest.highcountexpire.log")
                .c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void testThatEntriesExpire(
        const std::string& file, uint64_t& timer)
{
    std::cerr << "testThatEntriesExpire ...\n";
        // Test that we don't keep entries longer than max age
    timer = 10 * 1000000 + 4;
        // Time out after 120 seconds
    ns_log::BufferedLogger::instance().setMaxEntryAge(120);
        // Let counts count much, so they expire due to time instead
    ns_log::BufferedLogger::instance().setCountFactor(100000);

    LOGBM(info, "Starting up, using logfile %s", file.c_str());
    timer = 100 * 1000000 + 4;
    LOGBT(warning, "Token", "message");

    spamLog1(timer, 1);
    spamLog2(timer, 10 * 1000000);
    spamLog1(timer, 1);

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.expire.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testThatEntriesExpire\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file + " bufferedlogtest.expire.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

// Spam 10+ different entries lots of times, to fill cache with high count
// entries
void spamLog3(uint64_t& time, int diff) {
    for (int i=0; i<100; ++i) {
        time += diff;
        LOGBT(warning, "Failed to talk to node 1",
                       "Failed to send message to node 0x1: NOT CONNECTED");
        time += diff;
        LOGBT(warning, "Failed to talk to node 2",
                       "Failed to send message to node 0x2: NOT_READY");
        time += diff;
        LOGBT(warning, "Failed to talk to node 3",
                       "Failed to send message to node 0x3: BAAAH");
        time += diff;
        LOGBT(warning, "Failed to talk to node 4",
                       "Failed to send message to node 0x4: RPC FAILURE");
        time += diff;
        LOGBT(warning, "Failed to talk to node 5",
                       "Failed to send message to node 0x5: COSMIC RADIATION");
        time += diff;
        LOGBT(warning, "Failed to talk to node 6",
                       "Failed to send message to node 0x6: ITS SATURDAY");
        time += diff;
        LOGBT(warning, "Failed to talk to node 7",
                       "Failed to send message to node 0x7: Yeah, Right!!");
        time += diff;
        LOGBT(error,   "Failed to talk to node 8",
                       "Failed to send message to node 0x8: NOT CONNECTED");
        time += diff;
        LOGBT(info,    "Failed to talk to node 9",
                       "Failed to send message to node 0x9: NOT CONNECTED");
        time += diff;
        LOGBT(warning, "Failed to talk to node 10",
                       "Failed to send message to node 0xa: NOT CONNECTED");
    }
}

void testThatHighCountEntriesDontStarveOthers(
        const std::string& file, uint64_t& timer)
{
    std::cerr << "testThatHighCountEntriesDontStarveOthers ...\n";
    timer = 10 * 1000000 + 4;
        // Long time out, we don't want to rely on timeout to prevent starvation
    ns_log::BufferedLogger::instance().setMaxEntryAge(12000000);
        // Let counts count much, so they score high
    ns_log::BufferedLogger::instance().setCountFactor(100000);

    LOGBM(info, "Starting up, using logfile %s", file.c_str());
    timer = 100 * 1000000;
    LOGBT(warning, "Token", "message");

    spamLog3(timer, 1);
    spamLog1(timer, 1);

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.nostarve.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testThatHighCountEntriesDontStarveOthers\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file + " bufferedlogtest.nostarve.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void testNoTokenMatchAcrossComponents(const std::string& file,
                                      uint64_t& timer)
{
    std::cerr << "testNoTokenMatchAcrossComponents ...\n";

    LOGBP(info, "Starting up, using logfile %s", file.c_str());

    timer = 200 * 1000000;
    for (uint32_t i=0; i<100; ++i) {
        std::ostringstream ost;
        ost << "Message " << i;
        logWithLogger1("Message", ost.str());
        timer += 1;
        logWithLogger2("Message", ost.str());
        timer += 1;
    }

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.tokenacrossloggers.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testNoTokenMatchAcrossComponents\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file
                    + " bufferedlogtest.tokenacrossloggers.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void testLogLocationAsToken(const std::string& file, uint64_t& timer)
{
    std::cerr << "testLogLocationAsToken ...\n";
    LOGBP(info, "Starting up, using logfile %s", file.c_str());

    timer = 200 * 1000000;
    for (uint32_t i=0; i<100; ++i) {
        LOGBP(info, "Message %i", i);
        timer += 1;
        LOGBP(info, "Message %i", i);
        timer += 1;
    }

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.locationastoken.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testLogLocationAsToken\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file
                    + " bufferedlogtest.locationastoken.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void testLogMessageAsToken(const std::string& file, uint64_t& timer)
{
    std::cerr << "testLogMessageAsToken ...\n";
    LOGBM(info, "Starting up, using logfile %s", file.c_str());

    timer = 200 * 1000000;
    for (uint32_t i=0; i<100; ++i) {
        LOGBM(info, "Message %i", i % 10);
        timer += 1;
        LOGBM(info, "Message %i", i % 10);
        timer += 1;
    }

    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.messageastoken.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testLogMessageAsToken\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file
                    + " bufferedlogtest.messageastoken.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void testNonBufferedLoggerTriggersBufferedLogTrim(const std::string& file,
                                                  uint64_t& timer)
{
    std::cerr << "testNonBufferedLoggerTriggersBufferedLogTrim ...\n";
        // Write a lot of buffered log.
    LOGBM(info, "Starting up, using logfile %s", file.c_str());

    timer = 200 * 1000000;
    for (uint32_t i=0; i<100; ++i) {
        LOGBP(info, "Message %i", i);
        timer += 1;
    }

        // Advance time to time where we should have flushed long ago.
    timer = 100000 * 1000000ull;
    LOG(info, "This should cause buffered log flush");

        // So flushing should have happened before this time.
    timer = 200000 * 1000000ull;
    LOGB_FLUSH();

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogtest.trimcache.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testNonBufferedLoggerTriggersBufferedLogTrim\n";
        [[maybe_unused]] int systemResult =
        system(("diff -u " + file
                    + " bufferedlogtest.trimcache.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());

}

void reset(uint64_t& timer) {
    timer = 0;
    ns_log::BufferedLogger::instance().setMaxEntryAge(300);
    ns_log::BufferedLogger::instance().setCountFactor(5);
}

int
main(int argc, char **argv)
{
    if (argc != 2) {
        std::cerr << "bufferedlogtest must be called with one argument\n";
        return EXIT_FAILURE;
    }
    ns_log::Logger::fakePid = true;
    ns_log::BufferedLogger::instance().setMaxCacheSize(10);
    uint64_t timer;
    ns_log_logger.setTimer(std::make_unique<TestTimer>(timer));
    ns_log::BufferedLogger::instance().setTimer(std::make_unique<TestTimer>(timer));

    reset(timer);
    testThatEntriesWithHighCountIsKept(argv[1], timer);
    reset(timer);
    testThatEntriesWithHighCountsAreEventuallyRemoved(argv[1], timer);
    reset(timer);
    testThatEntriesExpire(argv[1], timer);
    reset(timer);
    testThatHighCountEntriesDontStarveOthers(argv[1], timer);
    reset(timer);
    testNoTokenMatchAcrossComponents(argv[1], timer);
    reset(timer);
    testLogLocationAsToken(argv[1], timer);
    reset(timer);
    testLogMessageAsToken(argv[1], timer);
    reset(timer);
    testNonBufferedLoggerTriggersBufferedLogTrim(argv[1], timer);

    return EXIT_SUCCESS;
}
