// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/documentapi/loadtypes/loadtype.h>
#include <vespa/storage/config/config-stor-prioritymapping.h>

namespace storage {

class PriorityMapper
{
    std::vector<uint8_t> _priorities;

public:
    typedef vespa::config::content::core::internal::InternalStorPrioritymappingType Config;

    PriorityMapper() : _priorities(16, 120) {}

    void setConfig(const Config c) {
        _priorities[documentapi::Priority::PRI_HIGHEST] = c.highest;
        _priorities[documentapi::Priority::PRI_VERY_HIGH] = c.veryHigh;
        _priorities[documentapi::Priority::PRI_HIGH_1] = c.high1;
        _priorities[documentapi::Priority::PRI_HIGH_2] = c.high2;
        _priorities[documentapi::Priority::PRI_HIGH_3] = c.high3;
        _priorities[documentapi::Priority::PRI_NORMAL_1] = c.normal1;
        _priorities[documentapi::Priority::PRI_NORMAL_2] = c.normal2;
        _priorities[documentapi::Priority::PRI_NORMAL_3] = c.normal3;
        _priorities[documentapi::Priority::PRI_NORMAL_4] = c.normal4;
        _priorities[documentapi::Priority::PRI_NORMAL_5] = c.normal5;
        _priorities[documentapi::Priority::PRI_NORMAL_6] = c.normal6;
        _priorities[documentapi::Priority::PRI_LOW_1] = c.low1;
        _priorities[documentapi::Priority::PRI_LOW_2] = c.low2;
        _priorities[documentapi::Priority::PRI_LOW_3] = c.low3;
        _priorities[documentapi::Priority::PRI_VERY_LOW] = c.veryLow;
        _priorities[documentapi::Priority::PRI_LOWEST] = c.lowest;
    }

    uint8_t getPriority(const documentapi::LoadType& lt) const {
        return _priorities[lt.getPriority()];
    }
};

} // storage
