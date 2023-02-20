// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_reconfig.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager_reconfig.h>

namespace proton {

DocumentSubDBReconfig::DocumentSubDBReconfig(std::shared_ptr<Matchers> matchers_in, std::shared_ptr<IAttributeManager> attribute_manager_in)
    : _old_matchers(matchers_in),
      _new_matchers(matchers_in),
      _old_attribute_manager(attribute_manager_in),
      _new_attribute_manager(attribute_manager_in),
      _attribute_manager_reconfig()
{
}

DocumentSubDBReconfig::~DocumentSubDBReconfig() = default;

void
DocumentSubDBReconfig::set_attribute_manager_reconfig(std::unique_ptr<IAttributeManagerReconfig> attribute_manager_reconfig)
{
    _attribute_manager_reconfig = std::move(attribute_manager_reconfig);
}

void
DocumentSubDBReconfig::complete(uint32_t docid_limit, search::SerialNum serial_num)
{
    if (_attribute_manager_reconfig) {
        _new_attribute_manager = _attribute_manager_reconfig->create(docid_limit, serial_num);
        _attribute_manager_reconfig.reset();
    }
}

}
