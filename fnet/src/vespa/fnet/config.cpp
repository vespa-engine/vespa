// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config.h"

FNET_Config::FNET_Config()
    : _minEventTimeOut(0),
      _pingInterval(0),
      _iocTimeOut(0),
      _maxInputBufferSize(0x10000),
      _maxOutputBufferSize(0x10000),
      _tcpNoDelay(true),
      _logStats(false),
      _directWrite(false)
{ }
