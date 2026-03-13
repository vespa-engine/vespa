// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_manager.h"
#include <vespa/searchcorespi/common/resource_usage.h>

using searchcorespi::common::ResourceUsage;

namespace proton::test {

MockAttributeManager::MockAttributeManager()
    : _mock(),
      _writables(),
      _importedAttributes(),
      _writer(),
      _shared()
{
}

MockAttributeManager::~MockAttributeManager() = default;

ResourceUsage
MockAttributeManager::get_resource_usage() const
{
    return {};
}

}
