// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/handler.h>
#include <vespa/config/common/provider.h>
#include <vespa/config/common/waitable.h>
#include <vespa/config/common/pollable.h>
#include <vespa/config/common/interruptable.h>
#include <vespa/config/common/configupdate.h>

namespace config {

class IConfigHolder : public ConfigHandler,
                      public Provider<ConfigUpdate>,
                      public Waitable,
                      public Pollable,
                      public Interruptable
{
public:
    typedef std::shared_ptr<IConfigHolder> SP;
    virtual ~IConfigHolder() { }
};

} // namespace config

