// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "session_manager_explorer.h"
#include "sessionmanager.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/fastos/timestamp.h>

using vespalib::slime::Inserter;
using vespalib::slime::Cursor;
using vespalib::StateExplorer;

namespace proton::matching {

namespace {

const vespalib::string SEARCH = "search";

class SearchSessionExplorer : public vespalib::StateExplorer
{
private:
    const SessionManager &_manager;

public:
    SearchSessionExplorer(const SessionManager &manager) : _manager(manager) {}
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override {
        Cursor &state = inserter.insertObject();
        state.setLong("numSessions", _manager.getNumSearchSessions());
        if (full) {
            std::vector<SessionManager::SearchSessionInfo> sessions = _manager.getSortedSearchSessionInfo();
            Cursor &array = state.setArray("sessions");
            for (const auto &session: sessions) {
                Cursor &entry = array.addObject();
                entry.setString("id", session.id);
                entry.setString("created", vespalib::to_string(vespalib::to_utc(session.created)));
                entry.setString("doom", vespalib::to_string(vespalib::to_utc(session.doom)));
            }
        }
    }
};

} // namespace proton::matching::<unnamed>

void
SessionManagerExplorer::get_state(const Inserter &, bool) const
{
}

std::vector<vespalib::string>
SessionManagerExplorer::get_children_names() const
{
    return std::vector<vespalib::string>({SEARCH});
}

std::unique_ptr<StateExplorer>
SessionManagerExplorer::get_child(vespalib::stringref name) const
{
    if (name == SEARCH) {
        return std::make_unique<SearchSessionExplorer>(_manager);
    }
    return std::unique_ptr<StateExplorer>();
}

}
