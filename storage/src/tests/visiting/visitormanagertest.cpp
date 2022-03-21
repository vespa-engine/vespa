// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucket.h>
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
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <gmock/gmock.h>
#include <optional>
#include <thread>
#include <chrono>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using documentapi::Priority;
using namespace std::chrono_literals;
using namespace ::testing;

namespace storage {
namespace {

using msg_ptr_vector = std::vector<api::StorageMessage::SP>;
vespalib::string _Storage("storage");
api::StorageMessageAddress _Address(&_Storage, lib::NodeType::STORAGE, 0);
}

struct VisitorManagerTest : Test {
protected:
    static uint32_t docCount;
    std::vector<document::Document::SP > _documents;
    std::unique_ptr<TestVisitorMessageSessionFactory> _messageSessionFactory;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    VisitorManager* _manager;

    VisitorManagerTest() : _node() {}
    ~VisitorManagerTest();

    // Not using setUp since can't throw exception out of it.
    void initializeTest(bool defer_manager_thread_start = false);
    void addSomeRemoves(bool removeAll = false);
    void TearDown() override;
    TestVisitorMessageSession& getSession(uint32_t n);
    void verifyCreateVisitorReply(
            api::ReturnCode::Result expectedResult,
            int checkStatsDocsVisited = -1,
            int checkStatsBytesVisited = -1,
            uint64_t* message_id_out = nullptr);
    void getMessagesAndReply(
            int expectedCount,
            TestVisitorMessageSession& session,
            std::vector<document::Document::SP >& docs,
            std::vector<document::DocumentId>& docIds,
            api::ReturnCode::Result returnCode = api::ReturnCode::OK,
            std::optional<Priority::Value> priority = documentapi::Priority::PRI_NORMAL_4);
    uint32_t getMatchingDocuments(std::vector<document::Document::SP >& docs);
    void finishAndWaitForVisitorSessionCompletion(uint32_t sessionIndex);
};

VisitorManagerTest::~VisitorManagerTest() = default;

uint32_t VisitorManagerTest::docCount = 10;

void
VisitorManagerTest::initializeTest(bool defer_manager_thread_start)
{
    vdstestlib::DirConfig config(getStandardConfig(true));
    config.getConfig("stor-visitor").set("visitorthreads", "1");

    _messageSessionFactory = std::make_unique<TestVisitorMessageSessionFactory>(config.getConfigId());
    _node = std::make_unique<TestServiceLayerApp>(config.getConfigId());
    _node->setupDummyPersistence();
    _node->getStateUpdater().setClusterState(std::make_shared<lib::ClusterState>("storage:1 distributor:1"));
    _top = std::make_unique<DummyStorageLink>();
    auto vm = std::make_unique<VisitorManager>(config::ConfigUri(config.getConfigId()),
                                               _node->getComponentRegister(),
                                               *_messageSessionFactory,
                                               VisitorFactory::Map(),
                                               defer_manager_thread_start);
    _manager = vm.get();
    _top->push_back(std::move(vm));
    _top->push_back(std::make_unique<FileStorManager>(config::ConfigUri(config.getConfigId()), _node->getPersistenceProvider(),
                                                      _node->getComponentRegister(), *_node, _node->get_host_info()));
    _manager->setTimeBetweenTicks(10);
    _top->open();

    // Adding some documents so database isn't empty
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
        uri << "id:test:testdoctype1:n=" << i % 10 << ":http://www.ntnu.no/"
            << i << ".html";

        _documents.push_back(document::Document::SP(
                _node->getTestDocMan().createDocument(content, uri.str())));
        const document::DocumentType& type(_documents.back()->getType());
        _documents.back()->setValue(type.getField("headerval"), document::IntFieldValue(i % 4));
    }
    for (uint32_t i=0; i<10; ++i) {
        document::BucketId bid(16, i);

        auto cmd = std::make_shared<api::CreateBucketCommand>(makeDocumentBucket(bid));
        cmd->setAddress(_Address);
        cmd->setSourceIndex(0);
        _top->sendDown(cmd);
        _top->waitForMessages(1, 60);
        _top->reset();

        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bid, "",
                    StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry.write();
    }
    for (uint32_t i=0; i<docCount; ++i) {
        document::BucketId bid(16, i);

        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), _documents[i], i+1);
        cmd->setAddress(_Address);
        _top->sendDown(cmd);
        _top->waitForMessages(1, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
        ASSERT_EQ(1, replies.size());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(replies[0]);
        ASSERT_TRUE(reply.get());
        ASSERT_EQ(api::ReturnCode(api::ReturnCode::OK), reply->getResult());
    }
}

void
VisitorManagerTest::addSomeRemoves(bool removeAll)
{
    framework::defaultimplementation::FakeClock clock;
    for (uint32_t i=0; i<docCount; i += (removeAll ? 1 : 4)) {
            // Add it to the database
        document::BucketId bid(16, i % 10);
        auto cmd = std::make_shared<api::RemoveCommand>(
                        makeDocumentBucket(bid), _documents[i]->getId(), clock.getTimeInMicros().getTime() + docCount + i + 1);
        cmd->setAddress(_Address);
        _top->sendDown(cmd);
        _top->waitForMessages(1, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
        ASSERT_EQ(1, replies.size());
        auto reply = std::dynamic_pointer_cast<api::RemoveReply>(replies[0]);
        ASSERT_TRUE(reply.get());
        ASSERT_EQ(api::ReturnCode(api::ReturnCode::OK), reply->getResult());
    }
}

void
VisitorManagerTest::TearDown()
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
    const std::vector<TestVisitorMessageSession*>& sessions(_messageSessionFactory->_visitorSessions);
    framework::defaultimplementation::RealClock clock;
    framework::MilliSecTime endTime(clock.getTimeInMillis() + framework::MilliSecTime(30 * 1000));
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
            std::lock_guard guard(session.getMonitor());

            if (priority) {
                ASSERT_EQ(*priority, session.sentMessages[i]->getPriority());
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
            reply->setMessage(std::move(session.sentMessages[i]));

            if (result != api::ReturnCode::OK) {
                reply->addError(mbus::Error(result, "Generic error"));
            }
        }

        session.reply(std::move(reply));
    }
}

void
VisitorManagerTest::verifyCreateVisitorReply(
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

namespace {

int getTotalSerializedSize(const std::vector<document::Document::SP>& docs)
{
    int total = 0;
    for (size_t i = 0; i < docs.size(); ++i) {
        total += int(docs[i]->serialize().size());
    }
    return total;
}

}

TEST_F(VisitorManagerTest, normal_usage) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(_Address);
    cmd->setControlDestination("foo/bar");
    _top->sendDown(cmd);
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;

    // Should receive one multioperation message (bucket 3 has one document).
    getMessagesAndReply(1, getSession(0), docs, docIds);

    // All data has been replied to, expecting to get a create visitor reply
    ASSERT_NO_FATAL_FAILURE(
            verifyCreateVisitorReply(api::ReturnCode::OK,
                                     int(docs.size()),
                                     getTotalSerializedSize(docs)));

    EXPECT_EQ(1u, getMatchingDocuments(docs));
    EXPECT_FALSE(_manager->hasPendingMessageState());
}

TEST_F(VisitorManagerTest, resending) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(_Address);
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

        ASSERT_EQ(documentapi::DocumentProtocol::MESSAGE_VISITORINFO,
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
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
}

TEST_F(VisitorManagerTest, visit_empty_bucket) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    addSomeRemoves(true);
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));

    cmd->setAddress(_Address);
    _top->sendDown(cmd);

    // All data has been replied to, expecting to get a create visitor reply
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
}

TEST_F(VisitorManagerTest, multi_bucket_visit) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    cmd->setAddress(_Address);
    cmd->setDataDestination("fooclient.0");
    _top->sendDown(cmd);
    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;

    // Should receive one multioperation message for each bucket
    getMessagesAndReply(10, getSession(0), docs, docIds);

    // All data has been replied to, expecting to get a create visitor reply
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));

    EXPECT_EQ(docCount, getMatchingDocuments(docs));
}

TEST_F(VisitorManagerTest, no_buckets) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");

    cmd->setAddress(_Address);
    _top->sendDown(cmd);

    // Should get one reply; a CreateVisitorReply with error since no
    // buckets where specified in the CreateVisitorCommand
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    ASSERT_EQ(1, replies.size());
    auto reply = std::dynamic_pointer_cast<api::CreateVisitorReply>(replies[0]);
    // Verify that cast went ok => it was a CreateVisitorReply message
    ASSERT_TRUE(reply.get());
    api::ReturnCode ret(api::ReturnCode::ILLEGAL_PARAMETERS, "No buckets specified");
    EXPECT_EQ(ret, reply->getResult());
}

TEST_F(VisitorManagerTest, visit_puts_and_removes) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    addSomeRemoves();
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
    cmd->setAddress(_Address);
    cmd->setVisitRemoves();
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    _top->sendDown(cmd);
    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;

    getMessagesAndReply(10, getSession(0), docs, docIds);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));

    EXPECT_EQ(docCount - (docCount + 3) / 4,
              getMatchingDocuments(docs));

    EXPECT_EQ((docCount + 3) / 4,
              docIds.size());
}

TEST_F(VisitorManagerTest, visit_with_timeframe_and_selection) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "testdoctype1.headerval < 2");
    cmd->setFromTime(3);
    cmd->setToTime(8);
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    cmd->setAddress(_Address);
    _top->sendDown(cmd);
    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;

    getMessagesAndReply(2, getSession(0), docs, docIds);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));

    ASSERT_EQ(2, docs.size());
    std::set<std::string> expected;
    expected.insert("id:test:testdoctype1:n=4:http://www.ntnu.no/4.html");
    expected.insert("id:test:testdoctype1:n=5:http://www.ntnu.no/5.html");
    std::set<std::string> actual;
    for (uint32_t i=0; i<docs.size(); ++i) {
        actual.insert(docs[i]->getId().toString());
    }
    EXPECT_THAT(expected, ContainerEq(actual));
}

TEST_F(VisitorManagerTest, visit_with_timeframe_and_bogus_selection) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis",
            "DocType(testdoctype1---///---) XXX BAD Field(headerval) < 2");
    cmd->setFromTime(3);
    cmd->setToTime(8);
    for (uint32_t i=0; i<10; ++i) {
        cmd->addBucketToBeVisited(document::BucketId(16, i));
    }
    cmd->setAddress(_Address);

    _top->sendDown(cmd);
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    ASSERT_EQ(1, replies.size());

    auto* reply = dynamic_cast<api::StorageReply*>(replies.front().get());
    ASSERT_TRUE(reply);
    EXPECT_EQ(api::ReturnCode::ILLEGAL_PARAMETERS, reply->getResult().getResult());
}

#define ASSERT_SUBSTRING_COUNT(source, expectedCount, substring) \
    { \
        uint32_t count = 0; \
        std::ostringstream value; /* Let value be non-strings */ \
        value << source; \
        std::string s(value.str()); \
        std::string::size_type pos = s.find(substring); \
        while (pos != std::string::npos) { \
            ++count; \
            pos = s.find(substring, pos+1); \
        } \
        if (count != (uint32_t) expectedCount) { \
            FAIL() << "Value of '" << s << "' contained " << count \
                   << " instances of substring '" << substring << "', not " \
                   << expectedCount << " as expected."; \
        } \
    }

TEST_F(VisitorManagerTest, visitor_callbacks) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());
    std::ostringstream replydata;
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "TestVisitor", "testvis", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->addBucketToBeVisited(document::BucketId(16, 5));
    cmd->setAddress(_Address);
    _top->sendDown(cmd);

    // Wait until we have started the visitor
    TestVisitorMessageSession& session = getSession(0);

    for (uint32_t i = 0; i < 6; i++) {
        session.waitForMessages(i + 1);
        mbus::Reply::UP reply;
        {
            std::lock_guard guard(session.getMonitor());

            ASSERT_EQ(documentapi::DocumentProtocol::MESSAGE_MAPVISITOR, session.sentMessages[i]->getType());

            auto* mapvisitormsg = dynamic_cast<documentapi::MapVisitorMessage*>(session.sentMessages[i].get());
            ASSERT_TRUE(mapvisitormsg != nullptr);

            replydata << mapvisitormsg->getData().get("msg");

            reply = mapvisitormsg->createReply();
            reply->swapState(*session.sentMessages[i]);
            reply->setMessage(std::move(session.sentMessages[i]));
        }
        session.reply(std::move(reply));
    }

    // All data has been replied to, expecting to get a create visitor reply
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));

    ASSERT_SUBSTRING_COUNT(replydata.str(), 1, "Starting visitor");
    ASSERT_SUBSTRING_COUNT(replydata.str(), 2, "Handling block of 1 documents");
    ASSERT_SUBSTRING_COUNT(replydata.str(), 2, "completedBucket");
    ASSERT_SUBSTRING_COUNT(replydata.str(), 1, "completedVisiting");
}

TEST_F(VisitorManagerTest, visitor_cleanup) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());

    // Start a bunch of invalid visitors
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream ost;
        ost << "testvis" << i;
        auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "InvalidVisitor", ost.str(), "");
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(_Address);
        cmd->setQueueTimeout(0ms);
        _top->sendDown(cmd);
        _top->waitForMessages(i+1, 60);
    }

    // Start a bunch of visitors
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream ost;
        ost << "testvis" << (i + 10);
        auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", ost.str(), "");
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(_Address);
        cmd->setQueueTimeout(0ms);
        _top->sendDown(cmd);
    }

    // Should get 16 immediate replies - 10 failures and 6 busy
    {
        const int expected_total = 16;
        _top->waitForMessages(expected_total, 60);
        const msg_ptr_vector replies = _top->getRepliesOnce();
        ASSERT_EQ(expected_total, replies.size());

        int failures = 0;
        int busy = 0;

        for (uint32_t i=0; i< expected_total; ++i) {
            std::shared_ptr<api::StorageMessage> msg(replies[i]);
            ASSERT_EQ(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
            auto reply = std::dynamic_pointer_cast<api::CreateVisitorReply>(msg);
            ASSERT_TRUE(reply.get());

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

        ASSERT_EQ(10, failures);
        ASSERT_EQ(expected_total - 10, busy);
    }

    // 4 pending

    // Finish a visitor
    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;

    getMessagesAndReply(1, getSession(0), docs, docIds);

    // Should get a reply for the visitor.
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));

    // 3 pending

    // Fail a visitor
    getMessagesAndReply(1, getSession(1), docs, docIds, api::ReturnCode::INTERNAL_FAILURE);

    // Should get a reply for the visitor.
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::INTERNAL_FAILURE));

    // Wait until there are 2 pending. Visitor threads might not have completed
    // cleanup of existing visitors yet.
    while (_manager->getActiveVisitorCount() != 2) {
        std::this_thread::sleep_for(10ms);
    }

    // Start a bunch of more visitors
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream ost;
        ost << "testvis" << (i + 24);
        auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", ost.str(), "");
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(_Address);
        cmd->setQueueTimeout(0ms);
        _top->sendDown(cmd);
    }

    // Should now get 8 busy.
    _top->waitForMessages(8, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    ASSERT_EQ(8, replies.size());

    for (uint32_t i=0; i< replies.size(); ++i) {
        std::shared_ptr<api::StorageMessage> msg(replies[i]);
        ASSERT_EQ(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
        auto reply = std::dynamic_pointer_cast<api::CreateVisitorReply>(msg);
        ASSERT_TRUE(reply.get());

        ASSERT_EQ(api::ReturnCode::BUSY, reply->getResult().getResult());
    }

    for (uint32_t i = 0; i < 4; ++i) {
        getMessagesAndReply(1, getSession(i + 2), docs, docIds);
        verifyCreateVisitorReply(api::ReturnCode::OK);
    }
}

TEST_F(VisitorManagerTest, abort_on_failed_visitor_info) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());

    {
        auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(_Address);
        cmd->setQueueTimeout(0ms);
        _top->sendDown(cmd);
    }

    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;

    TestVisitorMessageSession& session = getSession(0);
    getMessagesAndReply(1, session, docs, docIds, api::ReturnCode::NOT_READY);

    {
        session.waitForMessages(2);

        documentapi::DocumentMessage* cmd = session.sentMessages[1].get();

        mbus::Reply::UP reply = cmd->createReply();

        ASSERT_EQ(documentapi::DocumentProtocol::MESSAGE_VISITORINFO, session.sentMessages[1]->getType());
        reply->swapState(*session.sentMessages[1]);
        reply->setMessage(mbus::Message::UP(session.sentMessages[1].release()));
        reply->addError(mbus::Error(api::ReturnCode::NOT_CONNECTED, "Me no ready"));
        session.reply(std::move(reply));
    }
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::NOT_CONNECTED));
}

TEST_F(VisitorManagerTest, abort_on_field_path_error) {
    initializeTest();

    // Use bogus field path to force error to happen
    auto cmd = std::make_shared<api::CreateVisitorCommand>(
            makeBucketSpace(), "DumpVisitor", "testvis", "testdoctype1.headerval{bogus} == 1234");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(_Address);
    cmd->setQueueTimeout(0ms);
    _top->sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::ILLEGAL_PARAMETERS));
}

TEST_F(VisitorManagerTest, visitor_queue_timeout) {
    ASSERT_NO_FATAL_FAILURE(initializeTest(true));
    _manager->enforceQueueUsage();

    {
        auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
        cmd->addBucketToBeVisited(document::BucketId(16, 3));
        cmd->setAddress(_Address);
        cmd->setQueueTimeout(1ms);
        cmd->setTimeout(100 * 1000 * 1000ms);
        // The manager thread isn't running yet so the visitor stays on the queue
        _top->sendDown(cmd);
    }

    _node->getClock().addSecondsToTime(1000);
    _manager->create_and_start_manager_thread();

    // Don't answer any messages. Make sure we timeout anyways.
    _top->waitForMessages(1, 60);
    const msg_ptr_vector replies = _top->getRepliesOnce();
    std::shared_ptr<api::StorageMessage> msg(replies[0]);

    ASSERT_EQ(api::MessageType::VISITOR_CREATE_REPLY, msg->getType());
    auto reply = std::dynamic_pointer_cast<api::CreateVisitorReply>(msg);
    ASSERT_EQ(api::ReturnCode(api::ReturnCode::BUSY, "Visitor timed out in visitor queue"),
              reply->getResult());
}

TEST_F(VisitorManagerTest, visitor_processing_timeout) {
    ASSERT_NO_FATAL_FAILURE(initializeTest());

    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", "testvis", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(_Address);
    cmd->setQueueTimeout(0ms);
    cmd->setTimeout(100ms);
    _top->sendDown(cmd);

    // Wait for Put before increasing the clock
    TestVisitorMessageSession& session = getSession(0);
    session.waitForMessages(1);

    _node->getClock().addSecondsToTime(1000);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::ABORTED));
}

namespace {

uint32_t nextVisitor = 0;

api::StorageMessage::Id
sendCreateVisitor(vespalib::duration timeout, DummyStorageLink& top, uint8_t priority = 127) {
    std::ostringstream ost;
    ost << "testvis" << ++nextVisitor;
    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "DumpVisitor", ost.str(), "");
    cmd->addBucketToBeVisited(document::BucketId(16, 3));
    cmd->setAddress(_Address);
    cmd->setQueueTimeout(timeout);
    cmd->setPriority(priority);
    top.sendDown(cmd);
    return cmd->getMsgId();
}

}

TEST_F(VisitorManagerTest, prioritized_visitor_queing) {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    ASSERT_NO_FATAL_FAILURE(initializeTest());

    _manager->setMaxConcurrentVisitors(4);
    _manager->setMaxVisitorQueueSize(4);

    api::StorageMessage::Id ids[10] = { 0 };

    // First 4 should just start..
    for (uint32_t i = 0; i < 4; ++i) {
        ids[i] = sendCreateVisitor(i*1ms, *_top, i);
    }

    // Next ones should be queued - (Better not finish before we get here)
    // Submit with higher priorities
    for (uint32_t i = 0; i < 4; ++i) {
        ids[i + 4] = sendCreateVisitor(1000ms, *_top, 100 - i);
    }

    // Queue is now full with a pri 100 visitor at its end
    // Send a lower pri visitor that will be busy-returned immediately
    ids[8] = sendCreateVisitor(1000ms, *_top, 130);

    uint64_t message_id = 0;
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUSY, -1, -1, &message_id));
    ASSERT_EQ(ids[8], message_id);

    // Send a higher pri visitor that will take the place of pri 100 visitor
    ids[9] = sendCreateVisitor(1000ms, *_top, 60);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUSY, -1, -1, &message_id));
    ASSERT_EQ(ids[4], message_id);

    // Finish the first visitor
    std::vector<document::Document::SP> docs;
    std::vector<document::DocumentId> docIds;
    getMessagesAndReply(1, getSession(0), docs, docIds, api::ReturnCode::OK, Priority::PRI_HIGHEST);
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK, -1, -1, &message_id));

    // We should now start the highest priority visitor.
    getMessagesAndReply(1, getSession(4), docs, docIds, api::ReturnCode::OK, Priority::PRI_VERY_HIGH);
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK, -1, -1, &message_id));
    ASSERT_EQ(ids[9], message_id);

    // 3 pending, 3 in queue. Clean them up
    std::vector<uint32_t> pending_sessions = {1, 2, 3, 5, 6, 7};
    for (auto session : pending_sessions) {
        ASSERT_NO_FATAL_FAILURE(finishAndWaitForVisitorSessionCompletion(session));
    }
}

void VisitorManagerTest::finishAndWaitForVisitorSessionCompletion(uint32_t sessionIndex) {
    std::vector<document::Document::SP > docs;
    std::vector<document::DocumentId> docIds;
    getMessagesAndReply(1, getSession(sessionIndex), docs, docIds, api::ReturnCode::OK, std::optional<Priority::Value>());
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK));
}

TEST_F(VisitorManagerTest, prioritized_max_concurrent_visitors) {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    ASSERT_NO_FATAL_FAILURE(initializeTest());

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
        ids[i] = sendCreateVisitor(i*1ms, *_top, i);
    }

    // Low pri messages; get put into queue
    for (uint32_t i = 0; i < 6; ++i) {
        ids[i + 4] = sendCreateVisitor(1000ms, *_top, 203 - i);
    }

    // Higher pri message: fits happily into 1 extra concurrent slot
    ids[10] = sendCreateVisitor(1000ms, *_top, 190);

    // Should punch pri203 msg out of the queue -> busy
    ids[11] = sendCreateVisitor(1000ms, *_top, 197);

    uint64_t message_id = 0;
    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUSY, -1, -1, &message_id));
    ASSERT_EQ(ids[4], message_id);

    // No concurrency slots left for this message -> busy
    ids[12] = sendCreateVisitor(1000ms, *_top, 204);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUSY, -1, -1, &message_id));
    ASSERT_EQ(ids[12], message_id);

    // Gets a concurrent slot
    ids[13] = sendCreateVisitor(1000ms, *_top, 80);

    // Kicks pri 202 out of the queue -> busy
    ids[14] = sendCreateVisitor(1000ms, *_top, 79);

    ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUSY, -1, -1, &message_id));
    ASSERT_EQ(ids[5], message_id);

    // Gets a concurrent slot
    ids[15] = sendCreateVisitor(1000ms, *_top, 63);

    // Very Important Visitor(tm) gets a concurrent slot
    ids[16] = sendCreateVisitor(1000ms, *_top, 0);

    std::vector<document::Document::SP> docs;
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
        getMessagesAndReply(1, getSession(i), docs, docIds, api::ReturnCode::OK, priority);
        ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK, -1, -1, &message_id));
        finishedVisitors.insert(message_id);
    }

    for (int i = 0; i < 4; i++) {
        ASSERT_NE(finishedVisitors.find(ids[i]), finishedVisitors.end());
    }

    ASSERT_NE(finishedVisitors.find(ids[10]), finishedVisitors.end());
    ASSERT_NE(finishedVisitors.find(ids[13]), finishedVisitors.end());
    ASSERT_NE(finishedVisitors.find(ids[15]), finishedVisitors.end());
    ASSERT_NE(finishedVisitors.find(ids[16]), finishedVisitors.end());

    finishedVisitors.clear();

    for (int i = 8; i < 14; i++) {
        documentapi::Priority::Value priority =
            documentapi::Priority::PRI_LOWEST; // ids 6-9,11
        if (i == 8) {
            priority = documentapi::Priority::PRI_HIGH_2; // ids 14
        }
        getMessagesAndReply(1, getSession(i), docs, docIds, api::ReturnCode::OK,
                            priority);
        uint64_t msgId = 0;
        ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::OK, -1, -1, &msgId));
        finishedVisitors.insert(msgId);
    }

    for (int i = 6; i < 10; i++) {
        ASSERT_NE(finishedVisitors.find(ids[i]), finishedVisitors.end());
    }

    ASSERT_NE(finishedVisitors.find(ids[11]), finishedVisitors.end());
    ASSERT_NE(finishedVisitors.find(ids[14]), finishedVisitors.end());
}

TEST_F(VisitorManagerTest, visitor_queing_zero_queue_size) {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    ASSERT_NO_FATAL_FAILURE(initializeTest());

    _manager->setMaxConcurrentVisitors(4);
    _manager->setMaxVisitorQueueSize(0);

    // First 4 should just start..
    for (uint32_t i = 0; i < 4; ++i) {
        sendCreateVisitor(i * 1ms, *_top, i);
    }
    // Queue size is zero, all visitors will be busy-returned
    for (uint32_t i = 0; i < 5; ++i) {
        sendCreateVisitor(1000ms, *_top, 100 - i);
        ASSERT_NO_FATAL_FAILURE(verifyCreateVisitorReply(api::ReturnCode::BUSY));
    }
    for (uint32_t session = 0; session < 4; ++session) {
        finishAndWaitForVisitorSessionCompletion(session);
    }
}

TEST_F(VisitorManagerTest, status_page) {
    framework::HttpUrlPath path("?verbose=true&allvisitors=true");
    ASSERT_NO_FATAL_FAILURE(initializeTest());

    _manager->setMaxConcurrentVisitors(1, 1);
    _manager->setMaxVisitorQueueSize(6);
    // 1 running, 1 queued
    sendCreateVisitor(1000000ms, *_top, 1);
    sendCreateVisitor(1000000ms, *_top, 128);

    {
        TestVisitorMessageSession& session = getSession(0);
        session.waitForMessages(1);
    }

    std::ostringstream ss;
    static_cast<framework::HtmlStatusReporter&>(*_manager).reportHtmlStatus(ss, path);

    std::string str(ss.str());
    EXPECT_THAT(str, HasSubstr("Currently running visitors"));
    // Should be propagated to visitor thread
    EXPECT_THAT(str, HasSubstr("Running 1 visitors")); // 1 active
    EXPECT_THAT(str, HasSubstr("waiting visitors 1")); // 1 queued
    EXPECT_THAT(str, HasSubstr("Visitor thread 0"));
    EXPECT_THAT(str, HasSubstr("Disconnected visitor timeout")); // verbose per thread
    EXPECT_THAT(str, HasSubstr("Message #1 <b>putdocumentmessage</b>")); // 1 active

    for (uint32_t session = 0; session < 2 ; ++session){
        finishAndWaitForVisitorSessionCompletion(session);
    }
}

}
