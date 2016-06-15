// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".config.common.configcontext");
#include "configcontext.h"
#include "configmanager.h"
#include "exceptions.h"

namespace config {

ConfigContext::ConfigContext(const SourceSpec & spec)
    : _timingValues(),
      _generation(1),
      _manager(spec.createSourceFactory(_timingValues), _generation)
{
}

ConfigContext::ConfigContext(const TimingValues & timingValues, const SourceSpec & spec)
    : _timingValues(timingValues),
      _generation(1),
      _manager(spec.createSourceFactory(_timingValues), _generation)
{
}

IConfigManager &
ConfigContext::getManagerInstance()
{
    return _manager;
}

void
ConfigContext::reload()
{
    _generation++;
    _manager.reload(_generation);
}

} // namespace config
