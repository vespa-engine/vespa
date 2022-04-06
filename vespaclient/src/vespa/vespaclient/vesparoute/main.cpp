// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "application.h"
#include <vespa/vespalib/util/signalhandler.h>

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    vesparoute::Application app;
    int ret = app.main(argc, argv);
    if (ret) {
        printf("Non-zero exit status: %d\n", ret);
    }
    return ret;
}

