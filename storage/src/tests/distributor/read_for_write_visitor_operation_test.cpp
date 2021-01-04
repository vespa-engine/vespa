// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storage/common/reindexing_constants.h>
#include <vespa/storage/distributor/operations/external/read_for_write_visitor_operation.h>
#include <vespa/storage/distributor/operations/external/visitoroperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/uuid_generator.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/visitor.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;
using document::Bucket;
using document::BucketId;

namespace storage::distributor {

namespace {

Bucket default_bucket(BucketId id) {
    return Bucket(document::FixedBucketSpaces::default_space(), id);
}

api::StorageMessageAddress make_storage_address(uint16_t node) {
    static vespalib::string _storage("storage");
    return {&_storage, lib::NodeType::STORAGE, node};
}

struct MockUuidGenerator : UuidGenerator {
    vespalib::string _uuid;
    MockUuidGenerator() : _uuid("a-very-random-id") {}

    vespalib::string generate_uuid() const override {
        return _uuid;
    }
};

}

struct ReadForWriteVisitorOperationStarterTest : Test, DistributorTestUtil {
    document::TestDocMan            _test_doc_man;
    VisitorOperation::Config        _default_config;
    std::unique_ptr<OperationOwner> _op_owner;
    BucketId                        _superbucket;
    BucketId                        _sub_bucket;
    MockUuidGenerator               _mock_uuid_generator;

    ReadForWriteVisitorOperationStarterTest()
        : _test_doc_man(),
          _default_config(100, 100),
          _op_owner(),
          _superbucket(16, 4),
          _sub_bucket(17, 4),
          _mock_uuid_generator()
    {}

    void SetUp() override {
        createLinks();
        setupDistributor(1, 1, "version:1 distributor:1 storage:1");
        _op_owner = std::make_unique<OperationOwner>(_sender, getClock());
        _sender.setPendingMessageTracker(getDistributor().getPendingMessageTracker());

        addNodesToBucketDB(_sub_bucket, "0=1/2/3/t");
    }

    void TearDown() override {
        close();
    }

    std::shared_ptr<VisitorOperation> create_nested_visitor_op(bool valid_command = true) {
        auto cmd = std::make_shared<api::CreateVisitorCommand>(
                document::FixedBucketSpaces::default_space(), "reindexingvisitor", "foo", "");
        if (valid_command) {
            cmd->addBucketToBeVisited(_superbucket);
            cmd->addBucketToBeVisited(BucketId()); // Will be inferred to first sub-bucket in DB
        }
        return std::make_shared<VisitorOperation>(
                distributor_component(), distributor_component(),
                getDistributorBucketSpace(), cmd, _default_config,
                getDistributor().getMetrics().visits);
    }

    OperationSequencer& operation_sequencer() {
        return getExternalOperationHandler().operation_sequencer();
    }

    std::shared_ptr<ReadForWriteVisitorOperationStarter> create_rfw_op(std::shared_ptr<VisitorOperation> visitor_op) {
        return std::make_shared<ReadForWriteVisitorOperationStarter>(
                std::move(visitor_op), operation_sequencer(),
                *_op_owner, getDistributor().getPendingMessageTracker(),
                _mock_uuid_generator);
    }
};

TEST_F(ReadForWriteVisitorOperationStarterTest, visitor_that_fails_precondition_checks_is_immediately_failed) {
    auto op = create_rfw_op(create_nested_visitor_op(false));
    _op_owner->start(op, OperationStarter::Priority(120));
    ASSERT_EQ("", _sender.getCommands(true));
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(ILLEGAL_PARAMETERS, No buckets in CreateVisitorCommand for visitor 'foo')",
              _sender.getLastReply());
}

TEST_F(ReadForWriteVisitorOperationStarterTest, visitor_immediately_started_if_no_pending_ops_to_bucket) {
    auto op = create_rfw_op(create_nested_visitor_op(true));
    _op_owner->start(op, OperationStarter::Priority(120));
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));
}

TEST_F(ReadForWriteVisitorOperationStarterTest, visitor_is_bounced_if_merge_pending_for_bucket) {
    auto op = create_rfw_op(create_nested_visitor_op(true));
    std::vector<api::MergeBucketCommand::Node> nodes({{0, false}, {1, false}});
    auto merge = std::make_shared<api::MergeBucketCommand>(default_bucket(_sub_bucket),
                                                           std::move(nodes),
                                                           api::Timestamp(123456));
    merge->setAddress(make_storage_address(0));
    getDistributor().getPendingMessageTracker().insert(merge);
    _op_owner->start(op, OperationStarter::Priority(120));
    ASSERT_EQ("", _sender.getCommands(true));
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUSY, A merge operation is pending for this bucket)",
              _sender.getLastReply());
}

namespace {

struct ConcurrentMutationFixture {
    ReadForWriteVisitorOperationStarterTest& _test;
    std::shared_ptr<api::StorageCommand> _mutation;

    explicit ConcurrentMutationFixture(ReadForWriteVisitorOperationStarterTest& test) : _test(test) {}

    void block_bucket_with_mutation() {
        // Pending mutating op to same bucket, prevents visitor from starting
        auto update = std::make_shared<document::DocumentUpdate>(
                _test._test_doc_man.getTypeRepo(),
                *_test._test_doc_man.getTypeRepo().getDocumentType("testdoctype1"),
                document::DocumentId("id::testdoctype1:n=4:foo"));
        auto update_cmd = std::make_shared<api::UpdateCommand>(
                default_bucket(document::BucketId(0)), std::move(update), api::Timestamp(0));

        Operation::SP mutating_op;
        _test.getExternalOperationHandler().handleMessage(update_cmd, mutating_op);
        ASSERT_TRUE(mutating_op);
        _test._op_owner->start(mutating_op, OperationStarter::Priority(120));
        ASSERT_EQ("Update(BucketId(0x4400000000000004), id::testdoctype1:n=4:foo, timestamp 1) => 0",
                  _test.sender().getCommands(true, true));
        _mutation = _test.sender().command(0);
        // Since pending message tracking normally happens in the distributor itself during sendUp,
        // we have to emulate this and explicitly insert the sent message into the pending mapping.
        _test.getDistributor().getPendingMessageTracker().insert(_mutation);
    }

    void unblock_bucket() {
        // Pretend update operation completed
        auto update_reply = std::shared_ptr<api::StorageReply>(_mutation->makeReply());
        _test.getDistributor().getPendingMessageTracker().reply(*update_reply);
        _test._op_owner->handleReply(update_reply);
    }
};

}

TEST_F(ReadForWriteVisitorOperationStarterTest, visitor_start_deferred_if_pending_ops_to_bucket) {
    ConcurrentMutationFixture f(*this);
    auto op = create_rfw_op(create_nested_visitor_op(true));
    ASSERT_NO_FATAL_FAILURE(f.block_bucket_with_mutation());

    _op_owner->start(op, OperationStarter::Priority(120));
    // Nothing started yet
    ASSERT_EQ("", _sender.getCommands(true, false, 1));
    ASSERT_NO_FATAL_FAILURE(f.unblock_bucket());

    // Visitor should now be started!
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true, false, 1));
}

TEST_F(ReadForWriteVisitorOperationStarterTest, visitor_bounced_if_bucket_removed_from_db_before_deferred_start) {
    ConcurrentMutationFixture f(*this);
    auto op = create_rfw_op(create_nested_visitor_op(true));
    ASSERT_NO_FATAL_FAILURE(f.block_bucket_with_mutation());

    _op_owner->start(op, OperationStarter::Priority(120));
    // Nothing started yet
    ASSERT_EQ("", _sender.getCommands(true, false, 1));

    // Simulate that ownership of bucket has changed, or replica has gone down.
    removeFromBucketDB(_sub_bucket);
    ASSERT_NO_FATAL_FAILURE(f.unblock_bucket());

    // No visitor should be sent to the content node
    ASSERT_EQ("", _sender.getCommands(true, false, 1));
    // Instead, we should get a "bucket not found" transient error bounce back to the client.
    EXPECT_EQ("CreateVisitorReply(last=BucketId(0x0000000000000000)) "
              "ReturnCode(BUCKET_NOT_FOUND),"
              "UpdateReply(id::testdoctype1:n=4:foo, BucketId(0x0000000000000000), "
              "timestamp 1, timestamp of updated doc: 0) ReturnCode(NONE)",
              _sender.getReplies(false, true));
}

TEST_F(ReadForWriteVisitorOperationStarterTest, visitor_locks_bucket_with_random_token_with_parameter_propagation) {
    _mock_uuid_generator._uuid = "fritjof";
    auto op = create_rfw_op(create_nested_visitor_op(true));
    _op_owner->start(op, OperationStarter::Priority(120));
    ASSERT_EQ("Visitor Create => 0", _sender.getCommands(true));
    auto cmd = std::dynamic_pointer_cast<api::CreateVisitorCommand>(_sender.command(0));
    ASSERT_TRUE(cmd);
    EXPECT_EQ(cmd->getParameters().get(reindexing_bucket_lock_visitor_parameter_key(),
                                       vespalib::stringref("not found :I")),
              "fritjof");
}

}
