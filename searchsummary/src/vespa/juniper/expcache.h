// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simplemap.h"
#include <cstdint>

class MatchObject;

class ExpansionCache
{
public:
    explicit ExpansionCache(MatchObject* default_obj);

    virtual ~ExpansionCache();

    MatchObject* Lookup(uint32_t langid);
private:
    MatchObject* _default;
    simplemap<uint32_t, MatchObject*> _cache;

    ExpansionCache(ExpansionCache &);
    ExpansionCache &operator=(ExpansionCache &);
};

