// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace config {

class IConfigManager;

/**
 * A ConfigContext is a context object that can be used to consolidate
 * multiple ConfigSubscribers to use the same resources. It also gives the
 * ability to reload config for unit testing or if using file configs.
 */
class IConfigContext
{
public:
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

    virtual ~IConfigContext() = default;
};

} // namespace

