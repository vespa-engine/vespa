// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton::matching {

class SessionManager;

/**
 * Class used to explore the state of a session manager
 */
class SessionManagerExplorer : public vespalib::StateExplorer
{
private:
    const SessionManager &_manager;

public:
    SessionManagerExplorer(const SessionManager &manager) : _manager(manager) {}
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override;
};

}
