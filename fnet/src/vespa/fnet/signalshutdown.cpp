// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "signalshutdown.h"

#include "transport.h"

#include <vespa/vespalib/util/signalhandler.h>

FNET_SignalShutDown::FNET_SignalShutDown(FNET_Transport& t) : FNET_Task(t.GetScheduler()), _transport(t) {
    ScheduleNow();
}

void FNET_SignalShutDown::PerformTask() {
    using SIG = vespalib::SignalHandler;
    if (SIG::INT.check() || SIG::TERM.check()) {
        fprintf(stderr, "got signal, shutting down...\n");
        _transport.ShutDown(false);
    } else {
        Schedule(0.1);
    }
}

void FNET_SignalShutDown::hookSignals() {
    using SIG = vespalib::SignalHandler;
    SIG::INT.hook();
    SIG::TERM.hook();
}
