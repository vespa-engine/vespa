// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/ddbstate.h>
#include <vespa/searchcore/proton/server/document_db_initialization_status.h>
#include <vespa/searchcore/proton/server/i_replay_progress_producer.h>
#include <vespa/searchcore/proton/server/proton_initialization_status.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::DDBState;
using proton::DocumentDBInitializationStatus;
using proton::IReplayProgressProducer;
using proton::ProtonInitializationStatus;

class DummyReplayProgressProducer : public proton::IReplayProgressProducer {
    float getProgress() const override {
        return 0.23f;
    }
};

class ProtonInitializationStatusTest : public ::testing::Test {
protected:
    std::shared_ptr<DummyReplayProgressProducer> _producer;
    std::shared_ptr<DDBState> _db_state1;
    std::shared_ptr<DDBState> _db_state2;
    std::shared_ptr<DDBState> _db_state3;

    std::shared_ptr<DocumentDBInitializationStatus> _db_status1;
    std::shared_ptr<DocumentDBInitializationStatus> _db_status2;
    std::shared_ptr<DocumentDBInitializationStatus> _db_status3;

    ProtonInitializationStatus _status;

    ProtonInitializationStatusTest()
        : _producer(std::make_shared<DummyReplayProgressProducer>()),
          _db_state1(std::make_shared<DDBState>()),
          _db_state2(std::make_shared<DDBState>()),
          _db_state3(std::make_shared<DDBState>()),
          _db_status1(std::make_shared<DocumentDBInitializationStatus>("db1", _db_state1)),
          _db_status2(std::make_shared<DocumentDBInitializationStatus>("db2", _db_state2)),
          _db_status3(std::make_shared<DocumentDBInitializationStatus>("db3", _db_state3)) {
        _db_status1->set_replay_progress_producer(_producer);
        _db_status2->set_replay_progress_producer(_producer);
        _db_status3->set_replay_progress_producer(_producer);

    }
    ~ProtonInitializationStatusTest() override __attribute__((noinline)) = default; // Avoid warning about inline-unit-growth limit

    void expect_db_counts(size_t load, size_t replay, size_t online) const {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get()["load"].asLong(), load);
        EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), replay);
        EXPECT_EQ(slime.get()["online"].asLong(), online);
    }
};

TEST_F(ProtonInitializationStatusTest, test_state_to_string)
{
    EXPECT_EQ("initializing", ProtonInitializationStatus::state_to_string(ProtonInitializationStatus::INITIALIZING));
    EXPECT_EQ("ready", ProtonInitializationStatus::state_to_string(ProtonInitializationStatus::READY));
}

TEST_F(ProtonInitializationStatusTest, test_states)
{
    _status.start_initialization();
    EXPECT_EQ(ProtonInitializationStatus::State::INITIALIZING, _status.get_state());
    _status.end_initialization();
    EXPECT_EQ(ProtonInitializationStatus::State::READY, _status.get_state());
}

TEST_F(ProtonInitializationStatusTest, test_timestamps)
{
    ProtonInitializationStatus::time_point zero;

    _status.start_initialization();
    ProtonInitializationStatus::time_point start_time = _status.get_start_time();
    EXPECT_GT(start_time, zero);

    _status.end_initialization();
    ProtonInitializationStatus::time_point end_time = _status.get_end_time();
    EXPECT_GE(end_time, start_time);

    EXPECT_EQ(start_time, _status.get_start_time());
    EXPECT_EQ(end_time, _status.get_end_time());
}

TEST_F(ProtonInitializationStatusTest, test_reporting_initializing_no_dbs) {
    _status.start_initialization();

    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    _status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 7);
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("initializing"));
    EXPECT_TRUE(slime.get()["current_time"].valid());
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              ProtonInitializationStatus::timepoint_to_string(_status.get_start_time()));
    EXPECT_EQ(slime.get()["load"].asLong(), 0);
    EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
    EXPECT_EQ(slime.get()["online"].asLong(), 0);

    vespalib::slime::Inspector& dbs = slime.get()["dbs"];
    EXPECT_TRUE(dbs.valid());
    EXPECT_EQ(dbs.entries(), 0);
}

TEST_F(ProtonInitializationStatusTest, test_reporting_ready_no_dbs) {
    _status.start_initialization();
    _status.end_initialization();

    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    _status.report_initialization_status(inserter);

    EXPECT_EQ(slime.get().children(), 8);
    EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("ready"));
    EXPECT_TRUE(slime.get()["current_time"].valid());
    EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
              ProtonInitializationStatus::timepoint_to_string(_status.get_start_time()));
    EXPECT_EQ(slime.get()["end_time"].asString().make_string(),
              ProtonInitializationStatus::timepoint_to_string(_status.get_end_time()));
    EXPECT_EQ(slime.get()["load"].asLong(), 0);
    EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
    EXPECT_EQ(slime.get()["online"].asLong(), 0);

    vespalib::slime::Inspector& dbs = slime.get()["dbs"];
    EXPECT_TRUE(dbs.valid());
    EXPECT_EQ(dbs.entries(), 0);
};

TEST_F(ProtonInitializationStatusTest, test_reporting_with_dbs) {
    _status.start_initialization();

    _status.addDocumentDBInitializationStatus(_db_status1);
    _status.addDocumentDBInitializationStatus(_db_status2);
    _db_state1->enterLoadState();
    _db_state2->enterLoadState();

    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get().children(), 7);
        EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("initializing"));
        EXPECT_TRUE(slime.get()["current_time"].valid());
        EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
                  ProtonInitializationStatus::timepoint_to_string(_status.get_start_time()));
        EXPECT_EQ(slime.get()["load"].asLong(), 2);
        EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
        EXPECT_EQ(slime.get()["online"].asLong(), 0);

        vespalib::slime::Inspector& dbs = slime.get()["dbs"];
        EXPECT_TRUE(dbs.valid());
        EXPECT_EQ(dbs.entries(), 2);
        EXPECT_EQ(dbs[0]["name"].asString().make_string(), std::string("db1"));
        EXPECT_EQ(dbs[1]["name"].asString().make_string(), std::string("db2"));
    }

    _db_state1->enterReplayTransactionLogState();
    _db_state1->enterApplyLiveConfigState();
    _db_state1->enterReprocessState();
    _db_state1->enterOnlineState();

    _db_state2->enterReplayTransactionLogState();
    _db_state2->enterApplyLiveConfigState();
    _db_state2->enterReprocessState();
    _db_state2->enterOnlineState();

    _status.end_initialization();

    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get().children(), 8);
        EXPECT_EQ(slime.get()["state"].asString().make_string(), std::string("ready"));
        EXPECT_TRUE(slime.get()["current_time"].valid());
        EXPECT_EQ(slime.get()["start_time"].asString().make_string(),
                  ProtonInitializationStatus::timepoint_to_string(_status.get_start_time()));
        EXPECT_EQ(slime.get()["end_time"].asString().make_string(),
                  ProtonInitializationStatus::timepoint_to_string(_status.get_end_time()));
        EXPECT_EQ(slime.get()["load"].asLong(), 0);
        EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
        EXPECT_EQ(slime.get()["online"].asLong(), 2);

        vespalib::slime::Inspector& dbs = slime.get()["dbs"];
        EXPECT_TRUE(dbs.valid());
        EXPECT_EQ(dbs.entries(), 2);
        EXPECT_EQ(dbs[0]["name"].asString().make_string(), std::string("db1"));
        EXPECT_EQ(dbs[1]["name"].asString().make_string(), std::string("db2"));
    }
}

TEST_F(ProtonInitializationStatusTest, test_reporting_with_dbs_when_removing_and_adding_dbs) {
    _status.start_initialization();

    _status.addDocumentDBInitializationStatus(_db_status1);
    _status.addDocumentDBInitializationStatus(_db_status2);

    _db_state1->enterLoadState();
    _db_state1->enterReplayTransactionLogState();
    _db_state1->enterApplyLiveConfigState();
    _db_state1->enterReprocessState();
    _db_state1->enterOnlineState();

    _db_state2->enterLoadState();
    _db_state2->enterReplayTransactionLogState();
    _db_state2->enterApplyLiveConfigState();
    _db_state2->enterReprocessState();
    _db_state2->enterOnlineState();

    _status.end_initialization();

    _status.removeDocumentDBInitializationStatus(_db_status1);
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get().children(), 8);
        EXPECT_EQ(slime.get()["load"].asLong(), 0);
        EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
        EXPECT_EQ(slime.get()["online"].asLong(), 1);

        vespalib::slime::Inspector& dbs = slime.get()["dbs"];
        EXPECT_TRUE(dbs.valid());
        EXPECT_EQ(dbs.entries(), 1);
        EXPECT_EQ(dbs[0]["name"].asString().make_string(), std::string("db2"));
    }

    _status.addDocumentDBInitializationStatus(_db_status3);
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get().children(), 8);
        EXPECT_EQ(slime.get()["load"].asLong(), 1);
        EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
        EXPECT_EQ(slime.get()["online"].asLong(), 1);

        vespalib::slime::Inspector& dbs = slime.get()["dbs"];
        EXPECT_TRUE(dbs.valid());
        EXPECT_EQ(dbs.entries(), 2);
        EXPECT_EQ(dbs[0]["name"].asString().make_string(), std::string("db2"));
        EXPECT_EQ(dbs[1]["name"].asString().make_string(), std::string("db3"));
    }

    _db_state3->enterLoadState();
    _db_state3->enterReplayTransactionLogState();
    _db_state3->enterApplyLiveConfigState();
    _db_state3->enterReprocessState();
    _db_state3->enterOnlineState();

    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        _status.report_initialization_status(inserter);

        EXPECT_EQ(slime.get().children(), 8);
        EXPECT_EQ(slime.get()["load"].asLong(), 0);
        EXPECT_EQ(slime.get()["replay_transaction_log"].asLong(), 0);
        EXPECT_EQ(slime.get()["online"].asLong(), 2);

        vespalib::slime::Inspector& dbs = slime.get()["dbs"];
        EXPECT_TRUE(dbs.valid());
        EXPECT_EQ(dbs.entries(), 2);
        EXPECT_EQ(dbs[0]["name"].asString().make_string(), std::string("db2"));
        EXPECT_EQ(dbs[1]["name"].asString().make_string(), std::string("db3"));
    }
}

TEST_F(ProtonInitializationStatusTest, test_reporting_db_counts) {
    _status.start_initialization();

    _status.addDocumentDBInitializationStatus(_db_status1);
    _status.addDocumentDBInitializationStatus(_db_status2);
    _db_state1->enterLoadState();
    _db_state2->enterLoadState();

    expect_db_counts(2, 0, 0);

    _db_state1->enterReplayTransactionLogState();
    expect_db_counts(1, 1, 0);

    _db_state1->enterApplyLiveConfigState();
    expect_db_counts(2, 0, 0);

    _db_state1->enterReprocessState();
    expect_db_counts(2, 0, 0);

    _db_state1->enterOnlineState();
    expect_db_counts(1, 0, 1);

    _db_state2->enterReplayTransactionLogState();
    expect_db_counts(0, 1, 1);

    _db_state2->enterApplyLiveConfigState();
    expect_db_counts(1, 0, 1);

    _db_state2->enterReprocessState();
    expect_db_counts(1, 0, 1);

    _db_state2->enterOnlineState();
    expect_db_counts(0, 0, 2);

    _status.end_initialization();
    expect_db_counts(0, 0, 2);
}

TEST_F(ProtonInitializationStatusTest, test_reporting_db_counts_when_removing_and_adding_dbs) {
    _status.start_initialization();

    _status.addDocumentDBInitializationStatus(_db_status1);
    _status.addDocumentDBInitializationStatus(_db_status2);

    _db_state1->enterLoadState();
    _db_state1->enterReplayTransactionLogState();
    _db_state1->enterApplyLiveConfigState();
    _db_state1->enterReprocessState();
    _db_state1->enterOnlineState();

    _db_state2->enterLoadState();
    _db_state2->enterReplayTransactionLogState();
    _db_state2->enterApplyLiveConfigState();
    _db_state2->enterReprocessState();
    _db_state2->enterOnlineState();

    _status.end_initialization();
    expect_db_counts(0, 0, 2);

    _status.removeDocumentDBInitializationStatus(_db_status1);
    expect_db_counts(0, 0, 1);

    _status.addDocumentDBInitializationStatus(_db_status3);
    expect_db_counts(1, 0, 1);

    _db_state3->enterLoadState();
    expect_db_counts(1, 0, 1);

    _db_state3->enterReplayTransactionLogState();
    _db_state3->enterApplyLiveConfigState();
    _db_state3->enterReprocessState();
    _db_state3->enterOnlineState();

    expect_db_counts(0, 0, 2);

    _status.removeDocumentDBInitializationStatus(_db_status2);
    _status.removeDocumentDBInitializationStatus(_db_status3);
    expect_db_counts(0, 0, 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
