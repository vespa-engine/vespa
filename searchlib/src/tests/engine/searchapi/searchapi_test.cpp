// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("searchapi_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/engine/searchapi.h>
#include <vespa/searchlib/engine/packetconverter.h>

using namespace search::engine;
using namespace search::fs4transport;

namespace {

bool checkFeature(uint32_t features, uint32_t mask) {
    return ((features & mask) != 0);
}

bool checkNotFeature(uint32_t features, uint32_t mask) {
    return !checkFeature(features, mask);
}

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
    void propertyNames();
    void convertToRequest();
    void convertFromReply();
    int Main() override;
};

void
Test::propertyNames()
{
    EXPECT_EQUAL(search::MapNames::RANK, "rank");
    EXPECT_EQUAL(search::MapNames::FEATURE, "feature");
    EXPECT_EQUAL(search::MapNames::HIGHLIGHTTERMS, "highlightterms");
    EXPECT_EQUAL(search::MapNames::MATCH, "match");
    EXPECT_EQUAL(search::MapNames::CACHES, "caches");
}

void
Test::convertToRequest()
{
    FS4Packet_QUERYX src;
    src._offset = 2u;
    src._maxhits = 3u;
    src.setTimeout(fastos::TimeStamp(4*fastos::TimeStamp::MS));
    src.setQueryFlags(5u);
    src._features |= QF_RANKP;
    src.setRanking("seven");
    src._features |= QF_PROPERTIES;
    src._propsVector.resize(2);
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
    src._features |= QF_SORTSPEC;
    src.setSortSpec("sortspec");
    src._features |= QF_GROUPSPEC;
    src.setGroupSpec("groupspec");
    src._features |= QF_SESSIONID;
    src.setSessionId("sessionid");
    src._features |= QF_LOCATION;
    src.setLocation("location");
    src._features |= QF_PARSEDQUERY;
    src._numStackItems = 14u;
    src.setStackDump("stackdump");

    { // full copy
        FS4Packet_QUERYX cpy;
        copyPacket(src, cpy);

        SearchRequest dst;
        PacketConverter::toSearchRequest(cpy, dst);
        EXPECT_EQUAL(dst.offset, 2u);
        EXPECT_EQUAL(dst.maxhits, 3u);
        EXPECT_EQUAL((dst.getTimeOfDoom() - dst.getStartTime()).ms(), 4u);
        EXPECT_EQUAL(dst.queryFlags, 1u);  //Filtered
        EXPECT_EQUAL(vespalib::string("seven"), dst.ranking);
        EXPECT_EQUAL(dst.propertiesMap.size(), 2u);
        EXPECT_EQUAL(dst.propertiesMap.featureOverrides().lookup("p1k1").get(), std::string("p1v1"));
        EXPECT_EQUAL(dst.propertiesMap.featureOverrides().lookup("p1k2").get(), std::string("p1v2"));
        EXPECT_EQUAL(dst.propertiesMap.cacheProperties().lookup("p2k1").get(), std::string("p2v1"));
        EXPECT_EQUAL(dst.propertiesMap.cacheProperties().lookup("p2k2").get(), std::string("p2v2"));
        EXPECT_EQUAL(dst.propertiesMap.matchProperties().lookup("p3k1").get(), std::string(""));
        EXPECT_EQUAL(dst.sortSpec, "sortspec");
        EXPECT_EQUAL(std::string(&dst.groupSpec[0], dst.groupSpec.size()), "groupspec");
        EXPECT_EQUAL(std::string(&dst.sessionId[0], dst.sessionId.size()), "sessionid");
        EXPECT_EQUAL(dst.location, "location");
        EXPECT_EQUAL(dst.stackItems, 14u);
        EXPECT_EQUAL(std::string(&dst.stackDump[0], dst.stackDump.size()), "stackdump");
    }
    { // without datetime
        FS4Packet_QUERYX cpy;
        copyPacket(src, cpy);

        SearchRequest dst;
        PacketConverter::toSearchRequest(cpy, dst);
    }
}

void
Test::convertFromReply()
{
    SearchReply src;
    src.offset = 1u;
    src.totalHitCount = 2u;
    src.maxRank = 3;
    src.setDistributionKey(4u);
    src.sortIndex.push_back(0);
    src.sortIndex.push_back(1);
    src.sortIndex.push_back(2);
    src.sortData.push_back(11);
    src.sortData.push_back(22);
    src.groupResult.push_back(2);
    src.coverage = SearchReply::Coverage(5, 3);
    src.useWideHits = true;
    src.hits.resize(2);
    document::GlobalId gid0("aaaaaaaaaaaa");
    document::GlobalId gid1("bbbbbbbbbbbb");
    src.hits[0].gid = gid0;
    src.hits[0].metric = 5;
    src.hits[0].path = 11;
    src.hits[0].setDistributionKey(100);
    src.hits[1].gid = gid1;
    src.hits[1].metric = 4;
    src.hits[1].path = 10;
    src.hits[1].setDistributionKey(105);

    { // full copy
        SearchReply cpy = src;

        FS4Packet_QUERYRESULTX dst0;
        PacketConverter::fromSearchReply(cpy, dst0);
        FS4Packet_QUERYRESULTX dst;
        copyPacket(dst0, dst);
        EXPECT_EQUAL(dst._offset, 1u);
        EXPECT_EQUAL(dst._numDocs, 2u);
        EXPECT_EQUAL(dst._totNumDocs, 2u);
        EXPECT_EQUAL(dst._maxRank, 3);
        EXPECT_EQUAL(4u, dst.getDistributionKey());
        EXPECT_TRUE(checkFeature(dst._features, QRF_SORTDATA));
        EXPECT_EQUAL(dst._sortIndex[0], 0u);
        EXPECT_EQUAL(dst._sortIndex[1], 1u);
        EXPECT_EQUAL(dst._sortIndex[2], 2u);
        EXPECT_EQUAL(dst._sortData[0], 11);
        EXPECT_EQUAL(dst._sortData[1], 22);
        EXPECT_TRUE(checkFeature(dst._features, QRF_GROUPDATA));
        EXPECT_EQUAL(dst._groupDataLen, 1u);
        EXPECT_EQUAL(dst._groupData[0], 2);
        EXPECT_TRUE(checkFeature(dst._features, QRF_COVERAGE));
        EXPECT_EQUAL(dst._coverageDocs, 3u);
        EXPECT_EQUAL(dst._activeDocs, 5u);
        EXPECT_TRUE(checkFeature(dst._features, QRF_MLD));
        EXPECT_TRUE(dst._hits[0]._gid == gid0);
        EXPECT_EQUAL(dst._hits[0]._metric, 5);
        EXPECT_EQUAL(dst._hits[0]._partid, 11u);
        EXPECT_EQUAL(dst._hits[0].getDistributionKey(), 100u);
        EXPECT_TRUE(dst._hits[1]._gid == gid1);
        EXPECT_EQUAL(dst._hits[1]._metric, 4);
        EXPECT_EQUAL(dst._hits[1]._partid, 10u);
        EXPECT_EQUAL(dst._hits[1].getDistributionKey(), 105u);
    }
    { // not sortdata
        SearchReply cpy = src;
        cpy.sortIndex.clear();
        cpy.sortData.clear();

        FS4Packet_QUERYRESULTX dst0;
        PacketConverter::fromSearchReply(cpy, dst0);
        FS4Packet_QUERYRESULTX dst;
        copyPacket(dst0, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, QRF_SORTDATA));
    }
    { // not groupdata
        SearchReply cpy = src;
        cpy.groupResult.clear();

        FS4Packet_QUERYRESULTX dst0;
        PacketConverter::fromSearchReply(cpy, dst0);
        FS4Packet_QUERYRESULTX dst;
        copyPacket(dst0, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, QRF_GROUPDATA));
    }
    { // non-full coverage
        SearchReply cpy = src;

        FS4Packet_QUERYRESULTX dst0;
        PacketConverter::fromSearchReply(cpy, dst0);
        FS4Packet_QUERYRESULTX dst;
        copyPacket(dst0, dst);
        EXPECT_TRUE(checkFeature(dst._features, QRF_COVERAGE));
        EXPECT_EQUAL(dst._coverageDocs, 3u);
        EXPECT_EQUAL(dst._activeDocs, 5u);
    }
    { // non-mld
        SearchReply cpy = src;
        cpy.useWideHits = false;

        FS4Packet_QUERYRESULTX dst0;
        PacketConverter::fromSearchReply(cpy, dst0);
        FS4Packet_QUERYRESULTX dst;
        copyPacket(dst0, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, QRF_MLD));
        EXPECT_TRUE(dst._hits[0]._gid == gid0);
        EXPECT_EQUAL(dst._hits[0]._metric, 5);
        EXPECT_TRUE(dst._hits[1]._gid == gid1);
        EXPECT_EQUAL(dst._hits[1]._metric, 4);
    }
    { // non-mld not siteid
        SearchReply cpy = src;
        cpy.useWideHits = false;

        FS4Packet_QUERYRESULTX dst0;
        PacketConverter::fromSearchReply(cpy, dst0);
        FS4Packet_QUERYRESULTX dst;
        copyPacket(dst0, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, QRF_MLD));
        EXPECT_TRUE(dst._hits[0]._gid == gid0);
        EXPECT_EQUAL(dst._hits[0]._metric, 5);
        EXPECT_TRUE(dst._hits[1]._gid == gid1);
        EXPECT_EQUAL(dst._hits[1]._metric, 4);
    }
}

int
Test::Main()
{
    TEST_INIT("searchapi_test");
    propertyNames();
    convertToRequest();
    convertFromReply();
    TEST_DONE();
}

TEST_APPHOOK(Test);
