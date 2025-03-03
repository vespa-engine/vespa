// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

struct IAttributeManager;

/**
 * Class used to explore the state of an attribute manager and its attribute vectors.
 */
class AttributeManagerExplorer : public vespalib::StateExplorer
{
    std::shared_ptr<IAttributeManager> _mgr;

public:
    AttributeManagerExplorer(std::shared_ptr<IAttributeManager> mgr);
    ~AttributeManagerExplorer() override;

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<std::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(std::string_view name) const override;
};

} // namespace proton

