// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verify_ranksetup.h"
#include <vespa/vespalib/util/signalhandler.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-verify-ranksetup");

class App
{
public:
    int usage();
    int main(int argc, char **argv);
};

int
App::usage()
{
    fprintf(stderr, "Usage: vespa-verify-ranksetup <config-id>\n");
    return 1;
}

namespace {
ns_log::Logger::LogLevel
toLogLevel(search::fef::Level level) {
    switch (level) {
        case search::fef::Level::INFO:
            return ns_log::Logger::LogLevel::info;
        case search::fef::Level::WARNING:
            return ns_log::Logger::LogLevel::warning;
        case search::fef::Level::ERROR:
            return ns_log::Logger::LogLevel::error;
    }
    abort();
}
}
int
App::main(int argc, char **argv)
{
    if (argc != 2) {
        return usage();
    }

    auto [ok, messages] = verifyRankSetup(argv[1]);

    for (const auto & msg : messages) {
        VLOG(toLogLevel(msg.first), "%s", msg.second.c_str());
    }
    return ok ? 0 : 1;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
