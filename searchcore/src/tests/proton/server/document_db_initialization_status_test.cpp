// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/attribute_initialization_status.h>
#include <vespa/searchcore/proton/server/ddbstate.h>
#include <vespa/searchcore/proton/server/document_db_initialization_status.h>
#include <vespa/searchcore/proton/server/i_replay_progress_producer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <memory>

using proton::DDBState;
using proton::DocumentDBInitializationStatus;

class DummyReplayProgressProducer : public proton::IReplayProgressProducer {
    float getProgress() const override {
        return 0.23f;
    }
};

class DocumentDBInitializationStatusTest : public ::testing::Test {
protected:
    std::shared_ptr<DummyReplayProgressProducer> _producer;
    std::shared_ptr<DDBState> _state;
    DocumentDBInitializationStatus _status;

    std::shared_ptr<AttributeInitializationStatus> _queued_attribute1;
    std::shared_ptr<AttributeInitializationStatus> _queued_attribute2;
    std::shared_ptr<AttributeInitializationStatus> _loading_attribute;
    std::shared_ptr<AttributeInitializationStatus> _loaded_attribute;
    std::shared_ptr<AttributeInitializationStatus> _reprocessing_attribute;
    std::shared_ptr<AttributeInitializationStatus> _reprocessed_attribute;
    std::shared_ptr<AttributeInitializationStatus> _reprocessed_loaded_attribute;

    DocumentDBInitializationStatusTest()
        : _producer(std::make_shared<DummyReplayProgressProducer>()),
          _state(std::make_shared<DDBState>()),
          _status("test_database", _state) {

        _queued_attribute1 = std::make_shared<AttributeInitializationStatus>("queued_attribute1");
        _queued_attribute2 = std::make_shared<AttributeInitializationStatus>("queued_attribute2");

        _loading_attribute = std::make_shared<AttributeInitializationStatus>("loading_attribute");
        _loading_attribute->start_loading();

        _loaded_attribute = std::make_shared<AttributeInitializationStatus>("loaded_attribute");
        _loaded_attribute->start_loading();
        _loaded_attribute->end_loading();

        _reprocessing_attribute = std::make_shared<AttributeInitializationStatus>("reprocessing_attribute");
        _reprocessing_attribute->start_loading();
        _reprocessing_attribute->start_reprocessing();
        _reprocessing_attribute->set_reprocessing_percentage(0.42f);

        _reprocessed_attribute = std::make_shared<AttributeInitializationStatus>("reprocessed_attribute");
        _reprocessed_attribute->start_loading();
        _reprocessed_attribute->start_reprocessing();
        _reprocessed_attribute->set_reprocessing_percentage(0.42f);
        _reprocessed_attribute->end_reprocessing();

        _reprocessed_loaded_attribute = std::make_shared<AttributeInitializationStatus>("reprocessed_loaded_attribute");
        _reprocessed_loaded_attribute->start_loading();
        _reprocessed_loaded_attribute->start_reprocessing();
        _reprocessed_loaded_attribute->set_reprocessing_percentage(0.42f);
        _reprocessed_loaded_attribute->end_reprocessing();
        _reprocessed_loaded_attribute->end_loading();
    }
    ~DocumentDBInitializationStatusTest() override __attribute__((noinline)) = default; // Avoid warning about inline-unit-growth limit

    void expect_children_and_state(size_t children, const std::string& state) const {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get().children(), children);
        EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("test_database"));
        EXPECT_EQ(slime.get()["state"].asString().make_string(), state);
    }
};

TEST_F(DocumentDBInitializationStatusTest, test_reporting_initializing) {
    _status.set_replay_progress_producer(_producer);
    _state->enterLoadState();

    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    _status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 4);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("test_database"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("load"));
    EXPECT_EQ(slime.get()["loading_started"].asString().make_string(),
              DocumentDBInitializationStatus::timepoint_to_string(_state->get_load_time()));

    vespalib::slime::Inspector& ready_subdb = slime.get()["ready_subdb"];
    EXPECT_EQ(ready_subdb.children(), 3);

    vespalib::slime::Inspector& _loaded_attributes = ready_subdb["loaded_attributes"];
    EXPECT_TRUE(_loaded_attributes.valid());
    EXPECT_EQ(_loaded_attributes.entries(), 0);

    vespalib::slime::Inspector& _loading_attributes = ready_subdb["loading_attributes"];
    EXPECT_TRUE(_loading_attributes.valid());
    EXPECT_EQ(_loading_attributes.entries(), 0);

    vespalib::slime::Inspector& _queued_attributes = ready_subdb["queued_attributes"];
    EXPECT_TRUE(_queued_attributes.valid());
    EXPECT_EQ(_queued_attributes.entries(), 0);
}

TEST_F(DocumentDBInitializationStatusTest, test_reporting_initializing_with_attributes) {
    _status.set_replay_progress_producer(_producer);
    std::vector<std::shared_ptr<AttributeInitializationStatus> > attributes;
    attributes.push_back(_queued_attribute1);
    attributes.push_back(_queued_attribute2);
    attributes.push_back(_loading_attribute);
    attributes.push_back(_loaded_attribute);
    attributes.push_back(_reprocessing_attribute);
    attributes.push_back(_reprocessed_attribute);
    attributes.push_back(_reprocessed_loaded_attribute);
    _status.set_attribute_initialization_statuses(std::move(attributes));

    _state->enterLoadState();

    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    _status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 4);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("test_database"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("load"));
    EXPECT_EQ(slime.get()["loading_started"].asString().make_string(),
              DocumentDBInitializationStatus::timepoint_to_string(_state->get_load_time()));

    vespalib::slime::Inspector& ready_subdb = slime.get()["ready_subdb"];
    EXPECT_EQ(ready_subdb.children(), 3);

    vespalib::slime::Inspector& _loaded_attributes = ready_subdb["loaded_attributes"];
    EXPECT_TRUE(_loaded_attributes.valid());
    EXPECT_EQ(_loaded_attributes.entries(), 2);
    EXPECT_EQ(_loaded_attributes[0]["name"].asString().make_string(), "loaded_attribute");
    EXPECT_EQ(_loaded_attributes[1]["name"].asString().make_string(), "reprocessed_loaded_attribute");

    vespalib::slime::Inspector& _loading_attributes = ready_subdb["loading_attributes"];
    EXPECT_TRUE(_loading_attributes.valid());
    EXPECT_EQ(_loading_attributes.entries(), 3);
    EXPECT_EQ(_loading_attributes[0]["name"].asString().make_string(), "loading_attribute");
    EXPECT_EQ(_loading_attributes[1]["name"].asString().make_string(), "reprocessing_attribute");
    EXPECT_EQ(_loading_attributes[2]["name"].asString().make_string(), "reprocessed_attribute");

    vespalib::slime::Inspector& _queued_attributes = ready_subdb["queued_attributes"];
    EXPECT_TRUE(_queued_attributes.valid());
    EXPECT_EQ(_queued_attributes.entries(), 2);
    EXPECT_EQ(_queued_attributes[0]["name"].asString().make_string(), "queued_attribute1");
    EXPECT_EQ(_queued_attributes[1]["name"].asString().make_string(), "queued_attribute2");
}

TEST_F(DocumentDBInitializationStatusTest, test_reporting_online_with_attributes) {
    _status.set_replay_progress_producer(_producer);
    std::vector<std::shared_ptr<AttributeInitializationStatus> > attributes;
    attributes.push_back(_loaded_attribute);
    attributes.push_back(_reprocessed_loaded_attribute);
    _status.set_attribute_initialization_statuses(std::move(attributes));

    _state->enterLoadState();
    _state->enterReplayTransactionLogState();
    _state->enterApplyLiveConfigState();
    _state->enterReprocessState();
    _state->enterOnlineState();

    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    _status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 7);
    EXPECT_EQ(slime.get()["name"].asString().make_string(), std::string("test_database"));
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("online"));
    EXPECT_EQ(slime.get()["loading_started"].asString().make_string(),
              DocumentDBInitializationStatus::timepoint_to_string(_state->get_load_time()));
    EXPECT_EQ(slime.get()["loading_finished"].asString().make_string(),
              DocumentDBInitializationStatus::timepoint_to_string(_state->get_online_time()));
    EXPECT_EQ(slime.get()["replay_started"].asString().make_string(),
              DocumentDBInitializationStatus::timepoint_to_string(_state->get_replay_time()));
    EXPECT_EQ(slime.get()["replay_progress"].asString().make_string(), "0.230000");

    vespalib::slime::Inspector& ready_subdb = slime.get()["ready_subdb"];
    EXPECT_EQ(ready_subdb.children(), 3);

    vespalib::slime::Inspector& _loaded_attributes = ready_subdb["loaded_attributes"];
    EXPECT_TRUE(_loaded_attributes.valid());
    EXPECT_EQ(_loaded_attributes.entries(), 2);
    EXPECT_EQ(_loaded_attributes[0]["name"].asString().make_string(), "loaded_attribute");
    EXPECT_EQ(_loaded_attributes[1]["name"].asString().make_string(), "reprocessed_loaded_attribute");

    vespalib::slime::Inspector& _loading_attributes = ready_subdb["loading_attributes"];
    EXPECT_TRUE(_loading_attributes.valid());
    EXPECT_EQ(_loading_attributes.entries(), 0);

    vespalib::slime::Inspector& _queued_attributes = ready_subdb["queued_attributes"];
    EXPECT_TRUE(_queued_attributes.valid());
    EXPECT_EQ(_queued_attributes.entries(), 0);
}

TEST_F(DocumentDBInitializationStatusTest, test_reporting_without_progress_producer) {
    std::vector<std::shared_ptr<AttributeInitializationStatus> > attributes;
    attributes.push_back(_loaded_attribute);
    attributes.push_back(_reprocessed_loaded_attribute);
    _status.set_attribute_initialization_statuses(std::move(attributes));

    _state->enterLoadState();
    _state->enterReplayTransactionLogState();

    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    _status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 6);
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("replay_transaction_log"));
    EXPECT_EQ(slime.get()["replay_progress"].asString().make_string(), "0.000000");
}

TEST_F(DocumentDBInitializationStatusTest, test_reporting_states_before_online) {
    _status.set_replay_progress_producer(_producer);
    _state->enterLoadState();
    expect_children_and_state(4, "load");

    _state->enterReplayTransactionLogState();
    expect_children_and_state(6, "replay_transaction_log");

    _state->enterApplyLiveConfigState();
    expect_children_and_state(6, "apply_live_config");

    _state->enterReprocessState();
    expect_children_and_state(6, "reprocess");

    _state->enterOnlineState();
    expect_children_and_state(7, "online");
}
