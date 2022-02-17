// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/subscribehandler.h>
#include <vespa/config/common/cancelhandler.h>
#include <vespa/config/common/reloadhandler.h>

namespace config {

class IConfigManager : public SubscribeHandler,
                       public CancelHandler,
                       public ReloadHandler
{
public:
    virtual ~IConfigManager() = default;
};

} // namespace config

