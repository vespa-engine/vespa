// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/timestamp.h>

namespace proton { class DocumentDBConfig; }

namespace proton::documentmetastore {

/*
 * Class representing configuration for lid reuse delayer.
 */
class LidReuseDelayerConfig
{
private:
    fastos::TimeStamp                _visibilityDelay;
    bool                             _hasIndexedOrAttributeFields;
public:
    LidReuseDelayerConfig();
    explicit LidReuseDelayerConfig(const DocumentDBConfig &configSnapshot);
    fastos::TimeStamp visibilityDelay() const { return _visibilityDelay; }
    bool hasIndexedOrAttributeFields() const { return _hasIndexedOrAttributeFields; }
};

}
