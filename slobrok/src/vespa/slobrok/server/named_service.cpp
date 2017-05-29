// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "named_service.h"

#include <vespa/log/log.h>
LOG_SETUP(".rpcserver");

namespace slobrok {

//-----------------------------------------------------------------------------

NamedService::NamedService(const char *name,
                     const char *spec)
    : _name(name),
      _spec(spec)
{
}


NamedService::~NamedService()
{
    LOG(spam, "(role[%s].~NamedService)", _name.c_str());
}

//-----------------------------------------------------------------------------

} // namespace slobrok
