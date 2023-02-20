// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_manager_reconfig.h"
#include "attributemanager.h"
#include "sequential_attributes_initializer.h"
#include <cassert>

namespace proton {

AttributeManagerReconfig::AttributeManagerReconfig(std::shared_ptr<AttributeManager> mgr,
                             std::unique_ptr<SequentialAttributesInitializer> initializer)
    : _mgr(std::move(mgr)),
      _initializer(std::move(initializer))
{
}

AttributeManagerReconfig::~AttributeManagerReconfig() = default;

std::shared_ptr<IAttributeManager>
AttributeManagerReconfig::create(uint32_t docid_limit, search::SerialNum serial_num)
{
    assert(_mgr);
    _mgr->addInitializedAttributes(_initializer->getInitializedAttributes(), docid_limit, serial_num);
    return std::move(_mgr);
}


}
