// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * @brief Use this class for simple common-case signal handling.
 **/

class SigCatch
{
public:
    /**
     * Constructor installs signal handlers.
     **/
    SigCatch();

    /**
     * Check if a signal to stop has been received.
     **/
    bool receivedStopSignal();
};

} // namespace vespalib
