// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/exceptions.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/storage/common/reindexing_constants.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/visiting/visitormanager.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/storageserver/testvisitormessagesession.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>
#include <thread>
#include <sys/stat.h>

using namespace std::chrono_literals;
using document::test::makeBucketSpace;
using document::Document;
using document::DocumentId;
using namespace ::testing;

namespace storage {

namespace {

using msg_ptr_vector = std::vector<api::StorageMessage::SP>;

struct TestParams {
    TestParams& maxVisitorMemoryUsage(uint32_t bytes) {
        _maxVisitorMemoryUsage = bytes;
        return *this;
    }
    TestParams& parallelBuckets(uint32_t n) {
        _parallelBuckets = n;
        return *this;
    }
    TestParams& autoReplyError(const mbus::Error& error) {
        _autoReplyError = error;
        return *this;
    }

    uint32_t _maxVisitorMemoryUsage {UINT32_MAX};
    uint32_t _parallelBuckets {1};
    mbus::Error _autoReplyError;
};

}

struct VisitorTest : Test {
    static uint32_t docCount;
    std::vector<Document::SP> _documents;
    std::unique_ptr<TestVisitorMessageSessionFactory> _messageSessionFactory;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    DummyStorageLink* _bottom;
    VisitorManager* _manager;

    VisitorTest() : _node() {}

    // Not using setUp since can't throw exception out of it.
    void initializeTest(const TestParams& params = TestParams());

    struct VisitorOptions {
        std::string visitorType{"dumpvisitor"};

        VisitorOptions() {}

        VisitorOptions& withVisitorType(vespalib::stringref type) {
            visitorType = type;
            return *this;
        }
    };

    constexpr static api::StorageMessage::Priority DefaultPriority = 123;

    std::shared_ptr<api::CreateVisitorCommand> makeCreateVisitor(
            const VisitorOptions& options = VisitorOptions());
    void TearDown() override;
    bool waitUntilNoActiveVisitors();
    TestVisitorMessageSession& getSession(uint32_t n);
    void verifyCreateVisitorReply(
            api::ReturnCode::Result expectedResult,
            int checkStatsDocsVisited = -1,
            int checkStatsBytesVisited = -1,
            uint64_t* message_id_out = nullptr);
    void getMessagesAndReply(
            int expectedCount,
            TestVisitorMessageSession& session,
            std::vector<Document::SP> & docs,
            std::vector<DocumentId>& docIds,
            std::vector<std::string>& infoMessages,
            api::ReturnCode::Result returnCode = api::ReturnCode::OK);
    uint32_t getMatchingDocuments(std::vector<Document::SP>& docs);

protected:
    void doTestVisitorInstanceHasConsistencyLevel(
            vespalib::stringref visitorType,
            spi::ReadConsistency expectedConsistency);

    template <typename T>
    void fetchMultipleCommands(DummyStorageLink& link, size_t count,
                               std::vector<std::shared_ptr<T>>& commands_out);

    template <typename T>
    void fetchSingleCommand(DummyStorageLink& link, std::shared_ptr<T>& msg_out);

    void sendGetIterReply(GetIterCommand& cmd,
                          const api::ReturnCode& result =
                          api::ReturnCode(api::ReturnCode::OK),
                          uint32_t maxDocuments = 0,
                          bool overrideCompleted = false);
    void sendCreateIteratorReply(uint64_t iteratorId = 1234);
    void doCompleteVisitingSession(
            const std::shared_ptr<api::CreateVisitorCommand>& cmd,
            std::shared_ptr<api::CreateVisitorReply>& reply_out);

    void sendInitialCreateVisitorAndGetIterRound();

    int64_t getFailedVisitorDestinationReplyCount() const {
        // There's no metric manager attached to these tests, so even if the
        // test should magically freeze here for 5+ minutes, nothing should
        // come in and wipe our accumulated failure metrics.
        // Only 1 visitor thread running, so we know it has the metrics.
        const auto& metrics = _manager->getThread(0).getMetrics();
        return metrics.visitorDestinationFailureReplies.getCount();
    }
};

uint32_t VisitorTest::docCount = 10;

void
VisitorTest::initializeTest(const TestParams& params)
{
    vdstestlib::DirConfig config(getStandardConfig(true, "visitortest"));
    config.getConfig("stor-visitor").set("visitorthreads", "1");
    config.getConfig("stor-visitor").set(
            "defaultparalleliterators",
            std::to_string(params._parallelBuckets));
    config.getConfig("stor-visitor").set(
            "visitor_memory_usage_limit",
            std::to_string(params._maxVisitorMemoryUsage));

    std::string rootFolder = getRootFolder(config);

    ::chmod(rootFolder.c_str(), 0755);
    std::filesystem::remove_all(std::filesystem::path(rootFolder));
    std::filesystem::create_directories(std::filesystem::path(vespalib::make_string("%s/disks/d0", rootFolder.c_str())));
    std::filesystem::create_directories(std::filesystem::path(vespalib::make_string("%s/disks/d1", rootFolder.c_str())));

    _messageSessionFactory.reset(
            new TestVisitorMessageSessionFactory(config.getConfigId()));
    if (params._autoReplyError.getCode() != mbus::ErrorCode::NONE) {
        _messageSessionFactory->_autoReplyError = params._autoReplyError;
        _messageSessionFactory->_createAutoReplyVisitorSessions = true;
    }
    _node.reset(new TestServiceLayerApp(config.getConfigId()));
    _top.reset(new DummyStorageLink());
    _top->push_back(std::unique_ptr<StorageLink>(_manager
            = new VisitorManager(
                    config::ConfigUri(config.getConfigId()),
                _node->getComponentRegister(), *_messageSessionFactory)));
    _bottom = new DummyStorageLink();
    _top->push_back(std::unique_ptr<StorageLink>(_bottom));
    _manager->setTimeBetweenTicks(10);
    _top->open();

    std::string content(
            "To be, or not to be: that is the question:\n"
            "Whether 'tis nobler in the mind to suffer\n"
            "The slings and arrows of outrageous fortune,\n"
            "Or to take arms against a sea of troubles,\n"
            "And by opposing end them? To die: to sleep;\n"
            "No more; and by a sleep to say we end\n"
            "The heart-ache and the thousand natural shocks\n"
            "That flesh is heir to, 'tis a consummation\n"
            "Devoutly to be wish'd. To die, to sleep;\n"
            "To sleep: perchance to dream: ay, there's the rub;\n"
            "For in that sleep of death what dreams may come\n"
            "When we have shuffled off this mortal coil,\n"
            "Must give us pause: there's the respect\n"
            "That makes calamity of so long life;\n"
            "For who would bear the whips and scorns of time,\n"
            "The oppressor's wrong, the proud man's contumely,\n"
            "The pangs of despised love, the law's delay,\n"
            "The insolence of office and the spurns\n"
            "That patient merit of the unworthy takes,\n"
            "When he himself might his quietus make\n"
            "With a bare bodkin? who would fardels bear,\n"
            "To grunt and sweat under a weary life,\n"
            "But that the dread of something after death,\n"
            "The undiscover'd country from whose bourn\n"
            "No traveller returns, puzzles the will\n"
            "And makes us rather bear those ills we have\n"
            "Than fly to others that we know not of?\n"
            "Thus conscience does make cowards of us all;\n"
            "And thus the native hue of resolution\n"
            "Is sicklied o'er with the pale cast of thought,\n"
            "And enterprises of great pith and moment\n"
            "With this regard their currents turn awry,\n"
            "And lose the name of action. - Soft you now!\n"
            "The fair Ophelia! Nymph, in thy orisons\n"
            "Be all my sins remember'd.\n");
    _documents.clear();
    for (uint32_t i=0; i<docCount; ++i) {
        std::ostringstream uri;
        uri << "id:test:testdoctype1:n=" << i % 10 << ":http://www.ntnu.no/"
            << i << ".html";

        _documents.push_back(Document::SP(
                _node->getTestDocMan().createDocument(content, uri.str())));
        const document::DocumentType& type(_documents.back()->getType());
        _documents.back()->setValue(type.getField("headerval"),
                                    document::IntFieldValue(i % 4));
    }
}

void
VisitorTest::TearDown()
{
    if (_top) {
        _top->close();
        _top->flush();
        _top.reset();
    }
    _node.reset();
    _messageSessionFactory.reset();
    _manager = nullptr;
}

bool
VisitorTest::waitUntilNoActiveVisitors()
{
    int i = 0;
    for (; i < 1000; ++i) {
        if (_manager->getActiveVisitorCount() == 0) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    return false;
}

TestVisitorMessageSession&
VisitorTest::getSession(uint32_t n)
{
    // Wait until we have started the visitor
    const std::vector<TestVisitorMessageSession*>& sessions(
            _messageSessionFactory->_visitorSessions);
    framework::defaultimplementation::RealClock clock;
    framework::MilliSecTime endTime(
            clock.getTimeInMillis() + framework::MilliSecTime(30 * 1000));
    while (true) {
        {
            std::lock_guard lock(_messageSessionFactory->_accessLock);
            if (sessions.size() > n) {
                return *sessions[n];
            }
        }
        if (clock.getTimeInMillis() > endTime) {
            throw vespalib::IllegalStateException(
                    "Timed out waiting for visitor session", VESPA_STRLOC);
        }
        std::this_thread::sleep_for(10ms);
    }
    abort();
}

void
VisitorTest::getMessagesAndReply(
        int expectedCount,
        TestVisitorMessageSession& session,
        std::vector<Document::SP >& docs,
        std::vector<DocumentId>& docIds,
        std::vector<std::string>& infoMessages,
        api::ReturnCode::Result result)
{
    for (int i = 0; i < expectedCount; i++) {
        session.waitForMessages(1);
        mbus::Reply::UP reply;
        {
            std::lock_guard guard(session.getMonitor());
            ASSERT_FALSE(session.sentMessages.empty());
            std::unique_ptr<documentapi::DocumentMessage> msg(std::move(session.sentMessages.front()));
            session.sentMessages.pop_front();
            ASSERT_LT(msg->getPriority(), 16);

            switch (msg->getType()) {
            case documentapi::DocumentProtocol::MESSAGE_PUTDOCUMENT:
                docs.push_back(static_cast<documentapi::PutDocumentMessage&>(*msg).getDocumentSP());
                break;
            case documentapi::DocumentProtocol::MESSAGE_REMOVEDOCUMENT:
                docIds.push_back(static_cast<documentapi::RemoveDocumentMessage&>(*msg).getDocumentId());
                break;
            case documentapi::DocumentProtocol::MESSAGE_VISITORINFO:
                infoMessages.push_back(static_cast<documentapi::VisitorInfoMessage&>(*msg).getErrorMessage());
                break;
            default:
                break;
            }

            reply = msg->createReply();
            reply->swapState(*msg);

            reply->setMessage(std::move(msg));

            if (result != api::ReturnCode::OK) {
                reply->addError(mbus::Error(result, "Generic error"));
            }
        }
        session.reply(std::move(reply));
    }
}

void
VisitorTest::verifyCreateVisitorReply(
        api::ReturnCode::Result expectedResult,
        int checkStatsDocsVisited,
        int checkStatsBytesVisited,
        uint64_t* message_id_out)
{
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    ASSERT_EQ(1, replies.size());

    std::shared_ptr<api::StorageMessage> msg(replies[0]);

    ASSERT_EQ(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());

    auto reply = std::dynamic_pointer_cast<api::CreateVisitorReply>(msg);
    ASSERT_TRUE(reply.get());
    ASSERT_EQ(expectedResult, reply->getResult().getResult());

    if (checkStatsDocsVisited >= 0) {
        ASSERT_EQ(checkStatsDocsVisited,
                  reply->getVisitorStatistics().getDocumentsVisited());
    }
    if (checkStatsBytesVisited >= 0) {
        ASSERT_EQ(checkStatsBytesVisited,
                  reply->getVisitorStatistics().getBytesVisited());
    }

    if (message_id_out) {
        *message_id_out = reply->getMsgId();
    }
}

uint32_t
VisitorTest::getMatchingDocuments(std::vector<Document::SP >& docs) {
    uint32_t equalCount = 0;
    for (uint32_t i=0; i<docs.size(); ++i) {
        for (uint32_t j=0; j<_documents.size(); ++j) {
            if (*docs[i] == *_documents[j] &&
                docs[i]->getId() == _documents[j]->getId())
            {
                equalCount++;
            }
        }
    }

    return equalCount;
}

void
VisitorTest::sendGetIterReply(GetIterCommand& cmd,
                              const api::ReturnCode& result,
                              uint32_t maxDocuments,
                              bool overrideCompleted)
{
    GetIterReply::SP reply(new GetIterReply(cmd));
    if (result.failed()) {
        reply->setResult(result);
        _bottom->sendUp(reply);
        return;
    }
    assert(maxDocuments < _documents.size());
    size_t documentCount = maxDocuments != 0 ? maxDocuments : _documents.size();
    for (size_t i = 0; i < documentCount; ++i) {
        reply->getEntries().push_back(spi::DocEntry::create(spi::Timestamp(1000 + i), Document::UP(_documents[i]->clone())));
    }
    if (documentCount == _documents.size() || overrideCompleted) {
        reply->setCompleted();
    }
    _bottom->sendUp(reply);
}

template <typename T>
void
VisitorTest::fetchMultipleCommands(DummyStorageLink& link, size_t count,
                                   std::vector<std::shared_ptr<T>>& commands_out)
{
    link.waitForMessages(count, 60);
    std::vector<api::StorageMessage::SP> msgs(link.getCommandsOnce());
    std::vector<std::shared_ptr<T>> fetched;
    if (msgs.size() != count) {
        std::ostringstream oss;
        oss << "Expected "
            << count
            << " messages, got "
            << msgs.size()
            << ":\n";
        for (size_t i = 0; i < msgs.size(); ++i) {
            oss << i << ": " << *msgs[i] << "\n";
        }
        FAIL() << oss.str();
    }
    for (size_t i = 0; i < count; ++i) {
        auto ret = std::dynamic_pointer_cast<T>(msgs[i]);
        if (!ret) {
            std::ostringstream oss;
            oss << "Expected message of type "
                << typeid(T).name()
                << ", but got "
                << msgs[0]->toString();
            FAIL() << oss.str();
        }
        fetched.push_back(ret);
    }
    commands_out = std::move(fetched);
}

template <typename T>
void
VisitorTest::fetchSingleCommand(DummyStorageLink& link, std::shared_ptr<T>& msg_out)
{
    std::vector<std::shared_ptr<T>> ret;
    ASSERT_NO_FATAL_FAILURE(fetchMultipleCommands<T>(link, 1, ret));
    msg_out = std::move(ret[0]);
}

std::shared_ptr<api::CreateVisitorCommand>
VisitorTest::makeCreateVisitor(const VisitorOptions& options)
{
    static vespalib::string _storage("storage");
    api::StorageMessageAddress address(&_storage, lib::NodeType::STORAGE, 0);
    auto cmd = std::make_shared<api::CreateVisitorCommand>(
            makeBucketSpace(), options.visitorType, "testvis", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(address);
    cmd->setMaximumPendingReplyCount(UINT32_MAX);
    cmd->setControlDestination("foo/bar");
    cmd->setPriority(DefaultPriority);
    return cmd;
}

void
VisitorTest::sendCreateIteratorReply(uint64_t iteratorId)
{
    CreateIteratorCommand::SP createCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<CreateIteratorCommand>(*_bottom, createCmd));
    spi::IteratorId id(iteratorId);
    auto reply = std::make_shared<CreateIteratorReply>(*createCmd, id);
    _bottom->sendUp(reply);
}

TEST_F(VisitorTest, normal_usage) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = makeCreateVisitor();
    _top->sendDown(cmd);

    CreateIteratorCommand::SP createCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<CreateIteratorCommand>(*_bottom, createCmd));
    ASSERT_EQ(static_cast<int>(DefaultPriority),
              static_cast<int>(createCmd->getPriority())); // Inherit pri
    spi::IteratorId id(1234);
    auto reply = std::make_shared<CreateIteratorReply>(*createCmd, id);
    _bottom->sendUp(reply);

    GetIterCommand::SP getIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
    ASSERT_EQ(spi::IteratorId(1234), getIterCmd->getIteratorId());

    sendGetIterReply(*getIterCmd);

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(_documents.size(), getSession(0), docs, docIds, infoMessages);
    ASSERT_EQ(0, infoMessages.size());
    ASSERT_EQ(0, docIds.size());

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
    ASSERT_EQ(0, getFailedVisitorDestinationReplyCount());
}

TEST_F(VisitorTest, failed_create_iterator) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = makeCreateVisitor();
    cmd->addBucketToBeVisited(document::BucketId(16, 4));
    _top->sendDown(cmd);

    CreateIteratorCommand::SP createCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<CreateIteratorCommand>(*_bottom, createCmd));
    spi::IteratorId id(0);
    auto reply = std::make_shared<CreateIteratorReply>(*createCmd, id);
    reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE));
    _bottom->sendUp(reply);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE, 0, 0));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

TEST_F(VisitorTest, failed_get_iter) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = makeCreateVisitor();
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    GetIterCommand::SP getIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
    ASSERT_EQ(spi::IteratorId(1234), getIterCmd->getIteratorId());

    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND));

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUCKET_NOT_FOUND, 0, 0));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

TEST_F(VisitorTest, document_api_client_error) {
    initializeTest();
    auto cmd = makeCreateVisitor();
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    {
        GetIterCommand::SP getIterCmd;
        ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
        ASSERT_EQ(spi::IteratorId(1234), getIterCmd->getIteratorId());

        sendGetIterReply(*getIterCmd, api::ReturnCode(api::ReturnCode::OK), 1);
    }

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::INTERNAL_FAILURE);
    // INTERNAL_FAILURE is critical, so no visitor info sent
    ASSERT_EQ(0, infoMessages.size());

    std::this_thread::sleep_for(100ms);

    {
        GetIterCommand::SP getIterCmd;
        ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
        ASSERT_EQ(spi::IteratorId(1234), getIterCmd->getIteratorId());

        sendGetIterReply(*getIterCmd);
    }

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

TEST_F(VisitorTest, no_document_api_resending_for_failed_visitor) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    {
        GetIterCommand::SP getIterCmd;
        ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
        ASSERT_EQ(spi::IteratorId(1234), getIterCmd->getIteratorId());

        sendGetIterReply(*getIterCmd, api::ReturnCode(api::ReturnCode::OK), 2, true);
    }

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    // Use non-critical result. Visitor info message should be received
    // after we send a NOT_CONNECTED reply. Failing this message as well
    // should cause the entire visitor to fail.
    getMessagesAndReply(3, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::NOT_CONNECTED);
    ASSERT_EQ(1, infoMessages.size());
    EXPECT_EQ("[From content node 0] NOT_CONNECTED: Generic error",
              infoMessages[0]);

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::NOT_CONNECTED));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
    ASSERT_EQ(3, getFailedVisitorDestinationReplyCount());
}

TEST_F(VisitorTest, iterator_created_for_failed_visitor) {
    initializeTest(TestParams().parallelBuckets(2));
    auto cmd = makeCreateVisitor();
    cmd->addBucketToBeVisited(document::BucketId(16, 4));
    _top->sendDown(cmd);

    std::vector<CreateIteratorCommand::SP> createCmds;
    ASSERT_NO_FATAL_FAILURE(fetchMultipleCommands<CreateIteratorCommand>(*_bottom, 2, createCmds));
    {
        spi::IteratorId id(0);
        auto reply = std::make_shared<CreateIteratorReply>(*createCmds[0], id);
        reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE));
        _bottom->sendUp(reply);
    }
    {
        spi::IteratorId id(1234);
        auto reply = std::make_shared<CreateIteratorReply>(*createCmds[1], id);
        _bottom->sendUp(reply);
    }
    // Want to immediately receive destroyiterator for newly created
    // iterator, since we cannot use it anyway when the visitor has failed.
    DestroyIteratorCommand::SP destroyCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE, 0, 0));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

/**
 * Test that if a visitor fails to send a document API message outright
 * (i.e. a case where it will never get a reply), the session is failed
 * and the visitor terminates cleanly without counting the failed message
 * as pending.
 */
TEST_F(VisitorTest, failed_document_api_send) {
    ASSERT_NO_FATAL_FAILURE(initializeTest(TestParams().autoReplyError(
                mbus::Error(mbus::ErrorCode::HANDSHAKE_FAILED,
                    "abandon ship!"))));
    auto cmd = makeCreateVisitor();
    cmd->addBucketToBeVisited(document::BucketId(16, 4));
    _top->sendDown(cmd);

    sendCreateIteratorReply();
    GetIterCommand::SP getIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
    ASSERT_EQ(spi::IteratorId(1234), getIterCmd->getIteratorId());
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     2,
                     true);

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(
            static_cast<api::ReturnCode::Result>(
                    mbus::ErrorCode::HANDSHAKE_FAILED),
            0,
            0));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
    // We currently don't count failures to send in this metric; send failures
    // indicate a message bus problem and already log a warning when they happen
    ASSERT_EQ(0, getFailedVisitorDestinationReplyCount());
}

void
VisitorTest::sendInitialCreateVisitorAndGetIterRound()
{
    auto cmd = makeCreateVisitor();
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    {
        GetIterCommand::SP getIterCmd;
        ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
        sendGetIterReply(*getIterCmd, api::ReturnCode(api::ReturnCode::OK), 1, true);
    }
}

TEST_F(VisitorTest, no_visitor_notification_for_transient_failures) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    ASSERT_NO_FATAL_FAILURE(sendInitialCreateVisitorAndGetIterRound());

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    // Have to make sure time increases in visitor thread so that resend
    // times are reached.
    _node->getClock().setFakeCycleMode();
    // Should not get info message for BUCKET_DELETED, but resend of Put.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::BUCKET_DELETED);
    ASSERT_EQ(0, infoMessages.size());
    // Should not get info message for BUCKET_NOT_FOUND, but resend of Put.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::BUCKET_NOT_FOUND);
    ASSERT_EQ(0, infoMessages.size());
    // MessageBus error codes guaranteed to fit in return code result.
    // Should not get info message for SESSION_BUSY, but resend of Put.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        static_cast<api::ReturnCode::Result>(
                                mbus::ErrorCode::SESSION_BUSY));
    ASSERT_EQ(0, infoMessages.size());
    // WRONG_DISTRIBUTION should not be reported, as it will happen all the
    // time when initiating remote migrations et al.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::WRONG_DISTRIBUTION);
    ASSERT_EQ(0, infoMessages.size());

    // Complete message successfully to finish the visitor.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::OK);
    ASSERT_EQ(0, infoMessages.size());

    DestroyIteratorCommand::SP cmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, cmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

TEST_F(VisitorTest, notification_sent_if_transient_error_retried_many_times) {
    constexpr size_t retries = Visitor::TRANSIENT_ERROR_RETRIES_BEFORE_NOTIFY;

    ASSERT_NO_FATAL_FAILURE(initializeTest());
    sendInitialCreateVisitorAndGetIterRound();

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    // Have to make sure time increases in visitor thread so that resend
    // times are reached.
    _node->getClock().setFakeCycleMode();
    for (size_t attempt = 0; attempt < retries; ++attempt) {
        getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                            api::ReturnCode::WRONG_DISTRIBUTION);
        ASSERT_EQ(0, infoMessages.size());
    }
    // Should now have a client notification along for the ride.
    // This has to be ACKed as OK or the visitor will fail.
    getMessagesAndReply(2, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::OK);
    ASSERT_EQ(1, infoMessages.size());
    // TODO(vekterli) ideally we'd want to test that this happens only once
    // per message, but this seems frustratingly complex to do currently.
    DestroyIteratorCommand::SP cmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, cmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

void
VisitorTest::doCompleteVisitingSession(
        const std::shared_ptr<api::CreateVisitorCommand>& cmd,
        std::shared_ptr<api::CreateVisitorReply>& reply_out)
{
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    GetIterCommand::SP getIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     1,
                     true);

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages);

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    ASSERT_EQ(1, replies.size());

    std::shared_ptr<api::StorageMessage> msg(replies[0]);

    ASSERT_EQ(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
    reply_out = std::dynamic_pointer_cast<api::CreateVisitorReply>(msg);
}

TEST_F(VisitorTest, no_mbus_tracing_if_trace_level_is_zero) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    std::shared_ptr<api::CreateVisitorCommand> cmd(makeCreateVisitor());
    cmd->getTrace().setLevel(0);
    std::shared_ptr<api::CreateVisitorReply> reply;
    ASSERT_NO_FATAL_FAILURE(doCompleteVisitingSession(cmd, reply));
    EXPECT_TRUE(reply->getTrace().isEmpty());
}

TEST_F(VisitorTest, reply_contains_trace_if_trace_level_above_zero) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    std::shared_ptr<api::CreateVisitorCommand> cmd(makeCreateVisitor());
    cmd->getTrace().setLevel(1);
    cmd->getTrace().trace(1,"at least one trace.");
    std::shared_ptr<api::CreateVisitorReply> reply;
    ASSERT_NO_FATAL_FAILURE(doCompleteVisitingSession(cmd, reply));
    EXPECT_FALSE(reply->getTrace().isEmpty());
}

TEST_F(VisitorTest, no_more_iterators_sent_while_memory_used_above_limit) {
    initializeTest(TestParams().maxVisitorMemoryUsage(1)
                               .parallelBuckets(1));
    auto cmd = makeCreateVisitor();
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    GetIterCommand::SP getIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     1);

    // Pending Document API message towards client; memory usage should prevent
    // visitor from sending down additional GetIter messages until the pending
    // client message has been replied to and cleared from the internal state.
    getSession(0).waitForMessages(1);
    // Note that it's possible for this test to exhibit false negatives (but not
    // false positives) since the _absence_ of a message means we don't have any
    // kind of explicit barrier with which we can synchronize the test and the
    // running visitor thread.
    std::this_thread::sleep_for(100ms);
    ASSERT_EQ(0, _bottom->getNumCommands());

    std::vector<Document::SP> docs;
    std::vector<DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages);

    // 2nd round of GetIter now allowed. Send reply indicating completion.
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, getIterCmd));
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     1,
                     true);

    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages);

    DestroyIteratorCommand::SP destroyIterCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroyIterCmd));

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

void
VisitorTest::doTestVisitorInstanceHasConsistencyLevel(
        vespalib::stringref visitorType,
        spi::ReadConsistency expectedConsistency)
{
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor(VisitorOptions().withVisitorType(visitorType)));
    _top->sendDown(cmd);

    CreateIteratorCommand::SP createCmd;
    ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<CreateIteratorCommand>(*_bottom, createCmd));
    ASSERT_EQ(expectedConsistency, createCmd->getReadConsistency());
}

TEST_F(VisitorTest, dump_visitor_invokes_strong_read_consistency_iteration) {
    doTestVisitorInstanceHasConsistencyLevel(
            "dumpvisitor", spi::ReadConsistency::STRONG);
}

// NOTE: SearchVisitor cannot be tested here since it's in a separate module
// which depends on _this_ module for compilation. Instead we let TestVisitor
// use weak consistency, as this is just some internal stuff not used for/by
// any external client use cases. Our primary concern is to test that each
// visitor subclass might report its own read consistency requirement and that
// this is carried along to the CreateIteratorCommand.
TEST_F(VisitorTest, test_visitor_invokes_weak_read_consistency_iteration) {
    doTestVisitorInstanceHasConsistencyLevel(
            "testvisitor", spi::ReadConsistency::WEAK);
}

struct ReindexingVisitorTest : VisitorTest {
    void respond_with_docs_from_persistence() {
        sendCreateIteratorReply();
        GetIterCommand::SP get_iter_cmd;
        // Reply to GetIter with a single doc and bucket completed
        ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<GetIterCommand>(*_bottom, get_iter_cmd));
        sendGetIterReply(*get_iter_cmd, api::ReturnCode(api::ReturnCode::OK), 1, true);
    }

    void respond_to_client_put(api::ReturnCode::Result result) {
        // Reply to the Put from "client" back to the visitor
        std::vector<Document::SP> docs;
        std::vector<DocumentId> doc_ids;
        std::vector<std::string> info_messages;
        getMessagesAndReply(1, getSession(0), docs, doc_ids, info_messages, result);
    }

    void complete_visitor() {
        DestroyIteratorCommand::SP destroy_iter_cmd;
        ASSERT_NO_FATAL_FAILURE(fetchSingleCommand<DestroyIteratorCommand>(*_bottom, destroy_iter_cmd));
    }
};

TEST_F(ReindexingVisitorTest, puts_are_sent_with_tas_condition) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = makeCreateVisitor(VisitorOptions().withVisitorType("reindexingvisitor"));
    cmd->getParameters().set(reindexing_bucket_lock_visitor_parameter_key(), "foobar");
    _top->sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(respond_with_docs_from_persistence());
    auto& session = getSession(0);
    session.waitForMessages(1);

    ASSERT_EQ(session.sentMessages.size(), 1u);
    auto* put_cmd = dynamic_cast<documentapi::PutDocumentMessage*>(session.sentMessages.front().get());
    ASSERT_TRUE(put_cmd);
    auto token_str = vespalib::make_string("%s=foobar", reindexing_bucket_lock_bypass_prefix());
    EXPECT_EQ(put_cmd->getCondition().getSelection(), token_str);

    ASSERT_NO_FATAL_FAILURE(respond_to_client_put(api::ReturnCode::OK));
    ASSERT_NO_FATAL_FAILURE(complete_visitor());

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}


TEST_F(ReindexingVisitorTest, tas_responses_fail_the_visitor_and_are_rewritten_to_aborted) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = makeCreateVisitor(VisitorOptions().withVisitorType("reindexingvisitor"));
    cmd->getParameters().set(reindexing_bucket_lock_visitor_parameter_key(), "foobar");
    _top->sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(respond_with_docs_from_persistence());
    ASSERT_NO_FATAL_FAILURE(respond_to_client_put(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED));
    ASSERT_NO_FATAL_FAILURE(complete_visitor());

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::ABORTED, -1, -1));
    ASSERT_TRUE(waitUntilNoActiveVisitors());
}

} // namespace storage
