// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testframe.h"

#include <vespa/documentapi/documentapi.h>
#include <vespa/documentapi/messagebus/policies/andpolicy.h>
#include <vespa/documentapi/messagebus/policies/documentrouteselectorpolicy.h>
#include <vespa/documentapi/messagebus/policies/errorpolicy.h>
#include <vespa/documentapi/messagebus/policies/externpolicy.h>
#include <vespa/documentapi/messagebus/policies/loadbalancerpolicy.h>
#include <vespa/documentapi/messagebus/policies/localservicepolicy.h>
#include <vespa/documentapi/messagebus/policies/roundrobinpolicy.h>
#include <vespa/documentapi/messagebus/policies/storagepolicy.h>
#include <vespa/documentapi/messagebus/policies/subsetservicepolicy.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingnode.h>
#include <vespa/messagebus/routing/routingtable.h>
#include <vespa/messagebus/routing/policydirective.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("policies_test");

using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::readDocumenttypesConfig;
using slobrok::api::IMirrorAPI;
using namespace documentapi;
using vespalib::make_string;
using std::make_unique;
using std::make_shared;

class Test : public vespalib::TestApp {
private:
    LoadTypeSet          _loadTypes;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DataType      *_docType;

private:
    bool trySelect(TestFrame &frame, uint32_t numSelects, const std::vector<string> &expected);
    void setupExternPolicy(TestFrame &frame, mbus::Slobrok &slobrok, const string &pattern, int32_t numEntries = -1);
    StoragePolicy &setupStoragePolicy(TestFrame &frame, const string &param,
                                      const string &pattern = "", int32_t numEntries = -1);
    bool isErrorPolicy(const string &name, const string &param);
    void assertMirrorReady(const IMirrorAPI &mirror);
    void assertMirrorContains(const IMirrorAPI &mirror, const string &pattern, uint32_t numEntries);
    mbus::Message::UP newPutDocumentMessage(const string &documentId);

public:
    Test();
    ~Test();
    int  Main() override;
    void testAND();
    void testDocumentRouteSelector();
    void testDocumentRouteSelectorIgnore();
    void remove_document_messages_are_sent_to_the_route_handling_the_given_document_type();
    void remove_document_messages_with_legacy_document_ids_are_sent_to_all_routes();
    void testExternSend();
    void testExternMultipleSlobroks();
    void testLoadBalancer();
    void testLocalService();
    void testLocalServiceCache();
    void testProtocol();
    void testRoundRobin();
    void testRoundRobinCache();
    void multipleGetRepliesAreMergedToFoundDocument();
    void testSubsetService();
    void testSubsetServiceCache();

    void requireThatExternPolicyWithIllegalParamIsAnErrorPolicy();
    void requireThatExternPolicyWithUnknownPatternSelectsNone();
    void requireThatExternPolicySelectsFromExternSlobrok();
    void requireThatExternPolicyMergesOneReplyAsProtocol();
    void requireThatStoragePolicyWithIllegalParamIsAnErrorPolicy();
    void requireThatStoragePolicyIsRandomWithoutState();
    void requireThatStoragePolicyIsTargetedWithState();
    void requireThatStoragePolicyCombinesSystemAndSlobrokState();
};

TEST_APPHOOK(Test);

Test::Test() {}
Test::~Test() {}

int
Test::Main() {
    TEST_INIT(_argv[0]);

    _repo.reset(new DocumentTypeRepo(readDocumenttypesConfig(
                TEST_PATH("../../../test/cfg/testdoctypes.cfg"))));
    _docType = _repo->getDocumentType("testdoc");

    testProtocol();                     TEST_FLUSH();

    testAND();                          TEST_FLUSH();
    testDocumentRouteSelector();        TEST_FLUSH();
    testDocumentRouteSelectorIgnore();  TEST_FLUSH();
    remove_document_messages_are_sent_to_the_route_handling_the_given_document_type(); TEST_FLUSH();
    remove_document_messages_with_legacy_document_ids_are_sent_to_all_routes();        TEST_FLUSH();
    testExternSend();                   TEST_FLUSH();
    testExternMultipleSlobroks();       TEST_FLUSH();
    testLoadBalancer();                 TEST_FLUSH();
    testLocalService();                 TEST_FLUSH();
    testLocalServiceCache();            TEST_FLUSH();
    testRoundRobin();                   TEST_FLUSH();
    testRoundRobinCache();              TEST_FLUSH();
    testSubsetService();                TEST_FLUSH();
    testSubsetServiceCache();           TEST_FLUSH();

    multipleGetRepliesAreMergedToFoundDocument(); TEST_FLUSH();

    requireThatExternPolicyWithIllegalParamIsAnErrorPolicy();  TEST_FLUSH();
    requireThatExternPolicyWithUnknownPatternSelectsNone();    TEST_FLUSH();
    requireThatExternPolicySelectsFromExternSlobrok();         TEST_FLUSH();
    requireThatExternPolicyMergesOneReplyAsProtocol();         TEST_FLUSH();

    requireThatStoragePolicyWithIllegalParamIsAnErrorPolicy(); TEST_FLUSH();
    requireThatStoragePolicyIsRandomWithoutState();            TEST_FLUSH();
    requireThatStoragePolicyIsTargetedWithState();             TEST_FLUSH();
    requireThatStoragePolicyCombinesSystemAndSlobrokState();   TEST_FLUSH();

    TEST_DONE();
}

void
Test::testProtocol()
{
    mbus::IProtocol::SP protocol(new DocumentProtocol(_loadTypes, _repo));

    mbus::IRoutingPolicy::UP policy = protocol->createPolicy("AND", "");
    ASSERT_TRUE(dynamic_cast<ANDPolicy*>(policy.get()) != nullptr);

    policy = protocol->createPolicy("DocumentRouteSelector", "raw:route[0]\n");
    ASSERT_TRUE(dynamic_cast<DocumentRouteSelectorPolicy*>(policy.get()) != nullptr);

    policy = protocol->createPolicy("Extern", "foo;bar/baz");
    ASSERT_TRUE(dynamic_cast<ExternPolicy*>(policy.get()) != nullptr);

    policy = protocol->createPolicy("LoadBalancer",
                                    "cluster=docproc/cluster.default;"
                                    "session=chain.default;syncinit");
    ASSERT_TRUE(dynamic_cast<LoadBalancerPolicy*>(policy.get()) != nullptr);

    policy = protocol->createPolicy("LocalService", "");
    ASSERT_TRUE(dynamic_cast<LocalServicePolicy*>(policy.get()) != nullptr);

    policy = protocol->createPolicy("RoundRobin", "");
    ASSERT_TRUE(dynamic_cast<RoundRobinPolicy*>(policy.get()) != nullptr);

    policy = protocol->createPolicy("SubsetService", "");
    ASSERT_TRUE(dynamic_cast<SubsetServicePolicy*>(policy.get()) != nullptr);
}

void
Test::testAND()
{
    TestFrame frame(_repo);
    frame.setMessage(make_unique<PutDocumentMessage>(make_shared<Document>(*_docType, DocumentId("doc:scheme:"))));
    frame.setHop(mbus::HopSpec("test", "[AND]")
                 .addRecipient("foo")
                 .addRecipient("bar"));
    EXPECT_TRUE(frame.testSelect(StringList().add("foo").add("bar")));

    frame.setHop(mbus::HopSpec("test", "[AND:baz]")
                 .addRecipient("foo").addRecipient("bar"));
    EXPECT_TRUE(frame.testSelect(StringList().add("baz"))); // param precedes recipients

    frame.setHop(mbus::HopSpec("test", "[AND:foo]"));
    EXPECT_TRUE(frame.testMergeOneReply("foo"));

    frame.setHop(mbus::HopSpec("test", "[AND:foo bar]"));
    EXPECT_TRUE(frame.testMergeTwoReplies("foo", "bar"));
}

void
Test::requireThatExternPolicyWithIllegalParamIsAnErrorPolicy()
{
    mbus::Slobrok slobrok;
    string spec = vespalib::make_string("tcp/localhost:%d", slobrok.port());

    EXPECT_TRUE(isErrorPolicy("Extern", ""));
    EXPECT_TRUE(isErrorPolicy("Extern", spec));
    EXPECT_TRUE(isErrorPolicy("Extern", spec + ";"));
    EXPECT_TRUE(isErrorPolicy("Extern", spec + ";bar"));
}

void
Test::requireThatExternPolicyWithUnknownPatternSelectsNone()
{
    TestFrame frame(_repo);
    frame.setMessage(newPutDocumentMessage("doc:scheme:"));

    mbus::Slobrok slobrok;
    setupExternPolicy(frame, slobrok, "foo/bar");
    EXPECT_TRUE(frame.testSelect(StringList()));
}

void
Test::requireThatExternPolicySelectsFromExternSlobrok()
{
    TestFrame frame(_repo);
    frame.setMessage(newPutDocumentMessage("doc:scheme:"));
    mbus::Slobrok slobrok;
    std::vector<mbus::TestServer*> servers;
    for (uint32_t i = 0; i < 10; ++i) {
        mbus::TestServer *server = new mbus::TestServer(
                mbus::Identity(make_string("docproc/cluster.default/%d", i)), mbus::RoutingSpec(), slobrok,
                mbus::IProtocol::SP(new DocumentProtocol(_loadTypes, _repo)));
        servers.push_back(server);
        server->net.registerSession("chain.default");
    }
    setupExternPolicy(frame, slobrok, "docproc/cluster.default/*/chain.default", 10);
    std::set<string> lst;
    for (uint32_t i = 0; i < servers.size(); ++i) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        lst.insert(leaf[0]->getRoute().toString());

        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
        ASSERT_TRUE(frame.getReceptor().getReply(600));
    }
    EXPECT_EQUAL(servers.size(), lst.size());
    for (uint32_t i = 0; i < servers.size(); ++i) {
        delete servers[i];
    }
}

void
Test::requireThatExternPolicyMergesOneReplyAsProtocol()
{
    TestFrame frame(_repo);
    frame.setMessage(newPutDocumentMessage("doc:scheme:"));
    mbus::Slobrok slobrok;
    mbus::TestServer server(mbus::Identity("docproc/cluster.default/0"), mbus::RoutingSpec(), slobrok,
                            mbus::IProtocol::SP(new DocumentProtocol(_loadTypes, _repo)));
    server.net.registerSession("chain.default");
    setupExternPolicy(frame, slobrok, "docproc/cluster.default/0/chain.default", 1);
    EXPECT_TRUE(frame.testMergeOneReply(server.net.getConnectionSpec() + "/chain.default"));
}

mbus::Message::UP
Test::newPutDocumentMessage(const string &documentId)
{
    Document::SP doc(new Document(*_docType, DocumentId(documentId)));
    return make_unique<PutDocumentMessage>(doc);
}

void
Test::setupExternPolicy(TestFrame &frame, mbus::Slobrok &slobrok, const string &pattern,
                        int32_t numEntries)
{
    string param = vespalib::make_string("tcp/localhost:%d;%s", slobrok.port(), pattern.c_str());
    frame.setHop(mbus::HopSpec("test", vespalib::make_string("[Extern:%s]", param.c_str())));
    mbus::MessageBus &mbus = frame.getMessageBus();
    const mbus::HopBlueprint *hop = mbus.getRoutingTable(DocumentProtocol::NAME)->getHop("test");
    const mbus::PolicyDirective dir = static_cast<mbus::PolicyDirective&>(*hop->getDirective(0));
    ExternPolicy &policy = static_cast<ExternPolicy&>(*mbus.getRoutingPolicy(
                    DocumentProtocol::NAME,
                    dir.getName(),
                    dir.getParam()));
    assertMirrorReady(policy.getMirror());
    if (numEntries >= 0) {
        assertMirrorContains(policy.getMirror(), pattern, numEntries);
    }
}

void
Test::assertMirrorReady(const slobrok::api::IMirrorAPI &mirror)
{
    for (uint32_t i = 0; i < 6000; ++i) {
        if (mirror.ready()) {
            return;
        }
        FastOS_Thread::Sleep(10);
    }
    ASSERT_TRUE(false);
}

void
Test::assertMirrorContains(const slobrok::api::IMirrorAPI &mirror, const string &pattern,
                           uint32_t numEntries)
{
    for (uint32_t i = 0; i < 6000; ++i) {
        if (mirror.lookup(pattern).size() == numEntries) {
            return;
        }
        FastOS_Thread::Sleep(10);
    }
    ASSERT_TRUE(false);
}

void
Test::testExternSend()
{
    // Setup local source node.
    mbus::Slobrok local;
    mbus::TestServer src(mbus::Identity("src"), mbus::RoutingSpec(), local,
                         std::make_shared<DocumentProtocol>(_loadTypes, _repo));
    mbus::Receptor sr;
    mbus::SourceSession::UP ss = src.mb.createSourceSession(sr, mbus::SourceSessionParams().setTimeout(60));

    mbus::Slobrok slobrok;
    mbus::TestServer itr(mbus::Identity("itr"), mbus::RoutingSpec()
                         .addTable(mbus::RoutingTableSpec(DocumentProtocol::NAME)
                                   .addRoute(mbus::RouteSpec("default").addHop("dst"))
                                   .addHop(mbus::HopSpec("dst", "dst/session"))),
                         slobrok, std::make_shared<DocumentProtocol>(_loadTypes, _repo));
    mbus::Receptor ir;
    mbus::IntermediateSession::UP is = itr.mb.createIntermediateSession("session", true, ir, ir);

    mbus::TestServer dst(mbus::Identity("dst"), mbus::RoutingSpec(), slobrok,
                         std::make_shared<DocumentProtocol>(_loadTypes, _repo));
    mbus::Receptor dr;
    mbus::DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dr);

    // Send message from local node to remote cluster and resolve route there.
    mbus::Message::UP msg(new GetDocumentMessage(DocumentId("doc:scheme:"), 0));
    msg->getTrace().setLevel(9);
    msg->setRoute(mbus::Route::parse(vespalib::make_string("[Extern:tcp/localhost:%d;itr/session] default", slobrok.port())));

    ASSERT_TRUE(ss->send(std::move(msg)).isAccepted());
    ASSERT_TRUE((msg = ir.getMessage(600)));
    is->forward(std::move(msg));
    ASSERT_TRUE((msg = dr.getMessage(600)));
    ds->acknowledge(std::move(msg));
    mbus::Reply::UP reply = ir.getReply(600);
    ASSERT_TRUE(reply);
    is->forward(std::move(reply));
    ASSERT_TRUE((reply = sr.getReply(600)));

    fprintf(stderr, "%s", reply->getTrace().toString().c_str());
}

void
Test::testExternMultipleSlobroks()
{
    mbus::Slobrok local;
    mbus::TestServer src(mbus::Identity("src"), mbus::RoutingSpec(), local,
                         std::make_shared<DocumentProtocol>(_loadTypes, _repo));
    mbus::Receptor sr;
    mbus::SourceSession::UP ss = src.mb.createSourceSession(sr, mbus::SourceSessionParams().setTimeout(60));

    string spec;
    mbus::Receptor dr;
    {
        mbus::Slobrok ext;
        spec.append(vespalib::make_string("tcp/localhost:%d", ext.port()));

        mbus::TestServer dst(mbus::Identity("dst"), mbus::RoutingSpec(), ext,
                             std::make_shared<DocumentProtocol>(_loadTypes, _repo));
        mbus::DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dr);

        mbus::Message::UP msg(new GetDocumentMessage(DocumentId("doc:scheme:"), 0));
        msg->setRoute(mbus::Route::parse(vespalib::make_string("[Extern:%s;dst/session]", spec.c_str())));
        ASSERT_TRUE(ss->send(std::move(msg)).isAccepted());
        ASSERT_TRUE((msg = dr.getMessage(600)));
        ds->acknowledge(std::move(msg));
        mbus::Reply::UP reply = sr.getReply(600);
        ASSERT_TRUE(reply);
    }
    {
        mbus::Slobrok ext;
        spec.append(vespalib::make_string(",tcp/localhost:%d", ext.port()));

        mbus::TestServer dst(mbus::Identity("dst"), mbus::RoutingSpec(), ext,
                             std::make_shared<DocumentProtocol>(_loadTypes, _repo));
        mbus::DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dr);

        mbus::Message::UP msg(new GetDocumentMessage(DocumentId("doc:scheme:"), 0));
        msg->setRoute(mbus::Route::parse(vespalib::make_string("[Extern:%s;dst/session]", spec.c_str())));
        ASSERT_TRUE(ss->send(std::move(msg)).isAccepted());
        ASSERT_TRUE((msg = dr.getMessage(600)));
        ds->acknowledge(std::move(msg));
        mbus::Reply::UP reply = sr.getReply(600);
        ASSERT_TRUE(reply);
    }
}

void
Test::testLocalService()
{
    // Prepare message.
    TestFrame frame(_repo, "docproc/cluster.default");
    frame.setMessage(make_unique<PutDocumentMessage>(make_shared<Document>(*_docType, DocumentId("doc:scheme:"))));

    // Test select with proper address.
    for (uint32_t i = 0; i < 10; ++i) {
        frame.getNetwork().registerSession(vespalib::make_string("%d/chain.default", i));
    }
    ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 10));
    frame.setHop(mbus::HopSpec("test", "docproc/cluster.default/[LocalService]/chain.default"));

    std::set<string> lst;
    for (uint32_t i = 0; i < 10; ++i) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        lst.insert(leaf[0]->getRoute().toString());

        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
        ASSERT_TRUE(frame.getReceptor().getReply(600));
    }
    EXPECT_EQUAL(10u, lst.size());

    // Test select with broken address.
    lst.clear();
    frame.setHop(mbus::HopSpec("test", "docproc/cluster.default/[LocalService:broken]/chain.default"));
    for (uint32_t i = 0; i < 10; ++i) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        lst.insert(leaf[0]->getRoute().toString());

        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
        ASSERT_TRUE(frame.getReceptor().getReply(600));
    }
    EXPECT_EQUAL(1u, lst.size());
    EXPECT_EQUAL("docproc/cluster.default/*/chain.default", *lst.begin());

    // Test merge behavior.
    frame.setHop(mbus::HopSpec("test", "[LocalService]"));
    EXPECT_TRUE(frame.testMergeOneReply("*"));
}

void
Test::testLocalServiceCache()
{
    TestFrame fooFrame(_repo, "docproc/cluster.default");
    mbus::HopSpec fooHop("foo", "docproc/cluster.default/[LocalService]/chain.foo");
    fooFrame.setMessage(make_unique<GetDocumentMessage>(DocumentId("doc:scheme:foo")));
    fooFrame.setHop(fooHop);

    TestFrame barFrame(fooFrame);
    mbus::HopSpec barHop("test", "docproc/cluster.default/[LocalService]/chain.bar");
    barFrame.setMessage(mbus::Message::UP(new GetDocumentMessage(DocumentId("doc:scheme:bar"))));
    barFrame.setHop(barHop);

    fooFrame.getMessageBus().setupRouting(
            mbus::RoutingSpec().addTable(mbus::RoutingTableSpec(DocumentProtocol::NAME)
                                         .addHop(fooHop)
                                         .addHop(barHop)));

    fooFrame.getNetwork().registerSession("0/chain.foo");
    fooFrame.getNetwork().registerSession("0/chain.bar");
    ASSERT_TRUE(fooFrame.waitSlobrok("docproc/cluster.default/0/*", 2));

    std::vector<mbus::RoutingNode*> fooSelected;
    ASSERT_TRUE(fooFrame.select(fooSelected, 1));
    EXPECT_EQUAL("docproc/cluster.default/0/chain.foo", fooSelected[0]->getRoute().getHop(0).toString());

    std::vector<mbus::RoutingNode*> barSelected;
    ASSERT_TRUE(barFrame.select(barSelected, 1));
    EXPECT_EQUAL("docproc/cluster.default/0/chain.bar", barSelected[0]->getRoute().getHop(0).toString());

    barSelected[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
    fooSelected[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));

    ASSERT_TRUE(barFrame.getReceptor().getReply(600));
    ASSERT_TRUE(fooFrame.getReceptor().getReply(600));
}

void
Test::testRoundRobin()
{
    // Prepare message.
    TestFrame frame(_repo, "docproc/cluster.default");
    frame.setMessage(make_unique<PutDocumentMessage>(make_shared<Document>(*_docType, DocumentId("doc:scheme:"))));

    // Test select with proper address.
    for (uint32_t i = 0; i < 10; ++i) {
        frame.getNetwork().registerSession(vespalib::make_string("%d/chain.default", i));
    }
    ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 10));
    frame.setHop(mbus::HopSpec("test", "[RoundRobin]")
                 .addRecipient("docproc/cluster.default/3/chain.default")
                 .addRecipient("docproc/cluster.default/6/chain.default")
                 .addRecipient("docproc/cluster.default/9/chain.default"));
    EXPECT_TRUE(trySelect(frame, 32, StringList()
                          .add("docproc/cluster.default/3/chain.default")
                          .add("docproc/cluster.default/6/chain.default")
                          .add("docproc/cluster.default/9/chain.default")));
    frame.getNetwork().unregisterSession("6/chain.default");
    ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 9));
    EXPECT_TRUE(trySelect(frame, 32, StringList()
                          .add("docproc/cluster.default/3/chain.default")
                          .add("docproc/cluster.default/9/chain.default")));
    frame.getNetwork().unregisterSession("3/chain.default");
    ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 8));
    EXPECT_TRUE(trySelect(frame, 32, StringList()
                          .add("docproc/cluster.default/9/chain.default")));
    frame.getNetwork().unregisterSession("9/chain.default");
    ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 7));
    EXPECT_TRUE(trySelect(frame, 32, StringList()));

    // Test merge behavior.
    frame.setHop(mbus::HopSpec("test", "[RoundRobin]").addRecipient("docproc/cluster.default/0/chain.default"));
    EXPECT_TRUE(frame.testMergeOneReply("docproc/cluster.default/0/chain.default"));
}

void
Test::testRoundRobinCache()
{
    TestFrame fooFrame(_repo, "docproc/cluster.default");
    mbus::HopSpec fooHop("foo", "[RoundRobin]");
    fooHop.addRecipient("docproc/cluster.default/0/chain.foo");
    fooFrame.setMessage(mbus::Message::UP(new GetDocumentMessage(DocumentId("doc:scheme:foo"))));
    fooFrame.setHop(fooHop);

    TestFrame barFrame(fooFrame);
    mbus::HopSpec barHop("bar", "[RoundRobin]");
    barHop.addRecipient("docproc/cluster.default/0/chain.bar");
    barFrame.setMessage(mbus::Message::UP(new GetDocumentMessage(DocumentId("doc:scheme:bar"))));
    barFrame.setHop(barHop);

    fooFrame.getMessageBus().setupRouting(
            mbus::RoutingSpec().addTable(mbus::RoutingTableSpec(DocumentProtocol::NAME)
                                         .addHop(fooHop)
                                         .addHop(barHop)));

    fooFrame.getNetwork().registerSession("0/chain.foo");
    fooFrame.getNetwork().registerSession("0/chain.bar");
    ASSERT_TRUE(fooFrame.waitSlobrok("docproc/cluster.default/0/*", 2));

    std::vector<mbus::RoutingNode*> fooSelected;
    ASSERT_TRUE(fooFrame.select(fooSelected, 1));
    EXPECT_EQUAL("docproc/cluster.default/0/chain.foo", fooSelected[0]->getRoute().getHop(0).toString());

    std::vector<mbus::RoutingNode*> barSelected;
    ASSERT_TRUE(barFrame.select(barSelected, 1));
    EXPECT_EQUAL("docproc/cluster.default/0/chain.bar", barSelected[0]->getRoute().getHop(0).toString());

    barSelected[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
    fooSelected[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));

    ASSERT_TRUE(barFrame.getReceptor().getReply(600));
    ASSERT_TRUE(fooFrame.getReceptor().getReply(600));
}

void
Test::multipleGetRepliesAreMergedToFoundDocument()
{
    TestFrame frame(_repo);
    frame.setHop(mbus::HopSpec("test", "[DocumentRouteSelector:raw:"
                               "route[2]\n"
                               "route[0].name \"foo\"\n"
                               "route[0].selector \"testdoc\"\n"
                               "route[0].feed \"myfeed\"\n"
                               "route[1].name \"bar\"\n"
                               "route[1].selector \"other\"\n"
                               "route[1].feed \"myfeed\"\n]")
                 .addRecipient("foo")
                 .addRecipient("bar"));
    frame.setMessage(make_unique<GetDocumentMessage>(DocumentId("doc:scheme:yarn")));
    std::vector<mbus::RoutingNode*> selected;
    EXPECT_TRUE(frame.select(selected, 2));
    for (uint32_t i = 0, len = selected.size(); i < len; ++i) {
        Document::SP doc;
        if (i == 0) {
            doc.reset(new Document(*_docType, DocumentId("doc:scheme:yarn")));
            doc->setLastModified(123456ULL);
        }
        mbus::Reply::UP reply(new GetDocumentReply(std::move(doc)));
        selected[i]->handleReply(std::move(reply));
    }
    mbus::Reply::UP reply = frame.getReceptor().getReply(600);
    EXPECT_TRUE(reply);
    EXPECT_EQUAL(static_cast<uint32_t>(DocumentProtocol::REPLY_GETDOCUMENT), reply->getType());
    EXPECT_EQUAL(123456ULL, static_cast<GetDocumentReply&>(*reply).getLastModified());
}

void
Test::testDocumentRouteSelector()
{
    // Test policy with usage safeguard.
    string okConfig = "raw:route[0]\n";
    string errConfig = "raw:"
        "route[1]\n"
        "route[0].name \"foo\"\n"
        "route[0].selector \"foo bar\"\n"
        "route[0].feed \"baz\"\n";
    {
        DocumentProtocol protocol(_loadTypes, _repo, okConfig);
        EXPECT_TRUE(dynamic_cast<DocumentRouteSelectorPolicy*>(protocol.createPolicy("DocumentRouteSelector", "").get()) != nullptr);
        EXPECT_TRUE(dynamic_cast<ErrorPolicy*>(protocol.createPolicy("DocumentRouteSelector", errConfig).get()) != nullptr);
    }
    {
        DocumentProtocol protocol(_loadTypes, _repo, errConfig);
        EXPECT_TRUE(dynamic_cast<ErrorPolicy*>(protocol.createPolicy("DocumentRouteSelector", "").get()) != nullptr);
        EXPECT_TRUE(dynamic_cast<DocumentRouteSelectorPolicy*>(protocol.createPolicy("DocumentRouteSelector", okConfig).get()) != nullptr);
    }

    // Test policy with proper config.
    TestFrame frame(_repo);
    frame.setHop(mbus::HopSpec("test", "[DocumentRouteSelector:raw:"
                               "route[2]\n"
                               "route[0].name \"foo\"\n"
                               "route[0].selector \"testdoc\"\n"
                               "route[0].feed \"myfeed\"\n"
                               "route[1].name \"bar\"\n"
                               "route[1].selector \"other\"\n"
                               "route[1].feed \"myfeed\"\n]")
                 .addRecipient("foo")
                 .addRecipient("bar"));

    frame.setMessage(make_unique<GetDocumentMessage>(DocumentId("doc:scheme:"), 0));
    EXPECT_TRUE(frame.testSelect(StringList().add("foo").add("bar")));

    mbus::Message::UP put = make_unique<PutDocumentMessage>(make_shared<Document>(*_docType, DocumentId("doc:scheme:")));
    frame.setMessage(std::move(put));
    EXPECT_TRUE(frame.testSelect( StringList().add("foo")));

    frame.setMessage(mbus::Message::UP(new RemoveDocumentMessage(DocumentId("doc:scheme:"))));
    EXPECT_TRUE(frame.testSelect(StringList().add("foo").add("bar")));

    frame.setMessage(make_unique<UpdateDocumentMessage>(
            make_shared<DocumentUpdate>(*_docType, DocumentId("doc:scheme:"))));
    EXPECT_TRUE(frame.testSelect(StringList().add("foo")));

    put = make_unique<PutDocumentMessage>(make_shared<Document>(*_docType, DocumentId("doc:scheme:")));
    frame.setMessage(std::move(put));
    EXPECT_TRUE(frame.testMergeOneReply("foo"));
}

void
Test::testDocumentRouteSelectorIgnore()
{
    TestFrame frame(_repo);
    frame.setHop(mbus::HopSpec("test", "[DocumentRouteSelector:raw:"
                               "route[1]\n"
                               "route[0].name \"docproc/cluster.foo\"\n"
                               "route[0].selector \"testdoc and testdoc.stringfield == 'foo'\"\n"
                               "route[0].feed \"myfeed\"\n]")
                 .addRecipient("docproc/cluster.foo"));

    frame.setMessage(make_unique<PutDocumentMessage>(
            make_shared<Document>(*_docType, DocumentId("id:yarn:testdoc:n=1234:fluff"))));
    std::vector<mbus::RoutingNode*> leaf;
    ASSERT_TRUE(frame.select(leaf, 0));
    mbus::Reply::UP reply = frame.getReceptor().getReply(600);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(uint32_t(DocumentProtocol::REPLY_DOCUMENTIGNORED), reply->getType());
    EXPECT_EQUAL(0u, reply->getNumErrors());

    frame.setMessage(make_unique<UpdateDocumentMessage>(
            make_shared<DocumentUpdate>(*_docType, DocumentId("doc:scheme:"))));
    EXPECT_TRUE(frame.testSelect(StringList().add("docproc/cluster.foo")));
}

namespace {

vespalib::string
createDocumentRouteSelectorConfigWithTwoRoutes()
{
    return "[DocumentRouteSelector:raw:"
            "route[2]\n"
            "route[0].name \"testdoc-route\"\n"
            "route[0].selector \"testdoc and testdoc.stringfield != '0'\"\n"
            "route[0].feed \"\"\n"
            "route[1].name \"other-route\"\n"
            "route[1].selector \"other and other.intfield != '0'\"\n"
            "route[1].feed \"\"\n]";
}

std::unique_ptr<TestFrame>
createFrameWithTwoRoutes(std::shared_ptr<const DocumentTypeRepo> repo)
{
    auto result = std::make_unique<TestFrame>(repo);
    result->setHop(mbus::HopSpec("test", createDocumentRouteSelectorConfigWithTwoRoutes())
                           .addRecipient("testdoc-route").addRecipient("other-route"));
    return result;
}

std::unique_ptr<RemoveDocumentMessage>
makeRemove(vespalib::string docId)
{
    return std::make_unique<RemoveDocumentMessage>(DocumentId(docId));
}

}

void
Test::remove_document_messages_are_sent_to_the_route_handling_the_given_document_type()
{
    auto frame = createFrameWithTwoRoutes(_repo);

    frame->setMessage(makeRemove("id:ns:testdoc::1"));
    EXPECT_TRUE(frame->testSelect({"testdoc-route"}));

    frame->setMessage(makeRemove("id:ns:other::1"));
    EXPECT_TRUE(frame->testSelect({"other-route"}));
}

void
Test::remove_document_messages_with_legacy_document_ids_are_sent_to_all_routes()
{
    auto frame = createFrameWithTwoRoutes(_repo);

    frame->setMessage(makeRemove("userdoc:testdoc:1234:1"));
    EXPECT_TRUE(frame->testSelect({"testdoc-route", "other-route"}));

    frame->setMessage(makeRemove("userdoc:other:1234:1"));
    EXPECT_TRUE(frame->testSelect({"testdoc-route", "other-route"}));
}

namespace {
    string getDefaultDistributionConfig(
                    uint16_t redundancy = 2, uint16_t nodeCount = 10,
                    storage::lib::Distribution::DiskDistribution distr
                            = storage::lib::Distribution::MODULO_BID)
    {
        std::ostringstream ost;
        ost << "raw:redundancy " << redundancy << "\n"
            << "group[1]\n"
            << "group[0].index \"invalid\"\n"
            << "group[0].name \"invalid\"\n"
            << "group[0].partitions \"*\"\n"
            << "group[0].nodes[" << nodeCount << "]\n";
        for (uint16_t i=0; i<nodeCount; ++i) {
            ost << "group[0].nodes[" << i << "].index " << i << "\n";
        }
        ost << "disk_distribution "
            << storage::lib::Distribution::getDiskDistributionName(distr)
            << "\n";
        return ost.str();
    }
}

void Test::testLoadBalancer() {
    LoadBalancer lb("foo", "");

    IMirrorAPI::SpecList entries;
    entries.push_back(IMirrorAPI::Spec("foo/0/default", "tcp/bar:1"));
    entries.push_back(IMirrorAPI::Spec("foo/1/default", "tcp/bar:2"));
    entries.push_back(IMirrorAPI::Spec("foo/2/default", "tcp/bar:3"));

    const std::vector<LoadBalancer::NodeInfo>& nodeInfo = lb.getNodeInfo();

    for (int i = 0; i < 99; i++) {
        std::pair<string, int> recipient = lb.getRecipient(entries);
        EXPECT_EQUAL((i % 3), recipient.second);
    }

    // Simulate that one node is overloaded. It returns busy twice as often as the others.
    for (int i = 0; i < 100; i++) {
        lb.received(0, true);
        lb.received(0, false);
        lb.received(0, false);
        lb.received(2, true);
        lb.received(2, false);
        lb.received(2, false);
        lb.received(1, true);
        lb.received(1, true);
        lb.received(1, false);
    }

    EXPECT_EQUAL(421, (int)(100 * nodeInfo[0].weight / nodeInfo[1].weight));
    EXPECT_EQUAL(421, (int)(100 * nodeInfo[2].weight / nodeInfo[1].weight));

    EXPECT_EQUAL(0 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(0 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(1 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(2 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(2 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(2 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(2 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(0 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(0 , lb.getRecipient(entries).second);
    EXPECT_EQUAL(0 , lb.getRecipient(entries).second);
}

void
Test::requireThatStoragePolicyWithIllegalParamIsAnErrorPolicy()
{
    EXPECT_TRUE(isErrorPolicy("Storage", ""));
    EXPECT_TRUE(isErrorPolicy("Storage", "config=foo;slobroks=foo"));
    EXPECT_TRUE(isErrorPolicy("Storage", "slobroks=foo"));
}

void
Test::requireThatStoragePolicyIsRandomWithoutState()
{
    TestFrame frame(_repo);
    frame.setMessage(newPutDocumentMessage("doc:scheme:"));

    mbus::Slobrok slobrok;
    std::vector<mbus::TestServer*> servers;
    for (uint32_t i = 0; i < 5; ++i) {
        mbus::TestServer *srv = new mbus::TestServer(
                mbus::Identity(vespalib::make_string("storage/cluster.mycluster/distributor/%d", i)),
                mbus::RoutingSpec(), slobrok,
                mbus::IProtocol::SP(new DocumentProtocol(_loadTypes, _repo)));
        servers.push_back(srv);
        srv->net.registerSession("default");
    }
    string param = vespalib::make_string(
            "cluster=mycluster;slobroks=tcp/localhost:%d;clusterconfigid=%s;syncinit",
            slobrok.port(), getDefaultDistributionConfig(2, 5).c_str());
    StoragePolicy &policy = setupStoragePolicy(
            frame, param,
            "storage/cluster.mycluster/distributor/*/default", 5);
    ASSERT_TRUE(policy.getSystemState() == nullptr);

    std::set<string> lst;
    for (uint32_t i = 0; i < 666; i++) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        lst.insert(leaf[0]->getRoute().toString());
        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
    }
    EXPECT_EQUAL(servers.size(), lst.size());
    for (uint32_t i = 0; i < servers.size(); ++i) {
        delete servers[i];
    }
}

StoragePolicy &
Test::setupStoragePolicy(TestFrame &frame, const string &param,
                         const string &pattern, int32_t numEntries)
{
    frame.setHop(mbus::HopSpec("test", vespalib::make_string("[Storage:%s]", param.c_str())));
    mbus::MessageBus &mbus = frame.getMessageBus();
    const mbus::HopBlueprint *hop = mbus.getRoutingTable(DocumentProtocol::NAME)->getHop("test");
    const mbus::PolicyDirective dir = static_cast<mbus::PolicyDirective&>(*hop->getDirective(0));
    StoragePolicy &policy = static_cast<StoragePolicy&>(*mbus.getRoutingPolicy(DocumentProtocol::NAME,
                                                                               dir.getName(), dir.getParam()));
    policy.initSynchronous();
    assertMirrorReady(*policy.getMirror());
    if (numEntries >= 0) {
        assertMirrorContains(*policy.getMirror(), pattern, numEntries);
    }
    return policy;
}

void
Test::requireThatStoragePolicyIsTargetedWithState()
{
    TestFrame frame(_repo);
    frame.setMessage(newPutDocumentMessage("doc:scheme:"));

    mbus::Slobrok slobrok;
    std::vector<mbus::TestServer*> servers;
    for (uint32_t i = 0; i < 5; ++i) {
        mbus::TestServer *srv = new mbus::TestServer(
                mbus::Identity(vespalib::make_string("storage/cluster.mycluster/distributor/%d", i)),
                mbus::RoutingSpec(), slobrok,
                make_shared<DocumentProtocol>(_loadTypes, _repo));
        servers.push_back(srv);
        srv->net.registerSession("default");
    }
    string param = vespalib::make_string(
            "cluster=mycluster;slobroks=tcp/localhost:%d;clusterconfigid=%s;syncinit",
            slobrok.port(), getDefaultDistributionConfig(2, 5).c_str());
    StoragePolicy &policy = setupStoragePolicy(
            frame, param,
            "storage/cluster.mycluster/distributor/*/default", 5);
    ASSERT_TRUE(policy.getSystemState() == nullptr);
    {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        leaf[0]->handleReply(mbus::Reply::UP(new WrongDistributionReply("distributor:5 storage:5")));
        ASSERT_TRUE(policy.getSystemState() != nullptr);
        EXPECT_EQUAL(policy.getSystemState()->toString(), "distributor:5 storage:5");
    }
    std::set<string> lst;
    for (int i = 0; i < 666; i++) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        lst.insert(leaf[0]->getRoute().toString());
        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
    }
    EXPECT_EQUAL(1u, lst.size());
    for (uint32_t i = 0; i < servers.size(); ++i) {
        delete servers[i];
    }
}

void
Test::requireThatStoragePolicyCombinesSystemAndSlobrokState()
{
    TestFrame frame(_repo);
    frame.setMessage(newPutDocumentMessage("doc:scheme:"));

    mbus::Slobrok slobrok;
    mbus::TestServer server(mbus::Identity("storage/cluster.mycluster/distributor/0"),
                            mbus::RoutingSpec(), slobrok,
                            make_shared<DocumentProtocol>(_loadTypes, _repo));
    server.net.registerSession("default");

    string param = vespalib::make_string(
            "cluster=mycluster;slobroks=tcp/localhost:%d;clusterconfigid=%s;syncinit",
            slobrok.port(), getDefaultDistributionConfig(2, 5).c_str());
    StoragePolicy &policy = setupStoragePolicy(
            frame, param,
            "storage/cluster.mycluster/distributor/*/default", 1);
    ASSERT_TRUE(policy.getSystemState() == nullptr);
    {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        leaf[0]->handleReply(mbus::Reply::UP(new WrongDistributionReply("distributor:99 storage:99")));
        ASSERT_TRUE(policy.getSystemState() != nullptr);
        EXPECT_EQUAL(policy.getSystemState()->toString(), "distributor:99 storage:99");
    }
    for (int i = 0; i < 666; i++) {
        ASSERT_TRUE(frame.testSelect(StringList().add(server.net.getConnectionSpec() + "/default")));
    }
}

void
Test::testSubsetService()
{
    // Prepare message.
    TestFrame frame(_repo, "docproc/cluster.default");
    frame.setMessage(make_unique<PutDocumentMessage>(make_shared<Document>(*_docType, DocumentId("doc:scheme:"))));

    // Test requerying for adding nodes.
    frame.setHop(mbus::HopSpec("test", "docproc/cluster.default/[SubsetService:2]/chain.default"));
    std::set<string> lst;
    for (uint32_t i = 1; i <= 10; ++i) {
        frame.getNetwork().registerSession(vespalib::make_string("%d/chain.default", i));
        ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", i));

        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        lst.insert(leaf[0]->getRoute().toString());
        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
        ASSERT_TRUE(frame.getReceptor().getReply(600));
    }
    ASSERT_TRUE(lst.size() > 1); // must have requeried

    // Test load balancing.
    string prev = "";
    for (uint32_t i = 1; i <= 10; ++i) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));

        string next = leaf[0]->getRoute().toString();
        if (prev.empty()) {
            ASSERT_TRUE(!next.empty());
        } else {
            ASSERT_TRUE(prev != next);
        }

        prev = next;
        leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
        ASSERT_TRUE(frame.getReceptor().getReply(600));
    }

    // Test requerying for dropping nodes.
    lst.clear();
    for (uint32_t i = 1; i <= 10; ++i) {
        std::vector<mbus::RoutingNode*> leaf;
        ASSERT_TRUE(frame.select(leaf, 1));
        string route = leaf[0]->getRoute().toString();
        lst.insert(route);

        frame.getNetwork().unregisterSession(route.substr(frame.getIdentity().length() + 1));
        ASSERT_TRUE(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 10 - i));

        mbus::Reply::UP reply(new mbus::EmptyReply());
        reply->addError(mbus::Error(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE, route));
        leaf[0]->handleReply(std::move(reply));
        ASSERT_TRUE(frame.getReceptor().getReply(600));
    }
    EXPECT_EQUAL(10u, lst.size());

    // Test merge behavior.
    frame.setHop(mbus::HopSpec("test", "[SubsetService]"));
    EXPECT_TRUE(frame.testMergeOneReply("*"));
}

void
Test::testSubsetServiceCache()
{
    TestFrame fooFrame(_repo, "docproc/cluster.default");
    mbus::HopSpec fooHop("foo", "docproc/cluster.default/[SubsetService:2]/chain.foo");
    fooFrame.setMessage(mbus::Message::UP(new GetDocumentMessage(DocumentId("doc:scheme:foo"))));
    fooFrame.setHop(fooHop);

    TestFrame barFrame(fooFrame);
    mbus::HopSpec barHop("bar", "docproc/cluster.default/[SubsetService:2]/chain.bar");
    barFrame.setMessage(mbus::Message::UP(new GetDocumentMessage(DocumentId("doc:scheme:bar"))));
    barFrame.setHop(barHop);

    fooFrame.getMessageBus().setupRouting(
            mbus::RoutingSpec().addTable(mbus::RoutingTableSpec(DocumentProtocol::NAME)
                                         .addHop(fooHop)
                                         .addHop(barHop)));

    fooFrame.getNetwork().registerSession("0/chain.foo");
    fooFrame.getNetwork().registerSession("0/chain.bar");
    ASSERT_TRUE(fooFrame.waitSlobrok("docproc/cluster.default/0/*", 2));

    std::vector<mbus::RoutingNode*> fooSelected;
    ASSERT_TRUE(fooFrame.select(fooSelected, 1));
    EXPECT_EQUAL("docproc/cluster.default/0/chain.foo", fooSelected[0]->getRoute().getHop(0).toString());

    std::vector<mbus::RoutingNode*> barSelected;
    ASSERT_TRUE(barFrame.select(barSelected, 1));
    EXPECT_EQUAL("docproc/cluster.default/0/chain.bar", barSelected[0]->getRoute().getHop(0).toString());

    barSelected[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
    fooSelected[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));

    ASSERT_TRUE(barFrame.getReceptor().getReply(600));
    ASSERT_TRUE(fooFrame.getReceptor().getReply(600));
}

bool
Test::trySelect(TestFrame &frame, uint32_t numSelects, const std::vector<string> &expected) {
    std::set<string> lst;
    for (uint32_t i = 0; i < numSelects; ++i) {
        std::vector<mbus::RoutingNode*> leaf;
        if (!expected.empty()) {
            frame.select(leaf, 1);
            lst.insert(leaf[0]->getRoute().toString());
            leaf[0]->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
        } else {
            frame.select(leaf, 0);
        }
        if( ! frame.getReceptor().getReply(600)) {
            LOG(error, "Reply failed to propagate to reply handler.");
            return false;
        }
    }
    if (expected.size() != lst.size()) {
        LOG(error, "Expected %d recipients, got %d.", (uint32_t)expected.size(), (uint32_t)lst.size());
        return false;
    }
    std::set<string>::iterator it = lst.begin();
    for (uint32_t i = 0; i < expected.size(); ++i, ++it) {
        if (*it != expected[i]) {
            LOG(error, "Expected '%s', got '%s'.", expected[i].c_str(), it->c_str());
            return false;
        }
    }
    return true;
}

bool
Test::isErrorPolicy(const string &name, const string &param)
{
    DocumentProtocol protocol(_loadTypes, _repo);
    mbus::IRoutingPolicy::UP policy = protocol.createPolicy(name, param);

    return policy && dynamic_cast<ErrorPolicy*>(policy.get()) != nullptr;
}

