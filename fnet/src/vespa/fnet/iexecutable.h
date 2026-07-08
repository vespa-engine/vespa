// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/**
 * Interface used when injecting code execution into the transport
 * thread.
 **/
class FNET_IExecutable {
public:
    /**
     * Invoked by the transport thread as the only step to handle an
     * execution event.
     **/
    virtual void execute() = 0;

    /**
     * empty
     **/
    virtual ~FNET_IExecutable() = default;
};
