// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexmanagerconfig.h"

namespace searchcorespi {

IndexManagerConfig::IndexManagerConfig(const vespalib::string &configId,
                                       const config::ConfigSnapshot &configSnapshot,
                                       size_t numSearcherThreads)
    : _configId(configId),
      _configSnapshot(configSnapshot),
      _numSearcherThreads(numSearcherThreads)
{
}

IndexManagerConfig::~IndexManagerConfig() { }

} // namespace searchcorespi

