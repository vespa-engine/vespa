// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace search
{
namespace transactionlog
{

class SyncProxy
{
public:
    virtual ~SyncProxy() { }
    virtual void sync(SerialNum syncTo) = 0;
};

}

}

