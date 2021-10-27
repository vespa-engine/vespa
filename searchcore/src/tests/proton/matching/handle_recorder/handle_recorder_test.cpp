// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/handlerecorder.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>

LOG_SETUP("handle_recorder_test");

using search::fef::MatchDataDetails;
using search::fef::MatchData;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using namespace proton::matching;

using HandleMap = HandleRecorder::HandleMap;

constexpr MatchDataDetails NormalMask = MatchDataDetails::Normal;
constexpr MatchDataDetails InterleavedMask = MatchDataDetails::Interleaved;
constexpr MatchDataDetails BothMask = static_cast<MatchDataDetails>(static_cast<int>(NormalMask) | static_cast<int>(InterleavedMask));

void
register_normal_handle(TermFieldHandle handle)
{
    HandleRecorder::register_handle(handle, MatchDataDetails::Normal);
}

void
register_interleaved_features_handle(TermFieldHandle handle)
{
    HandleRecorder::register_handle(handle, MatchDataDetails::Interleaved);
}

TEST(HandleRecorderTest, can_record_both_normal_and_interleaved_features_handles)
{
    HandleRecorder recorder;
    {
        HandleRecorder::Binder binder(recorder);
        register_normal_handle(3);
        register_interleaved_features_handle(5);
        register_normal_handle(7);
    }
    EXPECT_EQ(HandleMap({{3, NormalMask}, {5, InterleavedMask}, {7, NormalMask}}), recorder.get_handles());
    EXPECT_EQ("normal: [3,7], interleaved: [5]", recorder.to_string());
}

TEST(HandleRecorderTest, the_same_handle_can_be_in_both_normal_and_cheap_set)
{
    HandleRecorder recorder;
    {
        HandleRecorder::Binder binder(recorder);
        register_normal_handle(3);
        register_interleaved_features_handle(3);
    }
    EXPECT_EQ(HandleMap({{3, BothMask}}), recorder.get_handles());
}

namespace {

void check_tagging(const TermFieldMatchData &tfmd, bool exp_not_needed,
                   bool exp_needs_normal_features, bool exp_needs_interleaved_features)
{
    EXPECT_EQ(tfmd.isNotNeeded(), exp_not_needed);
    EXPECT_EQ(tfmd.needs_normal_features(), exp_needs_normal_features);
    EXPECT_EQ(tfmd.needs_interleaved_features(), exp_needs_interleaved_features);
}

}

TEST(HandleRecorderTest, tagging_of_matchdata_works)
{
    HandleRecorder recorder;
    {
        HandleRecorder::Binder binder(recorder);
        register_normal_handle(0);
        register_interleaved_features_handle(2);
        register_normal_handle(3);
        register_interleaved_features_handle(3);
    }
    auto md = MatchData::makeTestInstance(4, 4);
    recorder.tag_match_data(*md);
    check_tagging(*md->resolveTermField(0), false, true, false);
    check_tagging(*md->resolveTermField(1), true, false, false);
    check_tagging(*md->resolveTermField(2), false, false, true);
    check_tagging(*md->resolveTermField(3), false, true, true);
    HandleRecorder recorder2;
    {
        HandleRecorder::Binder binder(recorder2);
        register_normal_handle(0);
        register_interleaved_features_handle(0);
        register_normal_handle(1);
        register_interleaved_features_handle(3);
    }
    recorder2.tag_match_data(*md);
    check_tagging(*md->resolveTermField(0), false, true, true);
    check_tagging(*md->resolveTermField(1), false, true, false);
    check_tagging(*md->resolveTermField(2), true, false, false);
    check_tagging(*md->resolveTermField(3), false, false, true);
}

GTEST_MAIN_RUN_ALL_TESTS()
