// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config.h"

FNET_Config::FNET_Config()
    : _iocTimeOut(vespalib::duration::zero()),
      _events_before_wakeup(1),
      _maxInputBufferSize(0x10000),
      _maxOutputBufferSize(0x10000),
      _tcpNoDelay(true)
{
}
