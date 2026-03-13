// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "task.h"

class FNET_Transport;
/**
 * Utility class that will shut down a transport when the process gets
 * either INT or TERM.
 **/
class FNET_SignalShutDown : FNET_Task {
private:
    FNET_Transport& _transport;

public:
    FNET_SignalShutDown(FNET_Transport& t);
    void PerformTask() override;

    /**
     * Set up signal handling to hook appropriate signals.
     **/
    static void hookSignals();
};
