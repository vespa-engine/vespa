// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/visiting/visitormanager.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <tests/storageserver/testvisitormessagesession.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/config/common/exceptions.h>
#include <optional>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using documentapi::Priority;

namespace storage {
namespace {
    typedef std::vector<api::StorageMessage::SP> msg_ptr_vector;
}

class VisitorManagerTest : public CppUnit::TestFixture
{
private:
    CPPUNIT_TEST_SUITE(VisitorManagerTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testResending);
    CPPUNIT_TEST(testVisitEmptyBucket);
    CPPUNIT_TEST(testMultiBucketVisit);
    CPPUNIT_TEST(testNoBuckets);
    CPPUNIT_TEST(testVisitPutsAndRemoves);
    CPPUNIT_TEST(testVisitWithTimeframeAndSelection);
    CPPUNIT_TEST(testVisitWithTimeframeAndBogusSelection);
    CPPUNIT_TEST(testVisitorCallbacks);
    CPPUNIT_TEST(testVisitorCleanup);
    CPPUNIT_TEST(testAbortOnFailedVisitorInfo);
    CPPUNIT_TEST(testAbortOnFieldPathError);
    CPPUNIT_TEST(testVisitorQueueTimeout);
    CPPUNIT_TEST(testVisitorProcessingTimeout);
    CPPUNIT_TEST(testPrioritizedVisitorQueing);
    CPPUNIT_TEST(testPrioritizedMaxConcurrentVisitors);
    CPPUNIT_TEST(testVisitorQueingZeroQueueSize);
    CPPUNIT_TEST(testHitCounter);
    CPPUNIT_TEST(testStatusPage);
    CPPUNIT_TEST_SUITE_END();

    static uint32_t docCount;
    std::vector<document::Document::SP > _documents;
    std::unique_ptr<TestVisitorMessageSessionFactory> _messageSessionFactory;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    VisitorManager* _manager;

public:
    VisitorManagerTest() : _node() {}

        // Not using setUp since can't throw exception out of it.
    void initializeTest();
    void addSomeRemoves(bool removeAll = false);
    void tearDown() override;
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
            api::ReturnCode::Result returnCode = api::ReturnCode::OK,
            std::optional<Priority::Value> priority = documentapi::Priority::PRI_NORMAL_4);
    uint32_t getMatchingDocuments(std::vector<document::Document::SP >& docs);
    void finishAndWaitForVisitorSessionCompletion(uint32_t sessionIndex);

    void testNormalUsage();
    void testResending();
    void testVisitEmptyBucket();
    void testMultiBucketVisit();
    void testNoBuckets();
    void testVisitPutsAndRemoves();
    void testVisitWithTimeframeAndSelection();
    void testVisitWithTimeframeAndBogusSelection();
    void testVisitorCallbacks();
    void testVisitorCleanup();
    void testAbortOnFailedVisitorInfo();
    void testAbortOnFieldPathError();
    void testVisitorQueueTimeout();
    void testVisitorProcessingTimeout();
    void testPrioritizedVisitorQueing();
    void testPrioritizedMaxConcurrentVisitors();
    void testVisitorQueingZeroQueueSize();
    void testHitCounter();
    void testStatusPage();
};

uint32_t VisitorManagerTest::docCount = 10;

CPPUNIT_TEST_SUITE_REGISTRATION(VisitorManagerTest);

void
VisitorManagerTest::initializeTest()
{
    vdstestlib::DirConfig config(getStandardConfig(true));
    config.getConfig("stor-visitor").set("visitorthreads", "1");

    try {
        _messageSessionFactory.reset(
                new TestVisitorMessageSessionFactory(config.getConfigId()));
        _node.reset(
                new TestServiceLayerApp(config.getConfigId()));
        _node->setupDummyPersistence();
        _node->getStateUpdater().setClusterState(
                lib::ClusterState::CSP(
                        new lib::ClusterState("storage:1 distributor:1")));
        _top.reset(new DummyStorageLink());
        _top->push_back(std::unique_ptr<StorageLink>(_manager
                = new VisitorManager(
                    config.getConfigId(), _node->getComponentRegister(),
                    *_messageSessionFactory)));
        _top->push_back(std::unique_ptr<StorageLink>(new FileStorManager(
                config.getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
        _manager->setTimeBetweenTicks(10);
        _top->open();
    } catch (config::InvalidConfigException& e) {
        fprintf(stderr, "%s\n", e.what());
    }
        // Adding some documents so database isn't empty
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
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
    for (uint32_t i=0; i<10; ++i) {
        document::BucketId bid(16, i);

        std::shared_ptr<api::CreateBucketCommand> cmd(
                new api::CreateBucketCommand(makeDocumentBucket(bid)));
        cmd->setAddress(address);
        cmd->setSourceIndex(0);
        _top->sendDown(cmd);
        _top->waitForMessages(1, 60);
        _top->reset();

        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bid, "",
                    StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry->disk = 0;
        entry.write();
    }
    for (uint32_t i=0; i<docCount; ++i) {
        document::BucketId bid(16, i);

        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), _documents[i], i+1));
        cmd->setAddress(address);
        _top->sendDown(cmd);
        _top->waitForMessages(1, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
        CPPUNIT_ASSERT_EQUAL((size_t) 1, replies.size());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    replies[0]));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                             reply->getResult());
    }
}

void
VisitorManagerTest::addSomeRemoves(bool removeAll)
{
    framework::defaultimplementation::FakeClock clock;
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    for (uint32_t i=0; i<docCount; i += (removeAll ? 1 : 4)) {
            // Add it to the database
        document::BucketId bid(16, i % 10);
        std::shared_ptr<api::RemoveCommand> cmd(
                new api::RemoveCommand(
                        makeDocumentBucket(bid), _documents[i]->getId(), clock.getTimeInMicros().getTime() + docCount + i + 1));
        cmd->setAddress(address);
        _top->sendDown(cmd);
        _top->waitForMessages(1, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
        CPPUNIT_ASSERT_EQUAL((size_t) 1, replies.size());
        std::shared_ptr<api::RemoveReply> reply(
                std::dynamic_pointer_cast<api::RemoveReply>(
                    replies[0]));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                             reply->getResult());
    }
}

void
VisitorManagerTest::tearDown()
{
    if (_top) {
        assert(_top->getNumReplies() == 0);
        _top->close();
        _top->flush();
        _top.reset();
    }
    _node.reset();
    _messageSessionFactory.reset();
    _manager = nullptr;
}

TestVisitorMessageSession&
VisitorManagerTest::getSession(uint32_t n)
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
        FastOS_Thread::Sleep(10);
    }
    throw std::logic_error("unreachable");
}

void
VisitorManagerTest::getMessagesAndReply(
        int expectedCount,
        TestVisitorMessageSession& session,
        std::vector<document::Document::SP >& docs,
        std::vector<document::DocumentId>& docIds,
        api::ReturnCode::Result result,
        std::optional<Priority::Value> priority)
{
    for (int i = 0; i < expectedCount; i++) {
        session.waitForMessages(i + 1);
        mbus::Reply::UP reply;
        {
            vespalib::MonitorGuard guard(session.getMonitor());

            if (priority) {
                CPPUNIT_ASSERT_EQUAL(*priority,
                                     session.sentMessages[i]->getPriority());
            }

            switch (session.sentMessages[i]->getType()) {
            case documentapi::DocumentProtocol::MESSAGE_PUTDOCUMENT:
                docs.push_back(static_cast<documentapi::PutDocumentMessage&>(
                                       *session.sentMessages[i]).getDocumentSP());
                break;
            case documentapi::DocumentProtocol::MESSAGE_REMOVEDOCUMENT:
                docIds.push_back(static_cast<documentapi::RemoveDocumentMessage&>(
                                       *session.sentMessages[i]).getDocumentId());
                break;
            default:
                break;
            }

            reply = session.sentMessages[i]->createReply();
            reply->swapState(*session.sentMessages[i]);
            reply->setMessage(
                    mbus::Message::UP(session.sentMessages[i].release()));

            if (result != api::ReturnCode::OK) {
                reply->addError(mbus::Error(result, "Generic error"));
            }
        }

        session.reply(std::move(reply));
    }
}

uint64_t
VisitorManagerTest::verifyCreateVisitorReply(
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
VisitorManagerTest::getMatchingDocuments(std::vector<document::Document::SP >& docs) {
    uint32_t equalCount = 0;
    for (uint32_t i=0; i<docs.size(); ++i) {
        for (uint32_t j=0; j<_documents.size(); ++j) {
            if (docs[i]->getId() == _documents[j]->getId()
                && *docs[i] == *_documents[j])

            {
                equalCount++;
            }
        }
    }

    return equalCount;
}

void
VisitorManagerTest::testHitCounter()
{
    document::OrderingSpecification spec(document::OrderingSpecification::ASCENDING, 42, 7, 2);
    Visitor::HitCounter hitCounter(&spec);

    hitCounter.addHit(document::DocumentId("orderdoc(7,2):mail:1234:42:foo"), 450);
    hitCounter.addHit(document::DocumentId("orderdoc(7,2):mail:1234:49:foo"), 450);
    hitCounter.addHit(document::DocumentId("orderdoc(7,2):mail:1234:60:foo"), 450);
    hitCounter.addHit(document::DocumentId("orderdoc(7,2):mail:1234:10:foo"), 450);
    hitCounter.addHit(document::DocumentId("orderdoc(7,2):mail:1234:21:foo"), 450);

    CPPUNIT_ASSERT_EQUAL(3, (int)hitCounter.getFirstPassHits());
    CPPUNIT_ASSERT_EQUAL(1350, (int)hitCounter.getFirstPassBytes());
    CPPUNIT_ASSERT_EQUAL(2, (int)hitCounter.getSecondPassHits());
    CPPUNIT_ASSERT_EQUAL(900, (int)hitCounter.getSecondPassBytes());
}

namespace {

int getTotalSerializedSize(const std::vector<document::Document::SP>& docs)
{
    int total = 0;
    for (size_t i = 0; i < docs.size(); ++i) {
        total += int(docs[i]->serialize()->getLength());
    }
    return total;
}

}

void
VisitorManagerTest::testNormalUsage()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(address);
    cmd->setControlDestination("foo/bar");
    _top->sendDown(cmd);
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    // Should receive one multioperation message (bucket 3 has one document).
    getMessagesAndReply(1, getSession(0), docs, docIds);

    // All data has been replied to, expecting to get a create visitor reply
    verifyCreateVisitorReply(api::ReturnCode::OK,
                             int(docs.size()),
                             getTotalSerializedSize(docs));

    CPPUNIT_ASSERT_EQUAL(1u, getMatchingDocuments(docs));
    CPPUNIT_ASSERT(!_manager->hasPendingMessageState());
}

void
VisitorManagerTest::testResending()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(address);
    cmd->setControlDestination("foo/bar");
    _top->sendDown(cmd);
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    TestVisitorMessageSession& session = getSession(0);
    getMessagesAndReply(1, session, docs, docIds, api::ReturnCode::NOT_READY);

    {
        session.waitForMessages(2);

        documentapi::DocumentMessage* msg = session.sentMessages[1].get();

        mbus::Reply::UP reply = msg->createReply();

        CPPUNIT_ASSERT_EQUAL((uint32_t)documentapi::DocumentProtocol::MESSAGE_VISITORINFO,
                             session.sentMessages[1]->getType());
        reply->swapState(*session.sentMessages[1]);
        reply->setMessage(mbus::Message::UP(session.sentMessages[1].release()));
        session.reply(std::move(reply));
    }

    _node->getClock().addSecondsToTime(1);

    {
        session.waitForMessages(3);

        documentapi::DocumentMessage* msg = session.sentMessages[2].get();

        mbus::Reply::UP reply = msg->createReply();

        reply->swapState(*session.sentMessages[2]);
        reply->setMessage(mbus::Message::UP(session.sentMessages[2].release()));
        session.reply(std::move(reply));
    }

    // All data has been replied to, expecting to get a create visitor reply
    verifyCreateVisitorReply(api::ReturnCode::OK);
}

void
VisitorManagerTest::testVisitEmptyBucket()
{
    initializeTest();
    addSomeRemoves(true);
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));

    cmd->setAddress(address);
    _top->sendDown(cmd);

    // All data has been replied to, expecting to get a create visitor reply
    verifyCreateVisitorReply(api::ReturnCode::OK);
}

void
VisitorManagerTest::testMultiBucketVisit()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    cmd->setAddress(address);
    cmd->setDataDestination("fooclient.0");
    _top->sendDown(cmd);
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    // Should receive one multioperation message for each bucket
    getMessagesAndReply(10, getSession(0), docs, docIds);

    // All data has been replied to, expecting to get a create visitor reply
    verifyCreateVisitorReply(api::ReturnCode::OK);

    CPPUNIT_ASSERT_EQUAL(docCount, getMatchingDocuments(docs));
}

void
VisitorManagerTest::testNoBuckets()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));

    cmd->setAddress(address);
    _top->sendDown(cmd);

        // Should get one reply; a CreateVisitorReply with error since no
        // buckets where specified in the CreateVisitorCommand
    _top->waitForMessages(1, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
    CPPUNIT_ASSERT_EQUAL((size_t) 1, replies.size());
    std::shared_ptr<api::CreateVisitorReply> reply(
            std::dynamic_pointer_cast<api::CreateVisitorReply>(
                replies[0]));
        // Verify that cast went ok => it was a CreateVisitorReply message
    CPPUNIT_ASSERT(reply.get());
    api::ReturnCode ret(api::ReturnCode::ILLEGAL_PARAMETERS,
                        "No buckets specified");
    CPPUNIT_ASSERT_EQUAL(ret, reply->getResult());
}

void VisitorManagerTest::testVisitPutsAndRemoves()
{
    initializeTest();
    addSomeRemoves();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
    cmd->setAddress(address);
    cmd->setVisitRemoves();
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    _top->sendDown(cmd);
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    getMessagesAndReply(10, getSession(0), docs, docIds);

    verifyCreateVisitorReply(api::ReturnCode::OK);

    CPPUNIT_ASSERT_EQUAL(
            docCount - (docCount + 3) / 4,
            getMatchingDocuments(docs));

    CPPUNIT_ASSERT_EQUAL(
            (size_t) (docCount + 3) / 4,
            docIds.size());
}

void VisitorManagerTest::testVisitWithTimeframeAndSelection()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis",
                "testdoctype1.headerval < 2"));
    cmd->setFromTime(3);
    cmd->setToTime(8);
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    cmd->setAddress(address);
    _top->sendDown(cmd);
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    getMessagesAndReply(2, getSession(0), docs, docIds);

    verifyCreateVisitorReply(api::ReturnCode::OK);

    CPPUNIT_ASSERT_EQUAL((size_t) 2, docs.size());
    std::set<std::string> expected;
    expected.insert("userdoc:test:4:http://www.ntnu.no/4.html");
    expected.insert("userdoc:test:5:http://www.ntnu.no/5.html");
    std::set<std::string> actual;
    for (uint32_t i=0; i<docs.size(); ++i) {
        actual.insert(docs[i]->getId().toString());
    }
    CPPUNIT_ASSERT_EQUAL(expected, actual);
}

void VisitorManagerTest::testVisitWithTimeframeAndBogusSelection()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis",
                "DocType(testdoctype1---///---) XXX BAD Field(headerval) < 2"));
    cmd->setFromTime(3);
    cmd->setToTime(8);
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    cmd->setAddress(address);

    _top->sendDown(cmd);
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    CPPUNIT_ASSERT_EQUAL((size_t) 1, replies.size());

    api::StorageReply* reply = dynamic_cast<api::StorageReply*>(
            replies.front().get());
    CPPUNIT_ASSERT(reply);
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ILLEGAL_PARAMETERS,
                         reply->getResult().getResult());
}

void
VisitorManagerTest::testVisitorCallbacks()
{
    initializeTest();
    std::ostringstream replydata;
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "TestVisitor", "testvis", ""));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->addBucketToBeVisited(document::BucketId(16, 5));
    cmd->setAddress(address);
    _top->sendDown(cmd);

    // Wait until we have started the visitor
    TestVisitorMessageSession& session = getSession(0);

    for (uint32_t i = 0; i < 6; i++) {
        session.waitForMessages(i + 1);
        mbus::Reply::UP reply;
        {
            vespalib::MonitorGuard guard(session.getMonitor());

            CPPUNIT_ASSERT_EQUAL((uint32_t)documentapi::DocumentProtocol::MESSAGE_MAPVISITOR, session.sentMessages[i]->getType());

            documentapi::MapVisitorMessage* mapvisitormsg(
                    static_cast<documentapi::MapVisitorMessage*>(session.sentMessages[i].get()));

            replydata << mapvisitormsg->getData().get("msg");

            reply = mapvisitormsg->createReply();
            reply->swapState(*session.sentMessages[i]);
            reply->setMessage(mbus::Message::UP(session.sentMessages[i].release()));
        }
        session.reply(std::move(reply));
    }

    // All data has been replied to, expecting to get a create visitor reply
    verifyCreateVisitorReply(api::ReturnCode::OK);

    CPPUNIT_ASSERT_SUBSTRING_COUNT(replydata.str(), 1, "Starting visitor");
    CPPUNIT_ASSERT_SUBSTRING_COUNT(replydata.str(), 2, "Handling block of 1 documents");
    CPPUNIT_ASSERT_SUBSTRING_COUNT(replydata.str(), 2, "completedBucket");
    CPPUNIT_ASSERT_SUBSTRING_COUNT(replydata.str(), 1, "completedVisiting");
}

void
VisitorManagerTest::testVisitorCleanup()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);

    // Start a bunch of invalid visitors
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream ost;
        ost << "testvis" << i;
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "InvalidVisitor", ost.str(), ""));
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(address);
        cmd->setQueueTimeout(0);
        _top->sendDown(cmd);
        _top->waitForMessages(i+1, 60);
    }

    // Start a bunch of visitors
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream ost;
        ost << "testvis" << (i + 10);
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", ost.str(), ""));
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(address);
        cmd->setQueueTimeout(0);
        _top->sendDown(cmd);
    }

    // Should get 16 immediate replies - 10 failures and 6 busy
    {
        const int expected_total = 16;
        _top->waitForMessages(expected_total, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
        CPPUNIT_ASSERT_EQUAL(size_t(expected_total), replies.size());

        int failures = 0;
        int busy = 0;

        for (uint32_t i=0; i< expected_total; ++i) {
            std::shared_ptr<api::StorageMessage> msg(replies[i]);
            CPPUNIT_ASSERT_EQUAL(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
            std::shared_ptr<api::CreateVisitorReply> reply(
                    std::dynamic_pointer_cast<api::CreateVisitorReply>(msg));
            CPPUNIT_ASSERT(reply.get());

            if (i < 10) {
                if (api::ReturnCode::ILLEGAL_PARAMETERS == reply->getResult().getResult()) {
                    failures++;
                } else {
                    std::cerr << reply->getResult() << "\n";
                }
            } else {
                if (api::ReturnCode::BUSY == reply->getResult().getResult()) {
                    busy++;
                }
            }
        }

        CPPUNIT_ASSERT_EQUAL(10, failures);
        CPPUNIT_ASSERT_EQUAL(expected_total - 10, busy);
    }

    // 4 pending

    // Finish a visitor
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    getMessagesAndReply(1, getSession(0), docs, docIds);

    // Should get a reply for the visitor.
    verifyCreateVisitorReply(api::ReturnCode::OK);

    // 3 pending

    // Fail a visitor
    getMessagesAndReply(1, getSession(1), docs, docIds, api::ReturnCode::INTERNAL_FAILURE);

    // Should get a reply for the visitor.
    verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE);

    // 2 pending

    CPPUNIT_ASSERT_EQUAL(2u, _manager->getActiveVisitorCount());

    // Start a bunch of more visitors
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream ost;
        ost << "testvis" << (i + 24);
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", ost.str(), ""));
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(address);
        cmd->setQueueTimeout(0);
        _top->sendDown(cmd);
    }

    // Should now get 8 busy.
    _top->waitForMessages(8, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    CPPUNIT_ASSERT_EQUAL(size_t(8), replies.size());

    for (uint32_t i=0; i< replies.size(); ++i) {
        std::shared_ptr<api::StorageMessage> msg(replies[i]);
        CPPUNIT_ASSERT_EQUAL(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
        std::shared_ptr<api::CreateVisitorReply> reply(
                std::dynamic_pointer_cast<api::CreateVisitorReply>(msg));
        CPPUNIT_ASSERT(reply.get());

        CPPUNIT_ASSERT_EQUAL(api::ReturnCode::BUSY, reply->getResult().getResult());
    }

    // 4 still pending, need to clean up our stuff before tearing down.
    CPPUNIT_ASSERT_EQUAL(4u, _manager->getActiveVisitorCount());

    for (uint32_t i = 0; i < 4; ++i) {
        getMessagesAndReply(1, getSession(i + 2), docs, docIds);
        verifyCreateVisitorReply(api::ReturnCode::OK);
    }

    CPPUNIT_ASSERT_EQUAL(0u, _manager->getActiveVisitorCount());
}

void
VisitorManagerTest::testAbortOnFailedVisitorInfo()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);

    {
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(address);
        cmd->setQueueTimeout(0);
        _top->sendDown(cmd);
    }

    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    TestVisitorMessageSession& session = getSession(0);
    getMessagesAndReply(1, session, docs, docIds, api::ReturnCode::NOT_READY);

    {
        session.waitForMessages(2);

        documentapi::DocumentMessage* cmd = session.sentMessages[1].get();

        mbus::Reply::UP reply = cmd->createReply();

        CPPUNIT_ASSERT_EQUAL((uint32_t)documentapi::DocumentProtocol::MESSAGE_VISITORINFO, session.sentMessages[1]->getType());
        reply->swapState(*session.sentMessages[1]);
        reply->setMessage(mbus::Message::UP(session.sentMessages[1].release()));
        reply->addError(mbus::Error(api::ReturnCode::NOT_CONNECTED, "Me no ready"));
        session.reply(std::move(reply));
    }
    verifyCreateVisitorReply(api::ReturnCode::NOT_CONNECTED);
}

void
VisitorManagerTest::testAbortOnFieldPathError()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);

    // Use bogus field path to force error to happen
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor",
                                          "testvis",
                                          "testdoctype1.headerval{bogus} == 1234"));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(address);
    cmd->setQueueTimeout(0);
    _top->sendDown(cmd);

    verifyCreateVisitorReply(api::ReturnCode::ILLEGAL_PARAMETERS);
}

void
VisitorManagerTest::testVisitorQueueTimeout()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
    _manager->enforceQueueUsage();

    {
        vespalib::MonitorGuard guard(_manager->getThread(0).getQueueMonitor());

        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(address);
        cmd->setQueueTimeout(1);
        cmd->setTimeout(100 * 1000 * 1000);
        _top->sendDown(cmd);

        _node->getClock().addSecondsToTime(1000);
    }

    // Don't answer any messages. Make sure we timeout anyways.
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    std::shared_ptr<api::StorageMessage> msg(replies[0]);

    CPPUNIT_ASSERT_EQUAL(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
    std::shared_ptr<api::CreateVisitorReply> reply(
            std::dynamic_pointer_cast<api::CreateVisitorReply>(msg));
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::BUSY,
                                "Visitor timed out in visitor queue"),
                         reply->getResult());
}

void
VisitorManagerTest::testVisitorProcessingTimeout()
{
    initializeTest();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);

    std::shared_ptr<api::CreateVisitorCommand> cmd(
            new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", "testvis", ""));
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(address);
    cmd->setQueueTimeout(0);
    cmd->setTimeout(100);
    _top->sendDown(cmd);

    // Wait for Put before increasing the clock
    TestVisitorMessageSession& session = getSession(0);
    session.waitForMessages(1);

    _node->getClock().addSecondsToTime(1000);

    verifyCreateVisitorReply(api::ReturnCode::ABORTED);
}

namespace {
    uint32_t nextVisitor = 0;

    api::StorageMessage::Id
    sendCreateVisitor(uint32_t timeout, DummyStorageLink& top, uint8_t priority = 127) {
        std::ostringstream ost;
        ost << "testvis" << ++nextVisitor;
        api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
        std::shared_ptr<api::CreateVisitorCommand> cmd(
                new api::CreateVisitorCommand(makeBucketSpace(), "DumpVisitor", ost.str(), ""));
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(address);
        cmd->setQueueTimeout(timeout);
        cmd->setPriority(priority);
        top.sendDown(cmd);
        return cmd->getMsgId();
    }
}

void
VisitorManagerTest::testPrioritizedVisitorQueing()
{
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    initializeTest();

    _manager->setMaxConcurrentVisitors(4);
    _manager->setMaxVisitorQueueSize(4);

    api::StorageMessage::Id ids[10] = { 0 };

    // First 4 should just start..
    for (uint32_t i = 0; i < 4; ++i) {
        ids[i] = sendCreateVisitor(i, *_top, i);
    }

    // Next ones should be queued - (Better not finish before we get here)
    // Submit with higher priorities
    for (uint32_t i = 0; i < 4; ++i) {
        ids[i + 4] = sendCreateVisitor(1000, *_top, 100 - i);
    }

    // Queue is now full with a pri 100 visitor at its end
    // Send a lower pri visitor that will be busy-returned immediately
    ids[8] = sendCreateVisitor(1000, *_top, 130);

    CPPUNIT_ASSERT_EQUAL(ids[8], verifyCreateVisitorReply(api::ReturnCode::BUSY));

    // Send a higher pri visitor that will take the place of pri 100 visitor
    ids[9] = sendCreateVisitor(1000, *_top, 60);

    CPPUNIT_ASSERT_EQUAL(ids[4], verifyCreateVisitorReply(api::ReturnCode::BUSY));

    // Finish the first visitor
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;
    getMessagesAndReply(1, getSession(0), docs, docIds, api::ReturnCode::OK, Priority::PRI_HIGHEST);
    verifyCreateVisitorReply(api::ReturnCode::OK);

    // We should now start the highest priority visitor.
    getMessagesAndReply(1, getSession(4), docs, docIds, api::ReturnCode::OK, Priority::PRI_VERY_HIGH);
    CPPUNIT_ASSERT_EQUAL(ids[9], verifyCreateVisitorReply(api::ReturnCode::OK));

    // 3 pending, 3 in queue. Clean them up
    std::vector<uint32_t> pending_sessions = {1, 2, 3, 5, 6, 7};
    for (auto session : pending_sessions) {
        finishAndWaitForVisitorSessionCompletion(session);
    }
    CPPUNIT_ASSERT_EQUAL(0u, _manager->getActiveVisitorCount());
}

void VisitorManagerTest::finishAndWaitForVisitorSessionCompletion(uint32_t sessionIndex) {
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;
    getMessagesAndReply(1, getSession(sessionIndex), docs, docIds, api::ReturnCode::OK, std::optional<Priority::Value>());
    verifyCreateVisitorReply(api::ReturnCode::OK);
}

void
VisitorManagerTest::testPrioritizedMaxConcurrentVisitors() {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    initializeTest();

    api::StorageMessage::Id ids[17] = { 0 };

    // Number of concurrent visitors is in [4, 8], depending on priority
    // Max concurrent:
    //  [0, 1):  4
    //  [1, 64): 3
    //  [64, 128): 2
    //  [128, 192): 1
    //  [192, 256): 0
    _manager->setMaxConcurrentVisitors(4, 4);
    _manager->setMaxVisitorQueueSize(6);

    // First 4 should just start..
    for (uint32_t i = 0; i < 4; ++i) {
        ids[i] = sendCreateVisitor(i, *_top, i);
    }

    // Low pri messages; get put into queue
    for (uint32_t i = 0; i < 6; ++i) {
        ids[i + 4] = sendCreateVisitor(1000, *_top, 203 - i);
    }

    // Higher pri message: fits happily into 1 extra concurrent slot
    ids[10] = sendCreateVisitor(1000, *_top, 190);

    // Should punch pri203 msg out of the queue -> busy
    ids[11] = sendCreateVisitor(1000, *_top, 197);

    CPPUNIT_ASSERT_EQUAL(ids[4], verifyCreateVisitorReply(api::ReturnCode::BUSY));

    // No concurrency slots left for this message -> busy
    ids[12] = sendCreateVisitor(1000, *_top, 204);

    CPPUNIT_ASSERT_EQUAL(ids[12], verifyCreateVisitorReply(api::ReturnCode::BUSY));

    // Gets a concurrent slot
    ids[13] = sendCreateVisitor(1000, *_top, 80);

    // Kicks pri 202 out of the queue -> busy
    ids[14] = sendCreateVisitor(1000, *_top, 79);

    CPPUNIT_ASSERT_EQUAL(ids[5], verifyCreateVisitorReply(api::ReturnCode::BUSY));

    // Gets a concurrent slot
    ids[15] = sendCreateVisitor(1000, *_top, 63);

    // Very Important Visitor(tm) gets a concurrent slot
    ids[16] = sendCreateVisitor(1000, *_top, 0);

    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    std::set<uint64_t> finishedVisitors;

    // Verify that the correct visitors are running.
    for (int i = 0; i < 8; i++) {
        documentapi::Priority::Value priority =
            documentapi::Priority::PRI_HIGHEST; // ids 0-3,16
        if (i == 4) {
            priority = documentapi::Priority::PRI_VERY_LOW; // ids 10
        } else if (i == 5) {
            priority = documentapi::Priority::PRI_HIGH_2; // ids 13
        } else if (i == 6) {
            priority = documentapi::Priority::PRI_HIGH_1; // ids 15
        }
        getMessagesAndReply(1, getSession(i), docs, docIds, api::ReturnCode::OK,
                            priority);
        finishedVisitors.insert(verifyCreateVisitorReply(api::ReturnCode::OK));
    }

    for (int i = 0; i < 4; i++) {
        CPPUNIT_ASSERT(finishedVisitors.find(ids[i]) != finishedVisitors.end());
    }

    CPPUNIT_ASSERT(finishedVisitors.find(ids[10]) != finishedVisitors.end());
    CPPUNIT_ASSERT(finishedVisitors.find(ids[13]) != finishedVisitors.end());
    CPPUNIT_ASSERT(finishedVisitors.find(ids[15]) != finishedVisitors.end());
    CPPUNIT_ASSERT(finishedVisitors.find(ids[16]) != finishedVisitors.end());

    finishedVisitors.clear();

    for (int i = 8; i < 14; i++) {
        documentapi::Priority::Value priority =
            documentapi::Priority::PRI_LOWEST; // ids 6-9,11
        if (i == 8) {
            priority = documentapi::Priority::PRI_HIGH_2; // ids 14
        }
        getMessagesAndReply(1, getSession(i), docs, docIds, api::ReturnCode::OK,
                            priority);
        uint64_t msgId = verifyCreateVisitorReply(api::ReturnCode::OK);
        finishedVisitors.insert(msgId);
    }

    for (int i = 6; i < 10; i++) {
        CPPUNIT_ASSERT(finishedVisitors.find(ids[i]) != finishedVisitors.end());
    }

    CPPUNIT_ASSERT(finishedVisitors.find(ids[11]) != finishedVisitors.end());
    CPPUNIT_ASSERT(finishedVisitors.find(ids[14]) != finishedVisitors.end());
    CPPUNIT_ASSERT_EQUAL(0u, _manager->getActiveVisitorCount());
}

void
VisitorManagerTest::testVisitorQueingZeroQueueSize() {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    initializeTest();

    _manager->setMaxConcurrentVisitors(4);
    _manager->setMaxVisitorQueueSize(0);

    // First 4 should just start..
    for (uint32_t i = 0; i < 4; ++i) {
        sendCreateVisitor(i, *_top, i);
    }
    // Queue size is zero, all visitors will be busy-returned
    for (uint32_t i = 0; i < 5; ++i) {
        sendCreateVisitor(1000, *_top, 100 - i);
        verifyCreateVisitorReply(api::ReturnCode::BUSY);
    }
    for (uint32_t session = 0; session < 4; ++session) {
        finishAndWaitForVisitorSessionCompletion(session);
    }
}

void
VisitorManagerTest::testStatusPage() {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    initializeTest();

    _manager->setMaxConcurrentVisitors(1, 1);
    _manager->setMaxVisitorQueueSize(6);
    // 1 running, 1 queued
    sendCreateVisitor(1000000, *_top, 1);
    sendCreateVisitor(1000000, *_top, 128);

    {
        TestVisitorMessageSession& session = getSession(0);
        session.waitForMessages(1);
    }

    std::ostringstream ss;
    static_cast<framework::HtmlStatusReporter&>(*_manager).reportHtmlStatus(ss, path);

    std::string str(ss.str());
    CPPUNIT_ASSERT(str.find("Currently running visitors") != std::string::npos);
    // Should be propagated to visitor thread
    CPPUNIT_ASSERT(str.find("Running 1 visitors") != std::string::npos); // 1 active
    CPPUNIT_ASSERT(str.find("waiting visitors 1") != std::string::npos); // 1 queued
    CPPUNIT_ASSERT(str.find("Visitor thread 0") != std::string::npos);
    CPPUNIT_ASSERT(str.find("Disconnected visitor timeout") != std::string::npos); // verbose per thread
    CPPUNIT_ASSERT(str.find("Message #1 <b>putdocumentmessage</b>") != std::string::npos); // 1 active

    for (uint32_t session = 0; session < 2 ; ++session){
        finishAndWaitForVisitorSessionCompletion(session);
    }
}

}
