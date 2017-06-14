// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "tls_stats.h"

namespace proton {
namespace flushengine {

class TlsStatsMap;

/*
 * Class used to create statistics for a transaction log server over
 * multiple domains.
 */
class ITlsStatsFactory {
public:
    virtual ~ITlsStatsFactory() { }
    virtual TlsStatsMap create() = 0;
};

} // namespace proton::flushengine
} // namespace proton
