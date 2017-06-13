// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/base/documentid.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/engine/transportserver.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/fnet.h>
#include <vespa/searchlib/engine/errorcodes.h>
#include <vespa/log/log.h>
LOG_SETUP("transportserver_test");

using namespace document;
using namespace vespalib;
using namespace search::engine;
using namespace search::fs4transport;

class SyncServer : public search::engine::SearchServer,
                   public search::engine::DocsumServer,
                   public search::engine::MonitorServer
{
private:
    virtual SearchReply::UP search(SearchRequest::Source request, SearchClient &client) override;
    virtual DocsumReply::UP getDocsums(DocsumRequest::Source request, DocsumClient &client) override;
    virtual MonitorReply::UP ping(MonitorRequest::UP request, MonitorClient &client) override;

    SyncServer(const SyncServer &);
    SyncServer &operator=(const SyncServer &);
public:
    SyncServer() {}
    virtual ~SyncServer() {}
};

SearchReply::UP
SyncServer::search(SearchRequest::Source request, SearchClient &)
{
    const SearchRequest &req = *request.get();
    SearchReply::UP reply(new SearchReply());
    SearchReply &ret = *reply;
    ret.request = request.release();
    LOG(info, "responding to search request...");
    ret.offset = req.offset;
    return reply;
}

DocsumReply::UP
SyncServer::getDocsums(DocsumRequest::Source request, DocsumClient &)
{
    DocsumReply::UP reply(new DocsumReply());
    DocsumReply &ret = *reply;
    ret.request = request.release();
    LOG(info, "responding to docsum request...");
    ret.docsums.resize(1);
    ret.docsums[0].setData("data", strlen("data"));
    ret.docsums[0].gid = DocumentId(vespalib::make_string("doc::100")).getGlobalId();
    return reply;
}

MonitorReply::UP
SyncServer::ping(MonitorRequest::UP request, MonitorClient &)
{
    MonitorRequest &req = *request;
    MonitorReply::UP reply(new MonitorReply());
    MonitorReply &ret = *reply;
    LOG(info, "responding to monitor request...");
    ret.timestamp = req.flags;
    return reply;
}

TEST("transportserver") {
    {
        SyncServer      server;
        TransportServer transport(server, server, server, 0,
                                  TransportServer::DEBUG_ALL);
        ASSERT_TRUE(transport.start());
        int port = transport.getListenPort();
        ASSERT_TRUE(port > 0);
        {
            FNET_Context ctx;
            FastOS_ThreadPool pool(128 * 1024);
            FNET_Transport client;
            ASSERT_TRUE(client.Start(&pool));

            FNET_PacketQueue adminQ;
            FNET_Connection *conn = client.Connect(make_string("tcp/localhost:%d", port).c_str(),
                    &FS4PersistentPacketStreamer::Instance, &adminQ);
            ASSERT_TRUE(conn != 0);
            {
                FS4Packet_MONITORQUERYX *mq = new FS4Packet_MONITORQUERYX();
                mq->_qflags = 30;
                mq->_features |= MQF_QFLAGS;
                conn->PostPacket(mq, FNET_NOID);
                FNET_Packet *p = adminQ.DequeuePacket(60000, &ctx);
                ASSERT_TRUE(p != 0);
                ASSERT_TRUE(p->GetPCODE() == PCODE_MONITORRESULTX);
                FS4Packet_MONITORRESULTX *r = (FS4Packet_MONITORRESULTX*)p;
                EXPECT_EQUAL(r->_timestamp, 30u);
                p->Free();
            }
            {
                FNET_PacketQueue q;
                FNET_Channel *ch = conn->OpenChannel(&q, FNET_Context());
                FS4Packet_QUERYX *qx = new FS4Packet_QUERYX();
                qx->_features |= QF_PARSEDQUERY;
                qx->_offset = 100;
                ch->Send(qx);
                FNET_Packet *p = q.DequeuePacket(60000, &ctx);
                ASSERT_TRUE(p != 0);
                ASSERT_TRUE(p->GetPCODE() == PCODE_QUERYRESULTX);
                FS4Packet_QUERYRESULTX *r = (FS4Packet_QUERYRESULTX*)p;
                EXPECT_EQUAL(r->_offset, 100u);
                p->Free();
                ch->CloseAndFree();
            }
            {
                FS4Packet_MONITORQUERYX *mq = new FS4Packet_MONITORQUERYX();
                mq->_qflags = 40;
                mq->_features |= MQF_QFLAGS;
                conn->PostPacket(mq, FNET_NOID);
                FNET_Packet *p = adminQ.DequeuePacket(60000, &ctx);
                ASSERT_TRUE(p != 0);
                ASSERT_TRUE(p->GetPCODE() == PCODE_MONITORRESULTX);
                FS4Packet_MONITORRESULTX *r = (FS4Packet_MONITORRESULTX*)p;
                EXPECT_EQUAL(r->_timestamp, 40u);
                p->Free();
            }
            {
                FNET_PacketQueue q;
                FNET_Channel *ch = conn->OpenChannel(&q, FNET_Context());
                FS4Packet_GETDOCSUMSX *qdx = new FS4Packet_GETDOCSUMSX();
                ch->Send(qdx);
                FNET_Packet *p = q.DequeuePacket(60000, &ctx);
                ASSERT_TRUE(p != 0);
                ASSERT_TRUE(p->GetPCODE() == PCODE_DOCSUM);
                FS4Packet_DOCSUM *r = (FS4Packet_DOCSUM*)p;
                EXPECT_EQUAL(r->getGid(), DocumentId("doc::100").getGlobalId());
                p->Free();
                p = q.DequeuePacket(60000, &ctx);
                ASSERT_TRUE(p != 0);
                ASSERT_TRUE(p->GetPCODE() == PCODE_EOL);
                p->Free();
                ch->CloseAndFree();
            }
            {
                FS4Packet_MONITORQUERYX *mq = new FS4Packet_MONITORQUERYX();
                mq->_qflags = 50;
                mq->_features |= MQF_QFLAGS;
                conn->PostPacket(mq, FNET_NOID);
                FNET_Packet *p = adminQ.DequeuePacket(60000, &ctx);
                ASSERT_TRUE(p != 0);
                ASSERT_TRUE(p->GetPCODE() == PCODE_MONITORRESULTX);
                FS4Packet_MONITORRESULTX *r = (FS4Packet_MONITORRESULTX*)p;
                EXPECT_EQUAL(r->_timestamp, 50u);
                p->Free();
            }
            // shut down client
            conn->CloseAdminChannel();
            client.Close(conn);
            conn->SubRef();
            client.sync();
            client.ShutDown(true);
            pool.Close();
        }

    }
}

void printError(ErrorCode ecode) {
    fprintf(stderr, "error code %u: '%s'\n", ecode, getStringFromErrorCode(ecode));
}

TEST("print errors") {
    printError(ECODE_NO_ERROR);
    printError(ECODE_GENERAL_ERROR);
    printError(ECODE_QUERY_PARSE_ERROR);
    printError(ECODE_ALL_PARTITIONS_DOWN);
    printError(ECODE_ILLEGAL_DATASET);
    printError(ECODE_OVERLOADED);
    printError(ECODE_NOT_IMPLEMENTED);
    printError(ECODE_QUERY_NOT_ALLOWED);
    printError(ECODE_TIMEOUT);
}

TEST("test SearchReply::Coverage") {
    SearchReply::Coverage c;
    EXPECT_EQUAL(0u, c.getActive());
    EXPECT_EQUAL(0u, c.getSoonActive());
    EXPECT_EQUAL(0u, c.getCovered());
    EXPECT_EQUAL(0u, c.getDegradeReason());
}

TEST("test SearchReply::Coverage(7)") {
    SearchReply::Coverage c(7);
    EXPECT_EQUAL(7u, c.getActive());
    EXPECT_EQUAL(7u, c.getSoonActive());
    EXPECT_EQUAL(7u, c.getCovered());
    EXPECT_EQUAL(0u, c.getDegradeReason());
}

TEST("test SearchReply::Coverage(7, 19)") {
    SearchReply::Coverage c(19, 7);
    EXPECT_EQUAL(19u, c.getActive());
    EXPECT_EQUAL(19u, c.getSoonActive());
    EXPECT_EQUAL(7u, c.getCovered());
    EXPECT_EQUAL(0u, c.getDegradeReason());
}

TEST("test SearchReply::Coverage set and get") {
    SearchReply::Coverage c;
    EXPECT_EQUAL(7u, c.setActive(7).getActive());
    EXPECT_EQUAL(9u, c.setSoonActive(9).getSoonActive());
    EXPECT_EQUAL(19u, c.setCovered(19).getCovered());
    EXPECT_EQUAL(5u, c.setDegradeReason(5).getDegradeReason());
    EXPECT_EQUAL(1u, SearchReply::Coverage().degradeMatchPhase().getDegradeReason());
    EXPECT_EQUAL(2u, SearchReply::Coverage().degradeTimeout().getDegradeReason());
    EXPECT_EQUAL(4u, SearchReply::Coverage().degradeAdaptiveTimeout().getDegradeReason());
    EXPECT_EQUAL(7u, SearchReply::Coverage().degradeAdaptiveTimeout().degradeTimeout().degradeMatchPhase().getDegradeReason());
}

TEST_MAIN() { TEST_RUN_ALL(); }
