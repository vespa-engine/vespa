// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/visiting/visitormanager.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/storageserver/testvisitormessagesession.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/config/common/exceptions.h>
#include <thread>

using namespace std::chrono_literals;
using document::test::makeBucketSpace;

namespace storage {

namespace {

using msg_ptr_vector = std::vector<api::StorageMessage::SP>;

struct TestParams
{
    TestParams& iteratorsPerBucket(uint32_t n) {
        _iteratorsPerBucket = n;
        return *this;
    }
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

    uint32_t _iteratorsPerBucket {1};
    uint32_t _maxVisitorMemoryUsage {UINT32_MAX};
    uint32_t _parallelBuckets {1};
    mbus::Error _autoReplyError;
};

}

class VisitorTest : public CppUnit::TestFixture
{
private:
    CPPUNIT_TEST_SUITE(VisitorTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testFailedCreateIterator);
    CPPUNIT_TEST(testFailedGetIter);
    CPPUNIT_TEST(testMultipleFailedGetIter);
    CPPUNIT_TEST(testDocumentAPIClientError);
    CPPUNIT_TEST(testNoDocumentAPIResendingForFailedVisitor);
    CPPUNIT_TEST(testIteratorCreatedForFailedVisitor);
    CPPUNIT_TEST(testFailedDocumentAPISend);
    CPPUNIT_TEST(testNoVisitorNotificationForTransientFailures);
    CPPUNIT_TEST(testNotificationSentIfTransientErrorRetriedManyTimes);
    CPPUNIT_TEST(testNoMbusTracingIfTraceLevelIsZero);
    CPPUNIT_TEST(testReplyContainsTraceIfTraceLevelAboveZero);
    CPPUNIT_TEST(testNoMoreIteratorsSentWhileMemoryUsedAboveLimit);
    CPPUNIT_TEST(testDumpVisitorInvokesStrongReadConsistencyIteration);
    CPPUNIT_TEST(testTestVisitorInvokesWeakReadConsistencyIteration);
    CPPUNIT_TEST_SUITE_END();

    static uint32_t docCount;
    std::vector<document::Document::SP > _documents;
    std::unique_ptr<TestVisitorMessageSessionFactory> _messageSessionFactory;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    DummyStorageLink* _bottom;
    VisitorManager* _manager;

public:
    VisitorTest() : _node() {}

    void testNormalUsage();
    void testFailedCreateIterator();
    void testFailedGetIter();
    void testMultipleFailedGetIter();
    void testDocumentAPIClientError();
    void testNoDocumentAPIResendingForFailedVisitor();
    void testIteratorCreatedForFailedVisitor();
    void testFailedDocumentAPISend();
    void testNoVisitorNotificationForTransientFailures();
    void testNotificationSentIfTransientErrorRetriedManyTimes();
    void testNoMbusTracingIfTraceLevelIsZero();
    void testReplyContainsTraceIfTraceLevelAboveZero();
    void testNoMoreIteratorsSentWhileMemoryUsedAboveLimit();
    void testDumpVisitorInvokesStrongReadConsistencyIteration();
    void testTestVisitorInvokesWeakReadConsistencyIteration();
    // TODO:
    void testVisitMultipleBuckets() {}

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

    std::shared_ptr<api::CreateVisitorCommand> makeCreateVisitor(
            const VisitorOptions& options = VisitorOptions());
    void tearDown() override;
    bool waitUntilNoActiveVisitors();
    TestVisitorMessageSession& getSession(uint32_t n);
    uint64_t verifyCreateVisitorReply(
            api::ReturnCode::Result expectedResult,
            int checkStatsDocsVisited = -1,
            int checkStatsBytesVisited = -1);
    void getMessagesAndReply(
            int expectedCount,
            TestVisitorMessageSession& session,
            std::vector<document::Document::SP >& docs,
            std::vector<document::DocumentId>& docIds,
            std::vector<std::string>& infoMessages,
            api::ReturnCode::Result returnCode = api::ReturnCode::OK);
    uint32_t getMatchingDocuments(std::vector<document::Document::SP >& docs);

private:
    void doTestVisitorInstanceHasConsistencyLevel(
            vespalib::stringref visitorType,
            spi::ReadConsistency expectedConsistency);

    template <typename T>
    std::vector<std::shared_ptr<T> >
    fetchMultipleCommands(DummyStorageLink& link, size_t count);

    template <typename T>
    std::shared_ptr<T>
    fetchSingleCommand(DummyStorageLink& link);

    void sendGetIterReply(GetIterCommand& cmd,
                          const api::ReturnCode& result =
                          api::ReturnCode(api::ReturnCode::OK),
                          uint32_t maxDocuments = 0,
                          bool overrideCompleted = false);
    void sendCreateIteratorReply(uint64_t iteratorId = 1234);
    std::shared_ptr<api::CreateVisitorReply> doCompleteVisitingSession(
            const std::shared_ptr<api::CreateVisitorCommand>& cmd);

    void sendInitialCreateVisitorAndGetIterRound();

    int64_t getFailedVisitorDestinationReplyCount() const {
        // There's no metric manager attached to these tests, so even if the
        // test should magically freeze here for 5+ minutes, nothing should
        // come in and wipe our accumulated failure metrics.
        // Only 1 visitor thread running, so we know it has the metrics.
        const auto& metrics = _manager->getThread(0).getMetrics();
        auto loadType = documentapi::LoadType::DEFAULT;
        return metrics.visitorDestinationFailureReplies[loadType].getCount();
    }
};

uint32_t VisitorTest::docCount = 10;

CPPUNIT_TEST_SUITE_REGISTRATION(VisitorTest);

void
VisitorTest::initializeTest(const TestParams& params)
{
    vdstestlib::DirConfig config(getStandardConfig(true, "visitortest"));
    config.getConfig("stor-visitor").set("visitorthreads", "1");
    config.getConfig("stor-visitor").set(
            "iterators_per_bucket",
            std::to_string(params._iteratorsPerBucket));
    config.getConfig("stor-visitor").set(
            "defaultparalleliterators",
            std::to_string(params._parallelBuckets));
    config.getConfig("stor-visitor").set(
            "visitor_memory_usage_limit",
            std::to_string(params._maxVisitorMemoryUsage));

    std::string rootFolder = getRootFolder(config);

    system(vespalib::make_string("chmod 755 %s 2>/dev/null", rootFolder.c_str()).c_str());
    system(vespalib::make_string("rm -rf %s* 2>/dev/null", rootFolder.c_str()).c_str());
    assert(system(vespalib::make_string("mkdir -p %s/disks/d0", rootFolder.c_str()).c_str()) == 0);
    assert(system(vespalib::make_string("mkdir -p %s/disks/d1", rootFolder.c_str()).c_str()) == 0);

    try {
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
                    config.getConfigId(),
                    _node->getComponentRegister(), *_messageSessionFactory)));
        _bottom = new DummyStorageLink();
        _top->push_back(std::unique_ptr<StorageLink>(_bottom));
        _manager->setTimeBetweenTicks(10);
        _top->open();
    } catch (config::InvalidConfigException& e) {
        fprintf(stderr, "%s\n", e.what());
    }
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
        uri << "userdoc:test:" << i % 10 << ":http://www.ntnu.no/"
            << i << ".html";

        _documents.push_back(document::Document::SP(
                _node->getTestDocMan().createDocument(content, uri.str())));
        const document::DocumentType& type(_documents.back()->getType());
        _documents.back()->setValue(type.getField("headerval"),
                                    document::IntFieldValue(i % 4));
    }
}

void
VisitorTest::tearDown()
{
    if (_top.get() != 0) {
        _top->close();
        _top->flush();
        _top.reset(0);
    }
    _node.reset(0);
    _messageSessionFactory.reset(0);
    _manager = 0;
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
            vespalib::LockGuard lock(_messageSessionFactory->_accessLock);
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
    throw std::logic_error("unreachable");
}

void
VisitorTest::getMessagesAndReply(
        int expectedCount,
        TestVisitorMessageSession& session,
        std::vector<document::Document::SP >& docs,
        std::vector<document::DocumentId>& docIds,
        std::vector<std::string>& infoMessages,
        api::ReturnCode::Result result)
{
    for (int i = 0; i < expectedCount; i++) {
        session.waitForMessages(1);
        mbus::Reply::UP reply;
        {
            vespalib::MonitorGuard guard(session.getMonitor());
            CPPUNIT_ASSERT(!session.sentMessages.empty());
            std::unique_ptr<documentapi::DocumentMessage> msg(std::move(session.sentMessages.front()));
            session.sentMessages.pop_front();
            CPPUNIT_ASSERT(msg->getPriority() < 16);

            switch (msg->getType()) {
            case documentapi::DocumentProtocol::MESSAGE_PUTDOCUMENT:
                docs.push_back(
                        static_cast<documentapi::PutDocumentMessage&>(*msg).getDocumentSP());
                break;
            case documentapi::DocumentProtocol::MESSAGE_REMOVEDOCUMENT:
                docIds.push_back(
                        static_cast<documentapi::RemoveDocumentMessage&>(*msg).getDocumentId());
                break;
            case documentapi::DocumentProtocol::MESSAGE_VISITORINFO:
                infoMessages.push_back(static_cast<documentapi::VisitorInfoMessage&>(*msg).getErrorMessage());
                break;
            default:
                break;
            }

            reply = msg->createReply();
            reply->swapState(*msg);

            reply->setMessage(mbus::Message::UP(msg.release()));

            if (result != api::ReturnCode::OK) {
                reply->addError(mbus::Error(result, "Generic error"));
            }
        }
        session.reply(std::move(reply));
    }
}

uint64_t
VisitorTest::verifyCreateVisitorReply(
        api::ReturnCode::Result expectedResult,
        int checkStatsDocsVisited,
        int checkStatsBytesVisited)
{
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    CPPUNIT_ASSERT_EQUAL(1, (int)replies.size());

    std::shared_ptr<api::StorageMessage> msg(replies[0]);

    CPPUNIT_ASSERT_EQUAL(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());

    std::shared_ptr<api::CreateVisitorReply> reply(
            std::dynamic_pointer_cast<api::CreateVisitorReply>(msg));
    CPPUNIT_ASSERT(reply.get());
    CPPUNIT_ASSERT_EQUAL(expectedResult, reply->getResult().getResult());

    if (checkStatsDocsVisited >= 0) {
        CPPUNIT_ASSERT_EQUAL(checkStatsDocsVisited,
                             int(reply->getVisitorStatistics().getDocumentsVisited()));
    }
    if (checkStatsBytesVisited >= 0) {
        CPPUNIT_ASSERT_EQUAL(checkStatsBytesVisited,
                             int(reply->getVisitorStatistics().getBytesVisited()));
    }

    return reply->getMsgId();
}

uint32_t
VisitorTest::getMatchingDocuments(std::vector<document::Document::SP >& docs) {
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
        reply->getEntries().push_back(
                spi::DocEntry::UP(
                        new spi::DocEntry(
                                spi::Timestamp(1000 + i),
                                spi::NONE,
                                document::Document::UP(_documents[i]->clone()))));
    }
    if (documentCount == _documents.size() || overrideCompleted) {
        reply->setCompleted();
    }
    _bottom->sendUp(reply);
}

template <typename T>
std::vector<std::shared_ptr<T> >
VisitorTest::fetchMultipleCommands(DummyStorageLink& link, size_t count)
{
    link.waitForMessages(count, 60);
    std::vector<api::StorageMessage::SP> msgs(link.getCommandsOnce());
    std::vector<std::shared_ptr<T> > fetched;
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
        CPPUNIT_FAIL(oss.str());
    }
    for (size_t i = 0; i < count; ++i) {
        std::shared_ptr<T> ret(std::dynamic_pointer_cast<T>(msgs[i]));
        if (!ret) {
            std::ostringstream oss;
            oss << "Expected message of type "
                << typeid(T).name()
                << ", but got "
                << msgs[0]->toString();
            CPPUNIT_FAIL(oss.str());
        }
        fetched.push_back(ret);
    }
    return fetched;
}

template <typename T>
std::shared_ptr<T>
VisitorTest::fetchSingleCommand(DummyStorageLink& link)
{
    std::vector<std::shared_ptr<T> > ret(
            fetchMultipleCommands<T>(link, 1));
    return ret[0];
}

std::shared_ptr<api::CreateVisitorCommand>
VisitorTest::makeCreateVisitor(const VisitorOptions& options)
{
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), options.visitorType, "testvis", ""));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(address);
    cmd->setMaximumPendingReplyCount(UINT32_MAX);
    cmd->setControlDestination("foo/bar");
    return cmd;
}

void
VisitorTest::sendCreateIteratorReply(uint64_t iteratorId)
{
    CreateIteratorCommand::SP createCmd(
            fetchSingleCommand<CreateIteratorCommand>(*_bottom));
    spi::IteratorId id(iteratorId);
    api::StorageReply::SP reply(
            new CreateIteratorReply(*createCmd, id));
    _bottom->sendUp(reply);
}

void
VisitorTest::testNormalUsage()
{
    initializeTest();
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);

    CreateIteratorCommand::SP createCmd(
            fetchSingleCommand<CreateIteratorCommand>(*_bottom));
    CPPUNIT_ASSERT_EQUAL(uint8_t(0), createCmd->getPriority()); // Highest pri
    spi::IteratorId id(1234);
    api::StorageReply::SP reply(
            new CreateIteratorReply(*createCmd, id));
    _bottom->sendUp(reply);

    GetIterCommand::SP getIterCmd(
            fetchSingleCommand<GetIterCommand>(*_bottom));
    CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1234),
                         getIterCmd->getIteratorId());

    sendGetIterReply(*getIterCmd);

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(_documents.size(), getSession(0), docs, docIds, infoMessages);
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());
    CPPUNIT_ASSERT_EQUAL(size_t(0), docIds.size());

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::OK);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
    CPPUNIT_ASSERT_EQUAL(0L, getFailedVisitorDestinationReplyCount());
}

void
VisitorTest::testFailedCreateIterator()
{
    initializeTest();
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    cmd->addBucketToBeVisited(document::BucketId(16, 4));
    _top->sendDown(cmd);

    CreateIteratorCommand::SP createCmd(
            fetchSingleCommand<CreateIteratorCommand>(*_bottom));
    spi::IteratorId id(0);
    api::StorageReply::SP reply(
            new CreateIteratorReply(*createCmd, id));
    reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE));
    _bottom->sendUp(reply);

    verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE, 0, 0);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

void
VisitorTest::testFailedGetIter()
{
    initializeTest();
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    GetIterCommand::SP getIterCmd(
            fetchSingleCommand<GetIterCommand>(*_bottom));
    CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1234),
                         getIterCmd->getIteratorId());

    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND));

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::BUCKET_NOT_FOUND, 0, 0);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

void
VisitorTest::testMultipleFailedGetIter()
{
    initializeTest(TestParams().iteratorsPerBucket(2));
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    std::vector<GetIterCommand::SP> getIterCmds(
            fetchMultipleCommands<GetIterCommand>(*_bottom, 2));

    sendGetIterReply(*getIterCmds[0],
                     api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND));

    // Wait for an "appropriate" amount of time so that wrongful logic
    // will send a DestroyIteratorCommand before all pending GetIters
    // have been replied to.
    std::this_thread::sleep_for(100ms);

    CPPUNIT_ASSERT_EQUAL(size_t(0), _bottom->getNumCommands());

    sendGetIterReply(*getIterCmds[1],
                     api::ReturnCode(api::ReturnCode::BUCKET_DELETED));

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::BUCKET_DELETED, 0, 0);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

void
VisitorTest::testDocumentAPIClientError()
{
    initializeTest();
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    {
        GetIterCommand::SP getIterCmd(
                fetchSingleCommand<GetIterCommand>(*_bottom));
        CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1234),
                             getIterCmd->getIteratorId());

        sendGetIterReply(*getIterCmd, api::ReturnCode(api::ReturnCode::OK), 1);
    }

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::INTERNAL_FAILURE);
    // INTERNAL_FAILURE is critical, so no visitor info sent
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());

    std::this_thread::sleep_for(100ms);

    {
        GetIterCommand::SP getIterCmd(
                fetchSingleCommand<GetIterCommand>(*_bottom));
        CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1234),
                             getIterCmd->getIteratorId());

        sendGetIterReply(*getIterCmd);
    }

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

void
VisitorTest::testNoDocumentAPIResendingForFailedVisitor()
{
    initializeTest();
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    {
        GetIterCommand::SP getIterCmd(
                fetchSingleCommand<GetIterCommand>(*_bottom));
        CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1234),
                             getIterCmd->getIteratorId());

        sendGetIterReply(*getIterCmd, api::ReturnCode(api::ReturnCode::OK), 2, true);
    }

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    // Use non-critical result. Visitor info message should be received
    // after we send a NOT_CONNECTED reply. Failing this message as well
    // should cause the entire visitor to fail.
    getMessagesAndReply(3, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::NOT_CONNECTED);
    CPPUNIT_ASSERT_EQUAL(size_t(1), infoMessages.size());
    CPPUNIT_ASSERT_EQUAL(
            std::string("[From content node 0] NOT_CONNECTED: Generic error"),
            infoMessages[0]);

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::NOT_CONNECTED);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
    CPPUNIT_ASSERT_EQUAL(3L, getFailedVisitorDestinationReplyCount());
}

void
VisitorTest::testIteratorCreatedForFailedVisitor()
{
    initializeTest(TestParams().iteratorsPerBucket(1).parallelBuckets(2));
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    cmd->addBucketToBeVisited(document::BucketId(16, 4));
    _top->sendDown(cmd);

    std::vector<CreateIteratorCommand::SP> createCmds(
            fetchMultipleCommands<CreateIteratorCommand>(*_bottom, 2));
    {
        spi::IteratorId id(0);
        api::StorageReply::SP reply(
                new CreateIteratorReply(*createCmds[0], id));
        reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE));
        _bottom->sendUp(reply);
    }
    {
        spi::IteratorId id(1234);
        api::StorageReply::SP reply(
                new CreateIteratorReply(*createCmds[1], id));
        _bottom->sendUp(reply);
    }
    // Want to immediately receive destroyiterator for newly created
    // iterator, since we cannot use it anyway when the visitor has failed.
    DestroyIteratorCommand::SP destroyCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE, 0, 0);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

/**
 * Test that if a visitor fails to send a document API message outright
 * (i.e. a case where it will never get a reply), the session is failed
 * and the visitor terminates cleanly without counting the failed message
 * as pending.
 */
void
VisitorTest::testFailedDocumentAPISend()
{
    initializeTest(TestParams().autoReplyError(
                mbus::Error(mbus::ErrorCode::HANDSHAKE_FAILED,
                    "abandon ship!")));
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    cmd->addBucketToBeVisited(document::BucketId(16, 4));
    _top->sendDown(cmd);

    sendCreateIteratorReply();
    GetIterCommand::SP getIterCmd(
            fetchSingleCommand<GetIterCommand>(*_bottom));
    CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1234),
                         getIterCmd->getIteratorId());
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     2,
                     true);

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(
            static_cast<api::ReturnCode::Result>(
                    mbus::ErrorCode::HANDSHAKE_FAILED),
            0,
            0);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
    // We currently don't count failures to send in this metric; send failures
    // indicate a message bus problem and already log a warning when they happen
    CPPUNIT_ASSERT_EQUAL(0L, getFailedVisitorDestinationReplyCount());
}

void
VisitorTest::sendInitialCreateVisitorAndGetIterRound()
{
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    {
        GetIterCommand::SP getIterCmd(
                fetchSingleCommand<GetIterCommand>(*_bottom));
        sendGetIterReply(*getIterCmd, api::ReturnCode(api::ReturnCode::OK),
                         1, true);
    }
}

void
VisitorTest::testNoVisitorNotificationForTransientFailures()
{
    initializeTest();
    sendInitialCreateVisitorAndGetIterRound();

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    // Have to make sure time increases in visitor thread so that resend
    // times are reached.
    _node->getClock().setFakeCycleMode();
    // Should not get info message for BUCKET_DELETED, but resend of Put.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::BUCKET_DELETED);
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());
    // Should not get info message for BUCKET_NOT_FOUND, but resend of Put.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::BUCKET_NOT_FOUND);
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());
    // MessageBus error codes guaranteed to fit in return code result.
    // Should not get info message for SESSION_BUSY, but resend of Put.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        static_cast<api::ReturnCode::Result>(
                                mbus::ErrorCode::SESSION_BUSY));
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());
    // WRONG_DISTRIBUTION should not be reported, as it will happen all the
    // time when initiating remote migrations et al.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::WRONG_DISTRIBUTION);
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());

    // Complete message successfully to finish the visitor.
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::OK);
    CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());

    fetchSingleCommand<DestroyIteratorCommand>(*_bottom);

    verifyCreateVisitorReply(api::ReturnCode::OK);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

void
VisitorTest::testNotificationSentIfTransientErrorRetriedManyTimes()
{
    constexpr size_t retries(
        Visitor::TRANSIENT_ERROR_RETRIES_BEFORE_NOTIFY);

    initializeTest();
    sendInitialCreateVisitorAndGetIterRound();

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    // Have to make sure time increases in visitor thread so that resend
    // times are reached.
    _node->getClock().setFakeCycleMode();
    for (size_t attempt = 0; attempt < retries; ++attempt) {
        getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages,
                            api::ReturnCode::WRONG_DISTRIBUTION);
        CPPUNIT_ASSERT_EQUAL(size_t(0), infoMessages.size());
    }
    // Should now have a client notification along for the ride.
    // This has to be ACKed as OK or the visitor will fail.
    getMessagesAndReply(2, getSession(0), docs, docIds, infoMessages,
                        api::ReturnCode::OK);
    CPPUNIT_ASSERT_EQUAL(size_t(1), infoMessages.size());
    // TODO(vekterli) ideally we'd want to test that this happens only once
    // per message, but this seems frustratingly complex to do currently.
    fetchSingleCommand<DestroyIteratorCommand>(*_bottom);

    verifyCreateVisitorReply(api::ReturnCode::OK);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

std::shared_ptr<api::CreateVisitorReply>
VisitorTest::doCompleteVisitingSession(
        const std::shared_ptr<api::CreateVisitorCommand>& cmd)
{
    initializeTest();
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    GetIterCommand::SP getIterCmd(
            fetchSingleCommand<GetIterCommand>(*_bottom));
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     1,
                     true);

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages);

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    CPPUNIT_ASSERT_EQUAL(size_t(1), replies.size());

    std::shared_ptr<api::StorageMessage> msg(replies[0]);

    CPPUNIT_ASSERT_EQUAL(api::MessageType::VISITOR_CREATE_REPLY,
                         msg->getType());
    return std::dynamic_pointer_cast<api::CreateVisitorReply>(msg);
}

void
VisitorTest::testNoMbusTracingIfTraceLevelIsZero()
{
    std::shared_ptr<api::CreateVisitorCommand> cmd(makeCreateVisitor());
    cmd->getTrace().setLevel(0);
    auto reply = doCompleteVisitingSession(cmd);
    CPPUNIT_ASSERT(reply->getTrace().getRoot().isEmpty());
}

void
VisitorTest::testReplyContainsTraceIfTraceLevelAboveZero()
{
    std::shared_ptr<api::CreateVisitorCommand> cmd(makeCreateVisitor());
    cmd->getTrace().setLevel(1);
    auto reply = doCompleteVisitingSession(cmd);
    CPPUNIT_ASSERT(!reply->getTrace().getRoot().isEmpty());
}

void
VisitorTest::testNoMoreIteratorsSentWhileMemoryUsedAboveLimit()
{
    initializeTest(TestParams().maxVisitorMemoryUsage(1)
                               .parallelBuckets(1)
                               .iteratorsPerBucket(1));
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor());
    _top->sendDown(cmd);
    sendCreateIteratorReply();

    GetIterCommand::SP getIterCmd(
            fetchSingleCommand<GetIterCommand>(*_bottom));
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
    CPPUNIT_ASSERT_EQUAL(size_t(0), _bottom->getNumCommands());

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    std::vector<std::string> infoMessages;
    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages);

    // 2nd round of GetIter now allowed. Send reply indicating completion.
    getIterCmd = fetchSingleCommand<GetIterCommand>(*_bottom);
    sendGetIterReply(*getIterCmd,
                     api::ReturnCode(api::ReturnCode::OK),
                     1,
                     true);

    getMessagesAndReply(1, getSession(0), docs, docIds, infoMessages);

    DestroyIteratorCommand::SP destroyIterCmd(
            fetchSingleCommand<DestroyIteratorCommand>(*_bottom));

    verifyCreateVisitorReply(api::ReturnCode::OK);
    CPPUNIT_ASSERT(waitUntilNoActiveVisitors());
}

void
VisitorTest::doTestVisitorInstanceHasConsistencyLevel(
        vespalib::stringref visitorType,
        spi::ReadConsistency expectedConsistency)
{
    initializeTest();
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            makeCreateVisitor(VisitorOptions().withVisitorType(visitorType)));
    _top->sendDown(cmd);

    auto createCmd = fetchSingleCommand<CreateIteratorCommand>(*_bottom);
    CPPUNIT_ASSERT_EQUAL(expectedConsistency,
                         createCmd->getReadConsistency());
}

void
VisitorTest::testDumpVisitorInvokesStrongReadConsistencyIteration()
{
    doTestVisitorInstanceHasConsistencyLevel(
            "dumpvisitor", spi::ReadConsistency::STRONG);
}

// NOTE: SearchVisitor cannot be tested here since it's in a separate module
// which depends on _this_ module for compilation. Instead we let TestVisitor
// use weak consistency, as this is just some internal stuff not used for/by
// any external client use cases. Our primary concern is to test that each
// visitor subclass might report its own read consistency requirement and that
// this is carried along to the CreateIteratorCommand.
void
VisitorTest::testTestVisitorInvokesWeakReadConsistencyIteration()
{
    doTestVisitorInstanceHasConsistencyLevel(
            "testvisitor", spi::ReadConsistency::WEAK);
}

} // namespace storage
