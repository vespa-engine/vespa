// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("docsumapi_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchlib/engine/packetconverter.h>

using namespace search::engine;
using namespace search::fs4transport;

namespace {

// light-weight network hop simulation
template <typename T> void copyPacket(T &src, T &dst) {
    FNET_DataBuffer buf;
    src.Encode(&buf);
    dst.Decode(&buf, buf.GetDataLen());
}

} // namespace <unnamed>

class Test : public vespalib::TestApp
{
public:
    void convertToRequest();
    void convertFromReply();
    int Main() override;
};

document::GlobalId gid0("aaaaaaaaaaaa");
document::GlobalId gid1("bbbbbbbbbbbb");

void
Test::convertToRequest()
{
    const string sessionId("qrserver.0.XXXXXXXXXXXXX.0");

    FS4Packet_GETDOCSUMSX src;
    src.setTimeout(fastos::TimeStamp(4*fastos::TimeStamp::MS));
    src._features |= GDF_RANKP_QFLAGS;
    src.setRanking("seven");
    src._qflags = 5u;
    src._features |= GDF_RESCLASSNAME;
    src.setResultClassName("resclass");
    src._features |= GDF_PROPERTIES;
    src._propsVector.resize(3);
    src._propsVector[0].allocEntries(2);
    src._propsVector[0].setName("feature", strlen("feature"));
    src._propsVector[0].setKey(0, "p1k1", strlen("p1k1"));
    src._propsVector[0].setValue(0, "p1v1", strlen("p1v1"));
    src._propsVector[0].setKey(1, "p1k2", strlen("p1k2"));
    src._propsVector[0].setValue(1, "p1v2", strlen("p1v2"));
    src._propsVector[1].allocEntries(2);
    src._propsVector[1].setName("caches", strlen("caches"));
    src._propsVector[1].setKey(0, "p2k1", strlen("p2k1"));
    src._propsVector[1].setValue(0, "p2v1", strlen("p2v1"));
    src._propsVector[1].setKey(1, "p2k2", strlen("p2k2"));
    src._propsVector[1].setValue(1, "p2v2", strlen("p2v2"));
    src._propsVector[2].allocEntries(1);
    src._propsVector[2].setName("rank", strlen("rank"));
    src._propsVector[2].setKey(0, "sessionId", strlen("sessionId"));
    src._propsVector[2].setValue(0, sessionId.c_str(), sessionId.size());
    src._features |= GDF_QUERYSTACK;
    src._stackItems = 14u;
    src.setStackDump("stackdump");
    src._features |= GDF_LOCATION;
    src.setLocation("location");
    src._features |= GDF_MLD;
    src.AllocateDocIDs(2);
    src._docid[0]._gid = gid0;
    src._docid[0]._partid = 5;
    src._docid[1]._gid = gid1;
    src._docid[1]._partid = 6;

    { // full copy
        FS4Packet_GETDOCSUMSX cpy;
        copyPacket(src, cpy);

        DocsumRequest dst;
        PacketConverter::toDocsumRequest(cpy, dst);
        EXPECT_EQUAL((dst.getTimeOfDoom() - dst.getStartTime()).ms(), 4u);
        EXPECT_EQUAL(dst.ranking, "seven");
        EXPECT_EQUAL(dst.queryFlags, 5u);
        EXPECT_EQUAL(dst.resultClassName, "resclass");
        EXPECT_EQUAL(dst.propertiesMap.size(), 3u);
        EXPECT_EQUAL(dst.propertiesMap.featureOverrides().lookup("p1k1").get(), std::string("p1v1"));
        EXPECT_EQUAL(dst.propertiesMap.featureOverrides().lookup("p1k2").get(), std::string("p1v2"));
        EXPECT_EQUAL(dst.propertiesMap.cacheProperties().lookup("p2k1").get(), std::string("p2v1"));
        EXPECT_EQUAL(dst.propertiesMap.cacheProperties().lookup("p2k2").get(), std::string("p2v2"));
        EXPECT_EQUAL(dst.propertiesMap.matchProperties().lookup("p3k1").get(), std::string(""));
        EXPECT_EQUAL(std::string(&dst.stackDump[0], dst.stackDump.size()), "stackdump");
        EXPECT_EQUAL(dst.location, "location");
        EXPECT_EQUAL(dst._flags, 0u);
        EXPECT_EQUAL(dst.hits.size(), 2u);
        EXPECT_EQUAL(dst.hits[0].docid, 0u);
        EXPECT_TRUE(dst.hits[0].gid == gid0);
        EXPECT_EQUAL(dst.hits[0].path, 5u);
        EXPECT_EQUAL(dst.hits[1].docid, 0u);
        EXPECT_TRUE(dst.hits[1].gid == gid1);
        EXPECT_EQUAL(dst.hits[1].path, 6u);
        EXPECT_EQUAL(sessionId,
                     string(&dst.sessionId[0], dst.sessionId.size()));
    }
    { // without datetime
        FS4Packet_GETDOCSUMSX cpy;
        copyPacket(src, cpy);

        DocsumRequest dst;
        PacketConverter::toDocsumRequest(cpy, dst);
    }
    { // without mld
        FS4Packet_GETDOCSUMSX cpy;
        copyPacket(src, cpy);
        cpy._features &= ~GDF_MLD;

        DocsumRequest dst;
        PacketConverter::toDocsumRequest(cpy, dst);
        EXPECT_EQUAL(dst.useWideHits, false);
        EXPECT_EQUAL(dst.hits.size(), 2u);
        EXPECT_EQUAL(dst.hits[0].docid, 0u);
        EXPECT_TRUE(dst.hits[0].gid == gid0);
        EXPECT_EQUAL(dst.hits[1].docid, 0u);
        EXPECT_TRUE(dst.hits[1].gid == gid1);
    }
    { // with ignore row flag
        FS4Packet_GETDOCSUMSX tcpy;
        copyPacket(src, tcpy);
        tcpy._features |= GDF_FLAGS;
        tcpy._flags = GDFLAG_IGNORE_ROW;
        FS4Packet_GETDOCSUMSX cpy;
        copyPacket(tcpy, cpy);
        DocsumRequest dst;
        PacketConverter::toDocsumRequest(cpy, dst);
        EXPECT_EQUAL(dst._flags, static_cast<uint32_t>(GDFLAG_IGNORE_ROW));
    }
}

void
Test::convertFromReply()
{
    DocsumReply src;
    src.docsums.resize(2);
    src.docsums[0].docid = 1;
    src.docsums[0].gid = gid0;
    src.docsums[0].data.resize(2);
    src.docsums[0].data.str()[0] = 5;
    src.docsums[0].data.str()[1] = 6;
    src.docsums[1].docid = 2;
    src.docsums[1].gid = gid1;
    src.docsums[1].data.resize(3);
    src.docsums[1].data.str()[0] = 7;
    src.docsums[1].data.str()[1] = 8;
    src.docsums[1].data.str()[2] = 9;

    { // test first
        FS4Packet_DOCSUM dst;
        PacketConverter::fromDocsumReplyElement(src.docsums[0], dst);
        EXPECT_EQUAL(dst.getGid(), gid0);
        EXPECT_EQUAL(dst.getBuf().size(), 2u);
        EXPECT_EQUAL(dst.getBuf().c_str()[0], 5);
        EXPECT_EQUAL(dst.getBuf().c_str()[1], 6);
    }
    { // test second
        FS4Packet_DOCSUM dst;
        PacketConverter::fromDocsumReplyElement(src.docsums[1], dst);
        EXPECT_EQUAL(dst.getGid(), gid1);
        EXPECT_EQUAL(dst.getBuf().size(), 3u);
        EXPECT_EQUAL(dst.getBuf().c_str()[0], 7);
        EXPECT_EQUAL(dst.getBuf().c_str()[1], 8);
        EXPECT_EQUAL(dst.getBuf().c_str()[2], 9);
    }
}

int
Test::Main()
{
    TEST_INIT("docsumapi_test");
    convertToRequest();
    convertFromReply();
    TEST_DONE();
}

TEST_APPHOOK(Test);
