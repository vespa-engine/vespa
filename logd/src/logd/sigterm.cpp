// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sigterm.h"
#include <vespa/vespalib/util/signalhandler.h>

void hook_signals()
{
    vespalib::SignalHandler::INT.hook();
    vespalib::SignalHandler::TERM.hook();
    vespalib::SignalHandler::PIPE.ignore();
}

bool gotSignaled()
{
    return (vespalib::SignalHandler::INT.check() ||
            vespalib::SignalHandler::TERM.check());
}

int gotSignalNumber()
{
    if (vespalib::SignalHandler::TERM.check()) {
        return SIGTERM;
    }
    if (vespalib::SignalHandler::INT.check()) {
        return SIGINT;
    }
    return 0;
}
