// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>

using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;
using namespace documentapi;
using mbus::Blob;
using mbus::Routable;
using mbus::IRoutingPolicy;

class MessageBusTest : public testing::Test {
protected:
    static std::shared_ptr<const DocumentTypeRepo> _repo;
    MessageBusTest();
    ~MessageBusTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

MessageBusTest::MessageBusTest() = default;
MessageBusTest::~MessageBusTest() = default;

std::shared_ptr<const DocumentTypeRepo> MessageBusTest::_repo;

void
MessageBusTest::SetUpTestSuite()
{
    auto path = TEST_PATH("../../../test/cfg/testdoctypes.cfg");
    _repo = std::make_shared<const DocumentTypeRepo>(readDocumenttypesConfig(path));
}

void
MessageBusTest::TearDownTestSuite()
{
    _repo.reset();
}

TEST_F(MessageBusTest, test_message)
{
    const document::DataType *testdoc_type = _repo->getDocumentType("testdoc");

    // Test one update.
    UpdateDocumentMessage upd1(
            document::DocumentUpdate::SP(
                    new document::DocumentUpdate(*_repo, *testdoc_type,
                            document::DocumentId("id:ns:testdoc::testme1"))));

    EXPECT_TRUE(upd1.getType() == DocumentProtocol::MESSAGE_UPDATEDOCUMENT);
    EXPECT_TRUE(upd1.getProtocol() == "document");

    DocumentProtocol protocol(_repo);

    Blob blob = protocol.encode(vespalib::Version(6,221), upd1);
    EXPECT_TRUE(blob.size() > 0);

    Routable::UP dec1 = protocol.decode(vespalib::Version(6,221), blob);
    EXPECT_TRUE(dec1.get() != NULL);
    EXPECT_TRUE(dec1->isReply() == false);
    EXPECT_TRUE(dec1->getType() == DocumentProtocol::MESSAGE_UPDATEDOCUMENT);

    // Compare to another.
    UpdateDocumentMessage upd2(
            document::DocumentUpdate::SP(
                    new document::DocumentUpdate(*_repo, *testdoc_type,
                            document::DocumentId("id:ns:testdoc::testme2"))));
    EXPECT_TRUE(!(upd1.getDocumentUpdate().getId() == upd2.getDocumentUpdate().getId()));

    DocumentMessage& msg2 = static_cast<DocumentMessage&>(upd2);
    EXPECT_TRUE(msg2.getType() == DocumentProtocol::MESSAGE_UPDATEDOCUMENT);
}

TEST_F(MessageBusTest, test_protocol)
{
    DocumentProtocol protocol(_repo);
    EXPECT_TRUE(protocol.getName() == "document");

    IRoutingPolicy::UP policy = protocol.createPolicy(string("DocumentRouteSelector"), string("file:documentrouteselectorpolicy.cfg"));
    EXPECT_TRUE(policy.get() != NULL);

    policy = protocol.createPolicy(string(""),string(""));
    EXPECT_TRUE(policy.get() == NULL);

    policy = protocol.createPolicy(string("Balle"),string(""));
    EXPECT_TRUE(policy.get() == NULL);
}

TEST_F(MessageBusTest, get_document_message_is_not_sequenced)
{
    GetDocumentMessage message(document::DocumentId("id:foo:bar::baz"));
    EXPECT_FALSE(message.hasSequenceId());
}

TEST_F(MessageBusTest, stat_bucket_message_is_not_sequenced)
{
    StatBucketMessage message(document::BucketId(16, 1), "");
    EXPECT_FALSE(message.hasSequenceId());
}

TEST_F(MessageBusTest, get_bucket_list_message_is_not_sequenced)
{
    GetBucketListMessage message(document::BucketId(16, 1));
    EXPECT_FALSE(message.hasSequenceId());
}

GTEST_MAIN_RUN_ALL_TESTS()
