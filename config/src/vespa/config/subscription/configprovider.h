// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configvalue.h>

namespace config {


class ConfigProvider
{
public:
    virtual ~ConfigProvider() { }

    /**
     * Fetches the appropriate ConfigValue.
     *
     * @return the current ConfigValue.
     */
    virtual ConfigValue getConfig() const = 0;

    /**
     * Checks whether or not the config has changed.
     *
     * @return true if changed, false if not.
     */
    virtual bool isChanged() const = 0;
};

} // namespace config

