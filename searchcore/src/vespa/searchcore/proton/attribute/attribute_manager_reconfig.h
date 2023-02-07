// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <memory>
#include <optional>

namespace proton {

class AttributeManager;
class SequentialAttributesInitializer;

/**
 * Class representing the result of the prepare step of an AttributeManager
 * reconfig.
 */
class AttributeManagerReconfig {
    std::shared_ptr<AttributeManager>                _mgr;
    std::unique_ptr<SequentialAttributesInitializer> _initializer;
public:
    AttributeManagerReconfig(std::shared_ptr<AttributeManager> mgr,
                             std::unique_ptr<SequentialAttributesInitializer> initializer);
    ~AttributeManagerReconfig();
    std::shared_ptr<AttributeManager> create(uint32_t docid_limit, search::SerialNum serial_num);
};

}
