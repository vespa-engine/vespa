// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include "timingvalues.h"
#include "configmanager.h"
#include <vespa/config/subscription/sourcespec.h>

namespace config {

/**
 * A ConfigContext is a context object that can be used to consolidate
 * multiple ConfigSubscribers to use the same resources. It also gives the
 * ability to reload config for unit testing or if using file configs.
 */
class IConfigContext
{
public:
    typedef std::shared_ptr<IConfigContext> SP;

    /**
     * Get an instance of the config manager.
     *
     * @return reference to a manager instance.
     */
    virtual IConfigManager & getManagerInstance() = 0;

    /**
     * Reload config for source provided by this context.
     */
    virtual void reload() = 0;

    virtual ~IConfigContext() { }
};

class ConfigContext : public IConfigContext
{
public:
    ConfigContext(const SourceSpec & spec = ServerSpec());
    ConfigContext(const TimingValues & timingValues, const SourceSpec & spec = ServerSpec());
    IConfigManager & getManagerInstance();
    void reload();

private:
    TimingValues _timingValues;
    int64_t _generation;
    ConfigManager _manager;
};


} // namespace

