// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

/**
 * This class is used internally by the @ref FNET_Transport to keep
 * track of the current configuration.
 **/
class FNET_Config {
public:
    vespalib::duration _iocTimeOut;
    uint32_t           _events_before_wakeup;
    uint32_t           _maxInputBufferSize;
    uint32_t           _maxOutputBufferSize;
    bool               _tcpNoDelay;
    bool               _drop_empty_buffers;

    FNET_Config();
};
