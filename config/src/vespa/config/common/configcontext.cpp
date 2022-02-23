// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configcontext.h"
#include "configmanager.h"

namespace config {

ConfigContext::ConfigContext(const SourceSpec & spec)
    : ConfigContext(TimingValues(), spec)
{ }

ConfigContext::ConfigContext(const TimingValues & timingValues, const SourceSpec & spec)
    : _timingValues(timingValues),
      _generation(1),
      _manager(std::make_unique<ConfigManager>(spec.createSourceFactory(_timingValues), _generation))
{ }

IConfigManager &
ConfigContext::getManagerInstance()
{
    return *_manager;
}

void
ConfigContext::reload()
{
    _generation++;
    _manager->reload(_generation);
}

} // namespace config
