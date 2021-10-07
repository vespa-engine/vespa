// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_manager_explorer.h"
#include "attribute_vector_explorer.h"
#include <vespa/searchlib/attribute/attributevector.h>

using vespalib::slime::Inserter;

namespace proton {

AttributeManagerExplorer::AttributeManagerExplorer(const proton::IAttributeManager::SP &mgr)
    : _mgr(mgr)
{
}

AttributeManagerExplorer::~AttributeManagerExplorer() {}

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
    auto attr = _mgr->getExclusiveReadAccessor(name);
    if (attr.get() != nullptr) {
        return std::make_unique<AttributeVectorExplorer>(std::move(attr));
    }
    return std::unique_ptr<vespalib::StateExplorer>();
}

} // namespace proton
