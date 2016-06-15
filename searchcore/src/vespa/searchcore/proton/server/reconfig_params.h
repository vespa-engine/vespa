// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfig.h"

namespace proton {

class ReconfigParams
{
private:
    const DocumentDBConfig::ComparisonResult _res;

public:
    ReconfigParams(const DocumentDBConfig::ComparisonResult &res);
    bool shouldSchemaChange() const;
    bool shouldMatchersChange() const;
    bool shouldIndexManagerChange() const;
    bool shouldAttributeManagerChange() const;
    bool shouldSummaryManagerChange() const;
    bool shouldSubDbsChange() const {
        return shouldMatchersChange()
                || shouldAttributeManagerChange()
                || shouldSummaryManagerChange();
    }
};

} // namespace proton

