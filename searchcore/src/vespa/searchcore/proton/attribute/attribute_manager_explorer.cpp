// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_manager_explorer");

#include "attribute_manager_explorer.h"
#include "attribute_vector_explorer.h"
#include "exclusive_attribute_read_accessor.h"
#include <vespa/searchlib/attribute/attributeguard.h>

using vespalib::slime::Inserter;

namespace proton {

AttributeManagerExplorer::AttributeManagerExplorer(const proton::IAttributeManager::SP &mgr)
    : _mgr(mgr)
{
}

void
AttributeManagerExplorer::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    inserter.insertObject();
}

std::vector<vespalib::string>
AttributeManagerExplorer::get_children_names() const
{
    std::vector<search::AttributeGuard> attributes;
    _mgr->getAttributeListAll(attributes);
    std::vector<vespalib::string> names;
    for (const auto &attr : attributes) {
        names.push_back(attr->getName());
    }
    return names;
}

std::unique_ptr<vespalib::StateExplorer>
AttributeManagerExplorer::get_child(vespalib::stringref name) const
{
    ExclusiveAttributeReadAccessor::UP attr = _mgr->getExclusiveReadAccessor(name);
    if (attr.get() != nullptr) {
        return std::unique_ptr<vespalib::StateExplorer>(new AttributeVectorExplorer(std::move(attr)));
    }
    return std::unique_ptr<vespalib::StateExplorer>();
}

} // namespace proton
