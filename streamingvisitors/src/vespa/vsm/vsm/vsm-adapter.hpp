// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "vsm-adapter.h"
#include <vespa/config/retriever/configsnapshot.hpp>

namespace vsm {

template <typename ConfigType>
std::unique_ptr<ConfigType>
VSMConfigSnapshot::getConfig() const
{
    return _snapshot->getConfig<ConfigType>(_configId);
}

} // namespace vsm

