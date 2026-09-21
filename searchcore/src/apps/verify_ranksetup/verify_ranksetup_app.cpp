// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verify_ranksetup.h"

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/landlock.h>

#include <cstdlib>
#include <cstring>

#include <vespa/log/log.h>
LOG_SETUP("vespa-verify-ranksetup");

class App {
public:
    int usage();
    int landlock_failure();
    int main(int argc, char** argv);
};

int App::usage() {
    fprintf(stderr, "Usage: vespa-verify-ranksetup <config-id>\n");
    return 1;
}

int App::landlock_failure() {
    fprintf(stderr, "Landlock init failure\n");
    return 2;
}

namespace {
ns_log::Logger::LogLevel toLogLevel(search::fef::Level level) {
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

bool maybe_setup_landlock_from_env() {
    auto rules = vespalib::Landlock::from_env();
    if (rules) {
        auto status = vespalib::Landlock::landlock_self(*rules);
        if (status && *status == false) {
            return false;
        } // else: either landlocked OK or not supported at all by platform
    } // else: no landlock enforcement configured
    return true;
}

} // namespace

int App::main(int argc, char** argv) {
    if (!maybe_setup_landlock_from_env()) {
        return landlock_failure();
    }

    SearchMode mode = SearchMode::INDEXED;
    if (argc == 3 && (strcmp("-S", argv[2]) == 0)) {
        mode = SearchMode::STREAMING;
        --argc;
    }
    if (argc != 2) {
        return usage();
    }

    auto [ok, messages] = verifyRankSetup(argv[1], mode);

    for (const auto& msg : messages) {
        VLOG(toLogLevel(msg.first), "%s", msg.second.c_str());
    }
    return ok ? 0 : 1;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
