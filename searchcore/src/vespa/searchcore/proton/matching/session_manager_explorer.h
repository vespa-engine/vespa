// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sessionmanager.h"
#include <vespa/vespalib/net/state_explorer.h>

namespace proton {
namespace matching {

/**
 * Class used to explore the state of a session manager
 */
class SessionManagerExplorer : public vespalib::StateExplorer
{
private:
    const SessionManager &_manager;

public:
    SessionManagerExplorer(const SessionManager &manager) : _manager(manager) {}
    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    virtual std::vector<vespalib::string> get_children_names() const override;
    virtual std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

}  // namespace proton::matching
}  // namespace proton
