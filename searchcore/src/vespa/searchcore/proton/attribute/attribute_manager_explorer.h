// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_manager.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of an attribute manager and its attribute vectors.
 */
class AttributeManagerExplorer : public vespalib::StateExplorer
{
private:
    proton::IAttributeManager::SP _mgr;

public:
    AttributeManagerExplorer(const proton::IAttributeManager::SP &mgr);
    ~AttributeManagerExplorer();

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton

