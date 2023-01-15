// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/bufferedlogger.h>

#include <fstream>
#include <iostream>
#include <unistd.h>
#include <cstdlib>

LOG_SETUP("bufferedlogskiptest");

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
            // Ignore debug entries. (Log adds some itself with timestamp we
            // can't control)
        if (result.find("\tdebug\t") == std::string::npos) {
            ost << result << "\n";
        }
    }
    return ost.str();
}

void testSkipBufferOnDebug(const std::string& file, uint64_t & timer)
{
    std::cerr << "testSkipBufferOnDebug ...\n";
    LOGBM(info, "Starting up, using logfile %s", file.c_str());

    timer = 200 * 1000000;
    for (uint32_t i=0; i<10; ++i) {
        LOGBP(info, "Message");
        timer += 1;
        LOGBM(info, "Message");
        timer += 1;
        LOGBT(info, "Message", "Message");
        timer += 1;
    }

    std::string result(readFile(file));
    std::string expected(readFile("bufferedlogskiptest.skipped.log"));

    if (result != expected) {
        std::cerr << "Failed "
                  << "testSkipBufferOnDebug\n";
        [[maybe_unused]] int system_result =
        system(("diff -u " + file
                    + " bufferedlogskiptest.skipped.log").c_str());
        std::_Exit(EXIT_FAILURE);
    }
    unlink(file.c_str());
}

void reset(uint64_t & timer) {
    timer = 0;
    ns_log::BufferedLogger::instance().setMaxCacheSize(10);
    ns_log::BufferedLogger::instance().setMaxEntryAge(300);
    ns_log::BufferedLogger::instance().setCountFactor(5);
}

int
main(int argc, char **argv)
{
    if (argc != 2) {
        std::cerr << "bufferedlogskiptest must be called with one argument\n";
        return EXIT_FAILURE;
    }
    ns_log::Logger::fakePid = true;
    uint64_t timer;
    ns_log_logger.setTimer(std::unique_ptr<ns_log::Timer>(new ns_log::TestTimer(timer)));
    ns_log::BufferedLogger::instance().setTimer(std::unique_ptr<ns_log::Timer>(new ns_log::TestTimer(timer)));

    reset(timer);
    testSkipBufferOnDebug(argv[1], timer);

    return EXIT_SUCCESS;
}
