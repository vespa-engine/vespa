// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/attribute_initialization_status.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::attribute::AttributeInitializationStatus;

TEST(AttributeInitializationStatusTest, test_get_name)
{
    AttributeInitializationStatus status("testAttribute");
    EXPECT_EQ("testAttribute", status.get_name());
}

TEST(AttributeInitializationStatusTest, test_reprocessing_percentage)
{
    AttributeInitializationStatus status("testAttribute");
    float percentage = 0.42f;
    status.set_reprocessing_percentage(percentage);
    EXPECT_EQ(percentage, status.get_reprocessing_percentage());
}

TEST(AttributeInitializationStatusTest, test_state_to_string)
{
    EXPECT_EQ("queued", AttributeInitializationStatus::state_to_string(AttributeInitializationStatus::QUEUED));
    EXPECT_EQ("loading", AttributeInitializationStatus::state_to_string(AttributeInitializationStatus::LOADING));
    EXPECT_EQ("reprocessing", AttributeInitializationStatus::state_to_string(AttributeInitializationStatus::REPROCESSING));
    EXPECT_EQ("loaded", AttributeInitializationStatus::state_to_string(AttributeInitializationStatus::LOADED));
}

TEST(AttributeInitializationStatusTest, test_states)
{
    AttributeInitializationStatus status("testAttribute");
    EXPECT_EQ(AttributeInitializationStatus::State::QUEUED, status.get_state());
    status.start_loading();
    EXPECT_EQ(AttributeInitializationStatus::State::LOADING, status.get_state());
    status.end_loading();
    EXPECT_EQ(AttributeInitializationStatus::State::LOADED, status.get_state());
}

TEST(AttributeInitializationStatusTest, test_states_with_reprocessing)
{
    AttributeInitializationStatus status("testAttribute");
    EXPECT_EQ(AttributeInitializationStatus::State::QUEUED, status.get_state());
    status.start_loading();
    EXPECT_EQ(AttributeInitializationStatus::State::LOADING, status.get_state());
    status.start_reprocessing();
    EXPECT_EQ(AttributeInitializationStatus::State::REPROCESSING, status.get_state());
    status.end_reprocessing();
    EXPECT_EQ(AttributeInitializationStatus::State::LOADING, status.get_state());
    status.end_loading();
    EXPECT_EQ(AttributeInitializationStatus::State::LOADED, status.get_state());
}

TEST(AttributeInitializationStatusTest, test_was_reprocessed)
{
    AttributeInitializationStatus status("testAttribute");
    EXPECT_FALSE(status.was_reprocessed());
    status.start_loading();
    EXPECT_FALSE(status.was_reprocessed());
    status.end_loading();
    EXPECT_FALSE(status.was_reprocessed());

    AttributeInitializationStatus status2("testAttribute");
    EXPECT_FALSE(status2.was_reprocessed());
    status2.start_loading();
    EXPECT_FALSE(status2.was_reprocessed());
    status2.start_reprocessing();
    EXPECT_TRUE(status2.was_reprocessed());
    status.end_reprocessing();
    EXPECT_TRUE(status2.was_reprocessed());
    status.end_loading();
    EXPECT_TRUE(status2.was_reprocessed());
}

TEST(AttributeInitializationStatusTest, test_timestamps)
{
    AttributeInitializationStatus::time_point zero;

    AttributeInitializationStatus status("testAttribute");
    status.start_loading();
    AttributeInitializationStatus::time_point start_time = status.get_start_time();
    EXPECT_GT(start_time, zero);

    status.start_reprocessing();
    AttributeInitializationStatus::time_point reprocessing_start_time = status.get_reprocessing_start_time();
    EXPECT_GT(reprocessing_start_time, zero);
    EXPECT_GE(reprocessing_start_time, start_time);

    status.end_reprocessing();
    AttributeInitializationStatus::time_point reprocessing_end_time = status.get_reprocessing_end_time();
    EXPECT_GT(reprocessing_end_time, zero);
    EXPECT_GE(reprocessing_end_time, reprocessing_start_time);

    status.end_loading();
    AttributeInitializationStatus::time_point end_time = status.get_end_time();
    EXPECT_GT(end_time, zero);
    EXPECT_GE(end_time, reprocessing_end_time);

    EXPECT_EQ(start_time, status.get_start_time());
    EXPECT_EQ(reprocessing_start_time, status.get_reprocessing_start_time());
    EXPECT_EQ(reprocessing_end_time, status.get_reprocessing_end_time());
    EXPECT_EQ(end_time, status.get_end_time());
}

TEST(AttributeInitializationStatusTest, test_reporting_queued) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);

    AttributeInitializationStatus status("testAttribute");
    status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 2);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("testAttribute"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("queued"));
}

TEST(AttributeInitializationStatusTest, test_reporting_loading) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);

    AttributeInitializationStatus status("testAttribute");
    status.start_loading();
    status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 3);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("testAttribute"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("loading"));
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_start_time()));
}

TEST(AttributeInitializationStatusTest, test_reporting_loaded) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);

    AttributeInitializationStatus status("testAttribute");
    status.start_loading();
    status.end_loading();
    status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 4);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("testAttribute"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("loaded"));
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_start_time()));
    EXPECT_EQ(slime.get()["end_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_end_time()));
}

TEST(AttributeInitializationStatusTest, test_reporting_reprocessing) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);

    AttributeInitializationStatus status("testAttribute");
    status.start_loading();
    status.start_reprocessing();
    status.set_reprocessing_percentage(0.42f);
    status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 5);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("testAttribute"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("reprocessing"));
    EXPECT_EQ(slime.get()["reprocess_progress"].asString().make_string(), "0.420000");
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_start_time()));
    EXPECT_EQ(slime.get()["reprocess_start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_reprocessing_start_time()));
}

TEST(AttributeInitializationStatusTest, test_reporting_reprocessing_loading) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);

    AttributeInitializationStatus status("testAttribute");
    status.start_loading();
    status.start_reprocessing();
    status.set_reprocessing_percentage(0.42f);
    status.end_reprocessing();
    status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 6);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("testAttribute"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("loading"));
    EXPECT_EQ(slime.get()["reprocess_progress"].asString().make_string(), "1.000000");
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_start_time()));
    EXPECT_EQ(slime.get()["reprocess_start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_reprocessing_start_time()));
    EXPECT_EQ(slime.get()["reprocess_end_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_reprocessing_end_time()));
}

TEST(AttributeInitializationStatusTest, test_reporting_reprocessing_loaded) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);

    AttributeInitializationStatus status("testAttribute");
    status.start_loading();
    status.start_reprocessing();
    status.set_reprocessing_percentage(0.42f);
    status.end_reprocessing();
    status.end_loading();
    status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 7);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("testAttribute"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("loaded"));
    EXPECT_EQ(slime.get()["reprocess_progress"].asString().make_string(), "1.000000");
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_start_time()));
    EXPECT_EQ(slime.get()["reprocess_start_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_reprocessing_start_time()));
    EXPECT_EQ(slime.get()["reprocess_end_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_reprocessing_end_time()));
    EXPECT_EQ(slime.get()["end_time"].asString().make_string(),
              AttributeInitializationStatus::timepoint_to_string(status.get_end_time()));
}

GTEST_MAIN_RUN_ALL_TESTS()
