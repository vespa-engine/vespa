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

    std::string messages;
    bool ok = verifyRankSetup(argv[1], messages);

    if ( ! messages.empty() ) {
        LOG(info, "%s", messages.c_str());
    }
    if (!ok) {
        return 1;
    }
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
