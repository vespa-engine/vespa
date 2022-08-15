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

int
App::main(int argc, char **argv)
{
    if (argc != 2) {
        return usage();
    }

    auto [ok, messages] = verifyRankSetup(argv[1]);

    for (const auto & msg : messages) {
        LOG(warning, "%s", msg.c_str());
    }
    return ok ? 0 : 1;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
