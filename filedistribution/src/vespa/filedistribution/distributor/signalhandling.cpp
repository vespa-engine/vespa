// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "signalhandling.h"
#include <vespa/vespalib/util/signalhandler.h>

#include <vespa/log/log.h>
LOG_SETUP(".signalhandling");

typedef vespalib::SignalHandler SIG;

void
initSignals() {
    SIG::PIPE.ignore();
    SIG::INT.hook();
    SIG::TERM.hook();
    SIG::USR1.hook();
}

bool
askedToShutDown() {
    bool result = SIG::INT.check() || SIG::TERM.check();
    if (result) {
        LOG(debug, "Asked to shut down.");
    }
    return result;
}

bool
askedToReinitialize() {
    bool result = SIG::USR1.check();
    if (result) {
        LOG(debug, "Asked to reinitialize.");
    }
    return result;
}

void
clearReinitializeFlag() {
    SIG::USR1.clear();
}
