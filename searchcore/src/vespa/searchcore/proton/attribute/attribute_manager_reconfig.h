// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_manager_reconfig.h"
#include <memory>

namespace proton {

class AttributeManager;
class SequentialAttributesInitializer;

/**
 * Class representing the result of the prepare step of an AttributeManager
 * reconfig.
 */
class AttributeManagerReconfig : public IAttributeManagerReconfig {
    std::shared_ptr<AttributeManager>                _mgr;
    std::unique_ptr<SequentialAttributesInitializer> _initializer;
public:
    AttributeManagerReconfig(std::shared_ptr<AttributeManager> mgr,
                             std::unique_ptr<SequentialAttributesInitializer> initializer);
    ~AttributeManagerReconfig() override;
    std::shared_ptr<IAttributeManager> create(uint32_t docid_limit, search::SerialNum serial_num) override;
};

}
