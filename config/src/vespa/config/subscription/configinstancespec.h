// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sourcespec.h"
#include <vespa/config/common/configkey.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

/**
 * A ConfigInstanceSpec serves a config from a config instance that does not change.
 */
class ConfigInstanceSpec : public SourceSpec
{
public:
    ConfigInstanceSpec(const ConfigInstance & instance);
    ConfigInstanceSpec(const ConfigInstanceSpec &) = delete;
    ConfigInstanceSpec & operator =(const ConfigInstanceSpec &) = delete;
    ~ConfigInstanceSpec() override;
    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;
private:
    const ConfigKey _key;
    vespalib::asciistream _buffer;
};

}

