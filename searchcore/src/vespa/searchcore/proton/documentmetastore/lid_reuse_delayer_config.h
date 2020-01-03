// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace proton { class DocumentDBConfig; }

namespace proton::documentmetastore {

/*
 * Class representing configuration for lid reuse delayer.
 */
class LidReuseDelayerConfig
{
private:
    vespalib::duration  _visibilityDelay;
    bool                _hasIndexedOrAttributeFields;
public:
    LidReuseDelayerConfig();
    explicit LidReuseDelayerConfig(const DocumentDBConfig &configSnapshot);
    vespalib::duration visibilityDelay() const { return _visibilityDelay; }
    bool hasIndexedOrAttributeFields() const { return _hasIndexedOrAttributeFields; }
};

}
