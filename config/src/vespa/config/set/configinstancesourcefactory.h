// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsetsource.h"
#include <vespa/config/common/sourcefactory.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

class Source;
class IConfigHolder;
class ConfigKey;

/**
 * Factory creating config payload from a single config instance
 */
class ConfigInstanceSourceFactory : public SourceFactory
{
public:
    ConfigInstanceSourceFactory(const ConfigKey & key, const vespalib::asciistream & buffer);

    /**
     * Create source handling config described by key.
     */
    Source::UP createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const override;
private:
    const ConfigKey _key;
    vespalib::asciistream _buffer;
};

} // namespace config

