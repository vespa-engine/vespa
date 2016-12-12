// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sourcespec.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

/**
 * A ConfigInstanceSpec serves a config from a config instance that does not change.
 */
class ConfigInstanceSpec : public SourceSpec
{
public:
    ConfigInstanceSpec(const ConfigInstance & instance);
    SourceFactory::UP createSourceFactory(const TimingValues & timingValues) const;
private:
    const ConfigKey _key;
    vespalib::asciistream _buffer;
};

}

