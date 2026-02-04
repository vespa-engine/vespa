// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_manager.h"
#include <vespa/searchcore/proton/common/resource_usage.h>

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
