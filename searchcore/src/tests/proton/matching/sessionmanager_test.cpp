// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for sessionmanager.


#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/matching/session_manager_explorer.h>
#include <vespa/searchcore/proton/matching/search_session.h>
#include <vespa/searchcore/proton/matching/match_tools.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP("sessionmanager_test");

using vespalib::string;
using namespace proton;
using namespace proton::matching;
using vespalib::StateExplorer;

namespace {

void checkStats(SessionManager::Stats stats, uint32_t numInsert,
                uint32_t numPick, uint32_t numDropped, uint32_t numCached,
                uint32_t numTimedout) {
    EXPECT_EQUAL(numInsert, stats.numInsert);
    EXPECT_EQUAL(numPick, stats.numPick);
    EXPECT_EQUAL(numDropped, stats.numDropped);
    EXPECT_EQUAL(numCached, stats.numCached);
    EXPECT_EQUAL(numTimedout, stats.numTimedout);
}


TEST("require that SessionManager handles SearchSessions.") {
    string session_id("foo");
    fastos::TimeStamp start(100);
    fastos::TimeStamp doom(1000);
    MatchToolsFactory::UP mtf;
    SearchSession::OwnershipBundle owned_objects;
    auto session = std::make_shared<SearchSession>(session_id, start, doom, std::move(mtf), std::move(owned_objects));

    SessionManager session_manager(10);
    TEST_DO(checkStats(session_manager.getSearchStats(), 0, 0, 0, 0, 0));
    session_manager.insert(std::move(session));
    TEST_DO(checkStats(session_manager.getSearchStats(), 1, 0, 0, 1, 0));
    session = session_manager.pickSearch(session_id);
    EXPECT_TRUE(session.get());
    TEST_DO(checkStats(session_manager.getSearchStats(), 0, 1, 0, 1, 0));
    session_manager.insert(std::move(session));
    TEST_DO(checkStats(session_manager.getSearchStats(), 1, 0, 0, 1, 0));
    session_manager.pruneTimedOutSessions(500);
    TEST_DO(checkStats(session_manager.getSearchStats(), 0, 0, 0, 1, 0));
    session_manager.pruneTimedOutSessions(2000);
    TEST_DO(checkStats(session_manager.getSearchStats(), 0, 0, 0, 0, 1));

    session = session_manager.pickSearch(session_id);
    EXPECT_FALSE(session.get());
}

TEST("require that SessionManager can be explored") {
    fastos::TimeStamp start(100);
    fastos::TimeStamp doom(1000);
    SessionManager session_manager(10);
    session_manager.insert(std::make_shared<SearchSession>("foo", start, doom,
                                                           MatchToolsFactory::UP(), SearchSession::OwnershipBundle()));
    session_manager.insert(std::make_shared<SearchSession>("bar", start, doom,
                                                           MatchToolsFactory::UP(), SearchSession::OwnershipBundle()));
    session_manager.insert(std::make_shared<SearchSession>("baz", start, doom,
                                                           MatchToolsFactory::UP(), SearchSession::OwnershipBundle()));
    SessionManagerExplorer explorer(session_manager);
    EXPECT_EQUAL(std::vector<vespalib::string>({"search"}),
                 explorer.get_children_names());
    std::unique_ptr<StateExplorer> search = explorer.get_child("search");
    ASSERT_TRUE(search.get() != nullptr);
    vespalib::Slime state;
    vespalib::Slime full_state;
    search->get_state(vespalib::slime::SlimeInserter(state), false);
    search->get_state(vespalib::slime::SlimeInserter(full_state), true);
    EXPECT_EQUAL(3, state.get()["numSessions"].asLong());
    EXPECT_EQUAL(3, full_state.get()["numSessions"].asLong());
    EXPECT_EQUAL(0u, state.get()["sessions"].entries());
    EXPECT_EQUAL(3u, full_state.get()["sessions"].entries());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
