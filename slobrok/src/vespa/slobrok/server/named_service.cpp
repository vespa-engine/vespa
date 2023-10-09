// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "named_service.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.named_service");

namespace slobrok {

//-----------------------------------------------------------------------------

NamedService::NamedService(const std::string & name, const std::string & spec)
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
