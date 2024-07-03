// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_manager_explorer.h"
#include "attribute_executor.h"
#include "attribute_vector_explorer.h"
#include "imported_attribute_vector_explorer.h"
#include "imported_attributes_repo.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>

using search::AttributeVector;
using search::attribute::ImportedAttributeVector;
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
    auto& attributes = _mgr->getWritableAttributes();
    std::vector<vespalib::string> names;
    for (const auto &attr : attributes) {
        names.push_back(attr->getName());
    }
    auto imported = _mgr->getImportedAttributes();
    if (imported != nullptr) {
        std::vector<std::shared_ptr<ImportedAttributeVector>> i_list;
        imported->getAll(i_list);
        for (const auto& attr : i_list) {
            names.push_back(attr->getName());
        }
    }
    return names;
}

std::unique_ptr<vespalib::StateExplorer>
AttributeManagerExplorer::get_child(std::string_view name) const
{
    auto guard = _mgr->getAttribute(name);
    if (!guard || !guard->getSP()) {
        auto imported = _mgr->getImportedAttributes();
        if (imported != nullptr) {
            auto& imported_attr = imported->get(name);
            if (imported_attr) {
                return std::make_unique<ImportedAttributeVectorExplorer>(imported_attr);
            }
        }
    }
    auto attr = guard ? guard->getSP() : std::shared_ptr<AttributeVector>();
    if (attr && _mgr->getWritableAttribute(name) != nullptr) {
        auto executor = std::make_unique<AttributeExecutor>(_mgr, std::move(attr));
        return std::make_unique<AttributeVectorExplorer>(std::move(executor));
    }
    return {};
}

} // namespace proton
