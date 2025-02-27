// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_manager_explorer.h"
#include "attribute_executor.h"
#include "attribute_vector_explorer.h"
#include "i_attribute_manager.h"
#include "imported_attribute_vector_explorer.h"
#include "imported_attributes_repo.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>

using search::AttributeVector;
using search::attribute::ImportedAttributeVector;
using vespalib::StateExplorer;
using vespalib::slime::Inserter;

namespace proton {

namespace {

/*
 * State explorer proxy class that runs get_state() and get_child() in the attribute writer thread.
 * Returned child explorers are wrapped using this proxy.
 * It also ensures that the attribute vector is valid during navigation to child explorers due to the
 * shared _executor instance having a shared pointer to the attribute vector.
 */
class ThreadedStateExplorerProxy : public StateExplorer {
    std::shared_ptr<const AttributeExecutor> _executor;
    std::unique_ptr<StateExplorer> _explorer;
public:
    ThreadedStateExplorerProxy(std::shared_ptr<const AttributeExecutor> executor,
                               std::unique_ptr<StateExplorer> explorer);
    ~ThreadedStateExplorerProxy() override;
    void get_state(const Inserter &inserter, bool full) const override;
    std::vector<std::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(std::string_view name) const override;
};

ThreadedStateExplorerProxy::ThreadedStateExplorerProxy(std::shared_ptr<const AttributeExecutor> executor,
                                                       std::unique_ptr<StateExplorer> explorer)
    : StateExplorer(),
      _executor(std::move(executor)),
      _explorer(std::move(explorer))
{
}

ThreadedStateExplorerProxy::~ThreadedStateExplorerProxy() = default;

void
ThreadedStateExplorerProxy::get_state(const Inserter &inserter, bool full) const
{
    _executor->run_sync([&explorer = *_explorer, &inserter, full]() { explorer.get_state(inserter, full); });
}

std::vector<std::string>
ThreadedStateExplorerProxy::get_children_names() const
{
    return _explorer->get_children_names();
}

std::unique_ptr<StateExplorer>
ThreadedStateExplorerProxy::get_child(std::string_view name) const
{
    std::unique_ptr<StateExplorer> child;
    _executor->run_sync([&explorer = *_explorer, name, &child]() { child = explorer.get_child(name); });
    if (!child) {
        return {};
    }
    return std::make_unique<ThreadedStateExplorerProxy>(_executor, std::move(child));
}

}

AttributeManagerExplorer::AttributeManagerExplorer(std::shared_ptr<IAttributeManager> mgr)
    : _mgr(std::move(mgr))
{
}

AttributeManagerExplorer::~AttributeManagerExplorer() = default;

void
AttributeManagerExplorer::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    inserter.insertObject();
}

std::vector<std::string>
AttributeManagerExplorer::get_children_names() const
{
    auto& attributes = _mgr->getWritableAttributes();
    std::vector<std::string> names;
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
        auto executor = std::make_shared<AttributeExecutor>(_mgr, attr);
        auto explorer = std::make_unique<AttributeVectorExplorer>(std::move(attr));
        return std::make_unique<ThreadedStateExplorerProxy>(std::move(executor), std::move(explorer));
    }
    return {};
}

} // namespace proton
