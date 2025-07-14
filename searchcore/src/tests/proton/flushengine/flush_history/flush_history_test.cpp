// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/flushengine/flush_history.h>
#include <vespa/searchcore/proton/flushengine/flush_history_view.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ios>

using proton::flushengine::FlushHistory;
using proton::flushengine::FlushHistoryEntry;
using proton::flushengine::FlushHistoryView;
using proton::flushengine::FlushStrategyHistoryEntry;
using std::chrono::steady_clock;

using FlushCounts = FlushStrategyHistoryEntry::FlushCounts;

using namespace std::literals::chrono_literals;

namespace {

const std::string NORMAL_STRATEGY("normal");
const std::string ALL_STRATEGY("all");
const std::string HANDLER1("handler1");
const std::string HANDLER2("handler2");

template <typename Entry>
std::vector<std::string>
make_names(const std::vector<Entry>& entries) {
    std::vector<std::string> result;
    result.reserve(entries.size());
    for (auto& entry : entries) {
        result.emplace_back(entry.name());
    }
    return result;
}

std::vector<FlushCounts>
make_flush_counts(const std::vector<FlushStrategyHistoryEntry>& entries) {
    std::vector<FlushCounts> result;
    result.reserve(entries.size());
    for (auto& entry : entries) {
        result.emplace_back(entry.flush_counts());
    }
    return result;
}

std::vector<FlushCounts>
make_finished_flush_counts(const FlushHistoryView& view)
{
    return make_flush_counts(view.finished_strategies());
}

std::vector<FlushCounts>
make_draining_flush_counts(const FlushHistoryView& view)
{
    return make_flush_counts(view.draining_strategies());
}

FlushCounts
make_active_flush_counts(const FlushHistoryView& view)
{
    return view.active_strategy().flush_counts();;
}

struct TSS {
    bool switch_time_set;
    bool finish_time_set;
    bool last_flush_time_set;
    TSS(bool switch_time_set_in, bool finish_time_set_in, bool last_flush_time_set_in)
        : switch_time_set(switch_time_set_in),
          finish_time_set(finish_time_set_in),
          last_flush_time_set(last_flush_time_set_in)
    {

    }
    bool operator==(const TSS&) const noexcept = default;
};

void PrintTo(const TSS& tss, std::ostream* os)
{
    *os << "{ switched=" << std::boolalpha << tss.switch_time_set << ", finished=" << tss.finish_time_set
    << ", flushed=" << tss.last_flush_time_set << " }";
}


TSS
make_tss(const FlushStrategyHistoryEntry& entry)
{
    return TSS(entry.switch_time() != steady_clock::time_point(),
               entry.finish_time() != steady_clock::time_point(),
               entry.last_flush_finish_time() != steady_clock::time_point());
}

std::vector<TSS>
make_tss(const std::vector<FlushStrategyHistoryEntry>& entries)
{
    std::vector<TSS> result;
    result.reserve(entries.size());
    for (auto& entry : entries) {
        result.emplace_back(make_tss(entry));
    }
    return result;
}

std::vector<TSS>
make_finished_tss(const FlushHistoryView& view)
{
    return make_tss(view.finished_strategies());
}

std::vector<TSS>
make_draining_tss(const FlushHistoryView& view)
{
    return make_tss(view.draining_strategies());
}

TSS
make_active_tss(const FlushHistoryView& view)
{
    return make_tss(view.active_strategy());
}

}

namespace proton::flushengine {

void PrintTo(const FlushCounts& counts, std::ostream* os)
{
    *os << "FlushCounts(" << counts._started << "," << counts._finished << "," <<
    counts._inherited << "," << counts._inherited_finished << ")";
}

}

class FlushHistoryTest : public ::testing::Test
{
protected:

    using SV = std::vector<std::string>;

    FlushHistory _flush_history;

    FlushHistoryTest();
    ~FlushHistoryTest() override;
};

FlushHistoryTest::FlushHistoryTest()
    : ::testing::Test(),
      _flush_history(NORMAL_STRATEGY, 42, 3)
{
}

FlushHistoryTest::~FlushHistoryTest() = default;

TEST_F(FlushHistoryTest, empty_history)
{
    auto view = _flush_history.make_view();
    auto& active_strategy = view->active_strategy();
    EXPECT_EQ(NORMAL_STRATEGY, active_strategy.name());
    EXPECT_EQ(42, active_strategy.id());
    EXPECT_FALSE(active_strategy.priority_strategy());
    EXPECT_EQ(3, view->max_concurrent_normal());
    EXPECT_TRUE(view->finished().empty());
    EXPECT_TRUE(view->active().empty());
    EXPECT_TRUE(view->pending().empty());
    EXPECT_TRUE(view->finished_strategies().empty());
    ASSERT_TRUE(view->draining_strategies().empty());
    EXPECT_TRUE(view->last_strategies().empty());
    EXPECT_EQ((std::vector<FlushCounts>{}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{{}}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(0, 0, 0, 0), make_active_flush_counts(*view));
    EXPECT_EQ((std::vector<TSS>{}), make_finished_tss(*view));
    EXPECT_EQ((std::vector<TSS>{}), make_draining_tss(*view));
    EXPECT_EQ(TSS(false, false, false), make_active_tss(*view));
}

TEST_F(FlushHistoryTest, track_flushes)
{
    _flush_history.start_flush(HANDLER1, "a1", 3s, 5);
    _flush_history.start_flush(HANDLER2, "a2", 1s, 6);
    _flush_history.start_flush(HANDLER1, "a3", 4s, 7);
    _flush_history.flush_done(6);
    _flush_history.flush_done(5);
    _flush_history.prune_done(6);
    _flush_history.prune_done(5);
    auto view = _flush_history.make_view();
    EXPECT_EQ((SV{"handler2.a2", "handler1.a1"}), make_names(view->finished()));
    EXPECT_EQ(SV{"handler1.a3"}, make_names(view->active()));
    EXPECT_EQ((std::vector<FlushCounts>{}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(3, 2, 0, 0), make_active_flush_counts(*view));
    EXPECT_EQ(TSS(false, false, true), make_active_tss(*view));
}

TEST_F(FlushHistoryTest, tracks_pending_flushes)
{
    _flush_history.add_pending_flush(HANDLER1, "a1", 3s);
    _flush_history.add_pending_flush(HANDLER2, "a2", 1s);
    _flush_history.add_pending_flush(HANDLER2, "a3", 4s);
    _flush_history.add_pending_flush(HANDLER1, "a4", 7s);
    _flush_history.start_flush(HANDLER1, "a1", 3s, 5);
    _flush_history.start_flush(HANDLER2, "a2", 1s, 6);
    _flush_history.flush_done(6);
    _flush_history.prune_done(6);
    auto view = _flush_history.make_view();
    EXPECT_EQ(SV{"handler2.a2"}, make_names(view->finished()));
    EXPECT_EQ(SV{"handler1.a1"}, make_names(view->active()));
    EXPECT_EQ((SV{"handler2.a3", "handler1.a4"}), make_names(view->pending()));
    EXPECT_EQ((std::vector<FlushCounts>{}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(2, 1, 0, 0), make_active_flush_counts(*view));
    EXPECT_EQ(TSS(false, false, true), make_active_tss(*view));
}

TEST_F(FlushHistoryTest, pending_flushes_can_be_cleared)
{
    _flush_history.add_pending_flush(HANDLER1, "a1", 3s);
    _flush_history.clear_pending_flushes();
    auto view = _flush_history.make_view();
    EXPECT_TRUE(view->pending().empty());
    EXPECT_EQ(TSS(false, false, false), make_active_tss(*view));
}

TEST_F(FlushHistoryTest, active_priority_flush_strategy_can_be_detected)
{
    _flush_history.set_strategy(ALL_STRATEGY, 43, true);
    auto view = _flush_history.make_view();
    auto& active_strategy = view->active_strategy();
    EXPECT_EQ(ALL_STRATEGY, active_strategy.name());
    EXPECT_EQ(43, active_strategy.id());
    EXPECT_TRUE(active_strategy.priority_strategy());
    EXPECT_EQ((std::vector<TSS>{{true, true, false}}), make_finished_tss(*view));
    EXPECT_EQ((std::vector<TSS>{}), make_draining_tss(*view));
    EXPECT_EQ(TSS(false, false, false), make_active_tss(*view));
}

TEST_F(FlushHistoryTest, flush_strategy_can_be_changed)
{
    _flush_history.start_flush(HANDLER1, "a1", 3s, 5);
    _flush_history.set_strategy(ALL_STRATEGY, 43, true);
    _flush_history.add_pending_flush(HANDLER2, "a2", 1s);
    _flush_history.add_pending_flush(HANDLER1, "a3", 4s);
    _flush_history.start_flush(HANDLER2, "a2", 1s, 6);
    _flush_history.start_flush(HANDLER1, "a3", 4s, 7);
    _flush_history.set_strategy(NORMAL_STRATEGY, 44, false);
    auto view = _flush_history.make_view();
    ASSERT_EQ(2, view->draining_strategies().size());
    auto& active_strategy = view->active_strategy();
    EXPECT_EQ(NORMAL_STRATEGY, active_strategy.name());
    EXPECT_EQ(44, active_strategy.id());
    EXPECT_FALSE(active_strategy.priority_strategy());
    EXPECT_TRUE(view->finished().empty());
    ASSERT_EQ((SV{"handler1.a1", "handler2.a2", "handler1.a3"}), make_names(view->active()));
    EXPECT_EQ(NORMAL_STRATEGY, view->active()[0].strategy());
    EXPECT_EQ(ALL_STRATEGY, view->active()[1].strategy());
    EXPECT_EQ(ALL_STRATEGY, view->active()[2].strategy());
    EXPECT_EQ((SV{}), make_names(view->finished_strategies()));
    EXPECT_EQ((SV{NORMAL_STRATEGY, ALL_STRATEGY}), make_names(view->draining_strategies()));
    EXPECT_FALSE(view->draining_strategies()[0].priority_strategy());
    EXPECT_TRUE(view->draining_strategies()[1].priority_strategy());
    EXPECT_EQ(42, view->draining_strategies()[0].id());
    EXPECT_EQ(43, view->draining_strategies()[1].id());
    EXPECT_EQ((std::vector<FlushCounts>{}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{{1, 0, 0, 0}, {2, 0, 1, 0}}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(0, 0, 3, 0), make_active_flush_counts(*view));
    EXPECT_EQ((SV{ALL_STRATEGY, NORMAL_STRATEGY}), make_names(view->last_strategies()));
    EXPECT_EQ((std::vector<TSS>{}), make_finished_tss(*view));
    EXPECT_EQ((std::vector<TSS>{{true, false, false}, {true, false, false}}), make_draining_tss(*view));
    EXPECT_EQ(TSS(false, false, false),make_active_tss(*view));
    _flush_history.flush_done(6);
    _flush_history.prune_done(6);
    view = _flush_history.make_view();
    EXPECT_EQ((SV{}), make_names(view->finished_strategies()));
    EXPECT_EQ((SV{NORMAL_STRATEGY, ALL_STRATEGY}), make_names(view->draining_strategies()));
    EXPECT_EQ((std::vector<FlushCounts>{}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{{1, 0, 0, 0}, {2, 1, 1, 0}}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(0, 0, 3, 1), make_active_flush_counts(*view));
    EXPECT_EQ((std::vector<TSS>{}), make_finished_tss(*view));
    EXPECT_EQ((std::vector<TSS>{{true, false, false}, {true, false, true}}), make_draining_tss(*view));
    EXPECT_EQ(TSS(false, false, true), make_active_tss(*view));
    _flush_history.flush_done(5);
    _flush_history.prune_done(5);
    view = _flush_history.make_view();
    EXPECT_EQ((SV{NORMAL_STRATEGY}), make_names(view->finished_strategies()));
    EXPECT_EQ((SV{ALL_STRATEGY}), make_names(view->draining_strategies()));
    EXPECT_EQ((std::vector<FlushCounts>{{1, 1, 0, 0}}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{{2, 1, 1, 1}}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(0, 0, 3, 2), make_active_flush_counts(*view));
    EXPECT_EQ((std::vector<TSS>{{true, true, true}}), make_finished_tss(*view));
    EXPECT_EQ((std::vector<TSS>{{true, false, true}}), make_draining_tss(*view));
    EXPECT_EQ(TSS(false, false, true), make_active_tss(*view));
    _flush_history.flush_done(7);
    _flush_history.prune_done(7);
    view = _flush_history.make_view();
    EXPECT_EQ((SV{NORMAL_STRATEGY, ALL_STRATEGY}), make_names(view->finished_strategies()));
    EXPECT_EQ((SV{}), make_names(view->draining_strategies()));
    EXPECT_EQ((std::vector<FlushCounts>{{1, 1, 0, 0}, {2, 2, 1, 1}}), make_finished_flush_counts(*view));
    EXPECT_EQ((std::vector<FlushCounts>{}), make_draining_flush_counts(*view));
    EXPECT_EQ(FlushCounts(0, 0, 3, 3), make_active_flush_counts(*view));
    EXPECT_EQ((std::vector<TSS>{{true, true, true}, {true, true, true}}), make_finished_tss(*view));
    EXPECT_EQ((std::vector<TSS>{}), make_draining_tss(*view));
    EXPECT_EQ(TSS(false, false, true), make_active_tss(*view));
}

GTEST_MAIN_RUN_ALL_TESTS()
