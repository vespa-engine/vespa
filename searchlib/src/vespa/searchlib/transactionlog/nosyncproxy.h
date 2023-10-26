// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "syncproxy.h"

namespace search::transactionlog {

class NoSyncProxy : public SyncProxy
{
public:
    NoSyncProxy();
    ~NoSyncProxy();
    void sync(SerialNum syncTo) override;
};

}
