// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for sessionmanager.


#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/matching/session_manager_explorer.h>
#include <vespa/searchcore/proton/matching/search_session.h>
#include <vespa/searchcore/proton/matching/match_tools.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("sessionmanager_test");

using std::string;
using namespace proton;
using namespace proton::matching;
using vespalib::StateExplorer;
using vespalib::steady_time;

namespace proton::matching {

void PrintTo(const SessionManager::Stats& stats, std::ostream* os) {
    *os << "{" << stats.numInsert << "," << stats.numPick << "," << stats.numDropped << "," <<
       stats.numCached << "," << stats.numTimedout << "}";
}

}

namespace {

TEST(SessionManagerTest, require_that_SessionManager_handles_SearchSessions)
{
    string session_id("foo");
    steady_time start(100ns);
    steady_time doom(1000ns);
    MatchToolsFactory::UP mtf;
    SearchSession::OwnershipBundle owned_objects;
    auto session = std::make_shared<SearchSession>(session_id, start, doom, std::move(mtf), std::move(owned_objects));

    SessionManager session_manager(10);
    EXPECT_EQ(SessionManager::Stats(0, 0, 0, 0, 0), session_manager.getSearchStats());
    session_manager.insert(std::move(session));
    EXPECT_EQ(SessionManager::Stats(1, 0, 0, 1, 0), session_manager.getSearchStats());
    session = session_manager.pickSearch(session_id);
    EXPECT_TRUE(session.get());
    EXPECT_EQ(SessionManager::Stats(0, 1, 0, 1, 0), session_manager.getSearchStats());
    session_manager.insert(std::move(session));
    EXPECT_EQ(SessionManager::Stats(1, 0, 0, 1, 0), session_manager.getSearchStats());
    session_manager.pruneTimedOutSessions(steady_time(500ns));
    EXPECT_EQ(SessionManager::Stats(0, 0, 0, 1, 0), session_manager.getSearchStats());
    session_manager.pruneTimedOutSessions(steady_time(2000ns));
    EXPECT_EQ(SessionManager::Stats(0, 0, 0, 0, 1), session_manager.getSearchStats());

    session = session_manager.pickSearch(session_id);
    EXPECT_FALSE(session.get());
}

TEST(SessionManagerTest, require_that_SessionManager_can_be_explored)
{
    steady_time start(100ns);
    steady_time doom(1000ns);
    SessionManager session_manager(10);
    session_manager.insert(std::make_shared<SearchSession>("foo", start, doom,
                                                           MatchToolsFactory::UP(), SearchSession::OwnershipBundle()));
    session_manager.insert(std::make_shared<SearchSession>("bar", start, doom,
                                                           MatchToolsFactory::UP(), SearchSession::OwnershipBundle()));
    session_manager.insert(std::make_shared<SearchSession>("baz", start, doom,
                                                           MatchToolsFactory::UP(), SearchSession::OwnershipBundle()));
    SessionManagerExplorer explorer(session_manager);
    EXPECT_EQ(std::vector<std::string>({"search"}),
                 explorer.get_children_names());
    std::unique_ptr<StateExplorer> search = explorer.get_child("search");
    ASSERT_TRUE(search.get() != nullptr);
    vespalib::Slime state;
    vespalib::Slime full_state;
    search->get_state(vespalib::slime::SlimeInserter(state), false);
    search->get_state(vespalib::slime::SlimeInserter(full_state), true);
    EXPECT_EQ(3, state.get()["numSessions"].asLong());
    EXPECT_EQ(3, full_state.get()["numSessions"].asLong());
    EXPECT_EQ(0u, state.get()["sessions"].entries());
    EXPECT_EQ(3u, full_state.get()["sessions"].entries());
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
