// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/bucket/bucket.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>
#include <vespa/storage/distributor/node_supported_features.h>
#include <vespa/storage/distributor/operations/external/check_condition.h>
#include <vespa/storage/distributor/persistence_operation_metric_set.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/distributor/distributor_stripe_test_util.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using namespace ::testing;

namespace storage::distributor {

using namespace document;
using documentapi::TestAndSetCondition;

class CheckConditionTest
    : public Test,
      public DistributorStripeTestUtil
{
public:
    DocumentId                    _doc_id{"id:foo:testdoctype1:n=1234:bar"};
    BucketId                      _bucket_id{16, 1234};
    TestAndSetCondition           _tas_cond{"foo or bar"};
    PersistenceOperationMetricSet _metrics{"dummy_metrics", nullptr};
    uint32_t                      _trace_level{5};

    CheckConditionTest();
    ~CheckConditionTest() override;

    void SetUp() override {
        createLinks();
        // By default, set up 2 nodes {0, 1} with mutually out of sync replica state
        // and with both reporting that they support condition probing.
        setup_stripe(2, 2, "version:1 storage:2 distributor:1");
        config_enable_condition_probing(true);
        tag_content_node_supports_condition_probing(0, true);
        tag_content_node_supports_condition_probing(1, true);
        addNodesToBucketDB(_bucket_id, "0=10/20/30/t,1=40/50/60");
    };

    void TearDown() override {
        close();
    }

    std::shared_ptr<CheckCondition> create_check_condition() {
        auto& bucket_space = getDistributorBucketSpace();
        auto doc_bucket    = BucketIdFactory{}.getBucketId(_doc_id);
        auto bucket        = Bucket(FixedBucketSpaces::default_space(), _bucket_id);
        assert(_bucket_id.contains(doc_bucket));
        return CheckCondition::create_if_inconsistent_replicas(bucket, bucket_space, _doc_id, _tas_cond,
                                                               node_context(), operation_context(), _metrics,
                                                               _trace_level);
    }

    std::shared_ptr<api::GetCommand> sent_get_command(size_t idx) {
        return sent_command<api::GetCommand>(idx);
    }

    std::shared_ptr<api::PutCommand> sent_put_command(size_t idx) {
        return sent_command<api::PutCommand>(idx);
    }

    static std::shared_ptr<api::GetReply> make_reply(const api::GetCommand& cmd, api::Timestamp ts,
                                                     bool is_tombstone, bool condition_matched)
    {
        return std::make_shared<api::GetReply>(cmd, std::shared_ptr<document::Document>(), ts,
                                               false, is_tombstone, condition_matched);
    }

    std::shared_ptr<api::GetReply> make_matched_reply(size_t cmd_idx, api::Timestamp ts = 1000) {
        return make_reply(*sent_get_command(cmd_idx), ts, false, true);
    }

    std::shared_ptr<api::GetReply> make_mismatched_reply(size_t cmd_idx, api::Timestamp ts = 1000) {
        return make_reply(*sent_get_command(cmd_idx), ts, false, false);
    }

    std::shared_ptr<api::GetReply> make_not_found_non_tombstone_reply(size_t cmd_idx) {
        return make_reply(*sent_get_command(cmd_idx), 0, false, false);
    }

    std::shared_ptr<api::GetReply> make_tombstone_reply(size_t cmd_idx, api::Timestamp ts = 1000) {
        return make_reply(*sent_get_command(cmd_idx), ts, true, false);
    }

    std::shared_ptr<api::GetReply> make_trace_reply(size_t cmd_idx, api::Timestamp ts, std::string trace_message) {
        auto reply = make_reply(*sent_get_command(cmd_idx), ts, true, false);
        MBUS_TRACE(reply->getTrace(), _trace_level, trace_message);
        return reply;
    }

    std::shared_ptr<api::GetReply> make_failed_reply(size_t cmd_idx) {
        auto reply = make_reply(*sent_get_command(cmd_idx), 0, false, false);
        reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "did a bork"));
        return reply;
    }

    void test_cond_with_2_gets_sent(const std::function<void(CheckCondition&)>& reply_invoker,
                                    const std::function<void(const CheckCondition::Outcome&)>& outcome_checker)
    {
        auto cond = create_check_condition();
        ASSERT_TRUE(cond);
        cond->start_and_send(_sender);
        ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
        reply_invoker(*cond);
        auto& outcome = cond->maybe_outcome();
        ASSERT_TRUE(outcome);
        outcome_checker(*outcome);
    }
};

CheckConditionTest::CheckConditionTest() = default;
CheckConditionTest::~CheckConditionTest() = default;

TEST_F(CheckConditionTest, no_checker_returned_when_config_disabled) {
    config_enable_condition_probing(false);
    auto cond = create_check_condition();
    EXPECT_FALSE(cond);
}

TEST_F(CheckConditionTest, no_checker_returned_when_probing_not_supported_on_at_least_one_node) {
    tag_content_node_supports_condition_probing(1, false);
    auto cond = create_check_condition();
    EXPECT_FALSE(cond);
}

TEST_F(CheckConditionTest, no_checker_returned_when_bucket_replicas_are_consistent) {
    addNodesToBucketDB(_bucket_id, "0=10/20/30/t,1=10/20/30");
    auto cond = create_check_condition();
    EXPECT_FALSE(cond);
}

TEST_F(CheckConditionTest, no_checker_returned_when_empty_replica_set) {
    removeFromBucketDB(_bucket_id);
    auto cond = create_check_condition();
    EXPECT_FALSE(cond);
}

TEST_F(CheckConditionTest, starting_sends_condition_probe_gets) {
    auto cond = create_check_condition();
    ASSERT_TRUE(cond);
    EXPECT_FALSE(cond->maybe_outcome());
    // Nothing should be sent prior to start_and_send()
    ASSERT_EQ("", _sender.getCommands(true));
    // We don't test too much of the Get functionality, as that's already covered by GetOperation tests.
    // But we test the main binding glue between the two components.
    cond->start_and_send(_sender);
    EXPECT_FALSE(cond->maybe_outcome());
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    auto cmd = sent_get_command(0);
    EXPECT_EQ(cmd->getDocumentId(), _doc_id);
    EXPECT_EQ(cmd->condition(), _tas_cond);
    EXPECT_EQ(cmd->getFieldSet(), NoFields::NAME);
    EXPECT_EQ(cmd->internal_read_consistency(), api::InternalReadConsistency::Strong);
    EXPECT_EQ(cmd->getTrace().getLevel(), _trace_level);
}

TEST_F(CheckConditionTest, condition_matching_completes_check_with_match_outcome) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0));
        cond.handle_reply(_sender, make_matched_reply(1));
    }, [&](auto& outcome) {
        EXPECT_TRUE(outcome.matched_condition());
        EXPECT_FALSE(outcome.not_found());
        EXPECT_FALSE(outcome.failed());
    });
}

TEST_F(CheckConditionTest, newest_document_version_is_authoritative_for_condition_match) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0, 1001));
        cond.handle_reply(_sender, make_mismatched_reply(1, 1000));
    }, [&](auto& outcome) {
        EXPECT_TRUE(outcome.matched_condition());
        EXPECT_FALSE(outcome.not_found());
        EXPECT_FALSE(outcome.failed());
    });
}

TEST_F(CheckConditionTest, condition_mismatching_completes_check_with_mismatch_outcome) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0, 1000));
        cond.handle_reply(_sender, make_mismatched_reply(1, 1001));
    }, [&](auto& outcome) {
        EXPECT_FALSE(outcome.matched_condition());
        EXPECT_FALSE(outcome.not_found());
        EXPECT_FALSE(outcome.failed());
    });
}

TEST_F(CheckConditionTest, not_found_non_tombstone_completes_check_with_not_found_outcome) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_not_found_non_tombstone_reply(0));
        cond.handle_reply(_sender, make_not_found_non_tombstone_reply(1));
    }, [&](auto& outcome) {
        EXPECT_FALSE(outcome.matched_condition());
        EXPECT_TRUE(outcome.not_found());
        EXPECT_FALSE(outcome.failed());
    });
}

TEST_F(CheckConditionTest, not_found_with_tombstone_completes_check_with_not_found_outcome) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0, 1000));
        cond.handle_reply(_sender, make_tombstone_reply(1, 1001));
    }, [&](auto& outcome) {
        EXPECT_FALSE(outcome.matched_condition());
        EXPECT_TRUE(outcome.not_found());
        EXPECT_FALSE(outcome.failed());
    });
}

TEST_F(CheckConditionTest, failed_gets_completes_check_with_error_outcome) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0));
        cond.handle_reply(_sender, make_failed_reply(1));
    }, [&](auto& outcome) {
        EXPECT_FALSE(outcome.matched_condition());
        EXPECT_FALSE(outcome.not_found());
        EXPECT_TRUE(outcome.failed());
    });
}

TEST_F(CheckConditionTest, check_fails_if_replica_set_changed_between_start_and_completion) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0));
        // Simulate node 0 going down, with new cluster state version push and implicit DB removal
        enable_cluster_state("version:2 storage:1 distributor:1");
        addNodesToBucketDB(_bucket_id, "1=10/20/30");
        cond.handle_reply(_sender, make_matched_reply(1));
    }, [&](auto& outcome) {
        EXPECT_FALSE(outcome.matched_condition());
        EXPECT_FALSE(outcome.not_found());
        EXPECT_TRUE(outcome.failed());
        EXPECT_EQ(outcome.error_code().getResult(), api::ReturnCode::BUCKET_NOT_FOUND);
    });
}

TEST_F(CheckConditionTest, nested_get_traces_are_propagated_to_outcome) {
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_trace_reply(0, 100, "hello"));
        cond.handle_reply(_sender, make_trace_reply(1, 200, "world"));
    }, [&](auto& outcome) {
        auto trace_str = outcome.trace().toString();
        EXPECT_THAT(trace_str, HasSubstr("hello"));
        EXPECT_THAT(trace_str, HasSubstr("world"));
    });
}

TEST_F(CheckConditionTest, condition_evaluation_increments_probe_latency_metrics) {
    getClock().setAbsoluteTimeInSeconds(1);
    EXPECT_EQ(_metrics.latency.getLongValue("count"), 0);
    EXPECT_EQ(_metrics.ok.getLongValue("last"), 0);
    test_cond_with_2_gets_sent([&](auto& cond) {
        cond.handle_reply(_sender, make_matched_reply(0));
        getClock().setAbsoluteTimeInSeconds(3);
        cond.handle_reply(_sender, make_matched_reply(1));
    }, [&](auto& outcome) noexcept {
        (void)outcome;
    });
    EXPECT_EQ(_metrics.latency.getLongValue("count"), 1);
    EXPECT_EQ(_metrics.ok.getLongValue("last"), 1);
    EXPECT_DOUBLE_EQ(_metrics.latency.getLast(), 2'000.0); // in millis
}

}
