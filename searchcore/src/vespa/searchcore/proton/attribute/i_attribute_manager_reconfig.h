// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <memory>

namespace proton {

struct IAttributeManager;

/**
 * Interface class representing the result of the prepare step of an IAttributeManager
 * reconfig.
 */
class IAttributeManagerReconfig {
public:
    virtual ~IAttributeManagerReconfig() = default;
    virtual std::shared_ptr<IAttributeManager> create(uint32_t docid_limit, search::SerialNum serial_num) = 0;
};

}
