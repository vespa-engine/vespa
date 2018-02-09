// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/mapnames.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/controlpacket.h>

using namespace search::fs4transport;
using vespalib::compression::CompressionConfig;

#define PCODE_BEGIN PCODE_EOL
#define PCODE_END   PCODE_LastCode

class MyPersistentPacketStreamer : public FS4PersistentPacketStreamer {
public:
    MyPersistentPacketStreamer() :
        FS4PersistentPacketStreamer(FS4PacketFactory::CreateFS4Packet) {
        // empty
    }

    uint32_t getChannelId(uint32_t pcode, uint32_t chid) {
        return HasChannelID(pcode) ? chid : -1u;
    }
};

FNET_Packet *
testEncodeDecode(FS4PersistentPacketStreamer &streamer, FNET_Packet &packet)
{
    FNET_Context ctx;
    FNET_DataBuffer buf;
    buf.WriteInt32(0xdeadbeef);  // buffers can have extra data at the front.
    streamer.Encode(&packet, 1u, &buf);
    buf.DataToDead(sizeof(uint32_t));

    FNET_DataBuffer lhs;
    lhs.WriteBytes(buf.GetData(), buf.GetDataLen());

    buf.WriteInt32(0xdeadbeef);  // buffers can have extra data at the end.

    bool broken;
    uint32_t plen, pcode, chid;
    MyPersistentPacketStreamer myStreamer;
    EXPECT_TRUE(streamer.GetPacketInfo(&buf, &plen, &pcode, &chid, &broken));
    if ((pcode & ~PCODE_MASK) == 0) {
        EXPECT_EQUAL(packet.GetLength(), plen);
    }
    EXPECT_EQUAL(packet.GetPCODE() & PCODE_MASK, pcode & PCODE_MASK);
    EXPECT_EQUAL(myStreamer.getChannelId(pcode, 1u), chid);

    FNET_Packet *ret = streamer.Decode(&buf, plen, pcode, ctx);
    assert(ret);
    if (ret->GetPCODE() == (pcode & PCODE_MASK)) {
        FNET_DataBuffer rhs;
        streamer.Encode(ret, 1u, &rhs);
        if (!EXPECT_TRUE(lhs.Equals(&rhs))) {
            lhs.HexDump();
            rhs.HexDump();
        }
    } else {
        // Packet was transcoded.
    }
    return ret;
}

FNET_Packet *
testEncodeDecode(FNET_Packet &packet)
{
    return testEncodeDecode(FS4PersistentPacketStreamer::Instance, packet);
}

void fillProperties(FS4Properties &props, const std::string &name,
                    uint32_t len) {
    props.setName(name);
    props.allocEntries(len);
    for (uint32_t i = 0; i < len; ++i) {
        std::string key = vespalib::make_string("key%d", i);
        props.setKey(i, key);

        std::string val = vespalib::make_string("val%d", i);
        props.setValue(i, val);
    }
}

void testProperties(FS4Properties &props, const std::string &name,
                    uint32_t len) {
    EXPECT_EQUAL(name, props.getName());
    EXPECT_EQUAL(name.size(), props.getNameLen());
    for (uint32_t i = 0; i < len; ++i) {
        std::string key = vespalib::make_string("key%d", i);
        EXPECT_EQUAL(key, std::string(props.getKey(i), props.getKeyLen(i)));

        std::string val = vespalib::make_string("val%d", i);
        EXPECT_EQUAL(val,
                     std::string(props.getValue(i), props.getValueLen(i)));
    }
}


// ----------------------------------------------------------------------------
//
// Tests
//
// ----------------------------------------------------------------------------

document::GlobalId gid0("aaaaaaaaaaaa");
document::GlobalId gid1("bbbbbbbbbbbb");

TEST("testPacketArray") {
    PacketArray arr;
    for (uint32_t i = 0; i < 32; ++i) {
        EXPECT_EQUAL(i, arr.Length());
        arr.Add(new FNET_ControlPacket(i));
        EXPECT_EQUAL(i, static_cast<FNET_ControlPacket&>(*arr.Array()[i]).GetCommand());
    }
    for (uint32_t i = 0; i < arr.Length(); ++i) {
        delete static_cast<FNET_ControlPacket *>(arr.Array()[i]);
    }
}

TEST("testPacketFactory") {
    ASSERT_TRUE(FS4PacketFactory::CreateFS4Packet(PCODE_BEGIN - 1) == NULL);

    ASSERT_TRUE(FS4PacketFactory::CreateFS4Packet(PCODE_END) == NULL);

    for (uint32_t pcode = PCODE_BEGIN; pcode < PCODE_END; ++pcode) {
        if ((pcode != PCODE_MLD_QUERYRESULT2_NOTUSED) &&
            (pcode != PCODE_QUERY_NOTUSED) &&
            (pcode != PCODE_MONITORQUERY_NOTUSED) &&
            (pcode != PCODE_GETDOCSUMS_NOTUSED) &&
            (pcode != PCODE_MLD_GETDOCSUMS_NOTUSED) &&
            (pcode != PCODE_QUERYRESULT_NOTUSED) &&
            (pcode != PCODE_MLD_QUERYRESULT_NOTUSED) &&
            (pcode != PCODE_MONITORRESULT_NOTUSED) &&
            (pcode != PCODE_MLD_MONITORRESULT_NOTUSED) &&
            (pcode != PCODE_CLEARCACHES_NOTUSED) &&
            (pcode != PCODE_PARSEDQUERY2_NOTUSED) &&
            (pcode != PCODE_QUEUELEN_NOTUSED) &&
            (pcode != PCODE_QUERY2_NOTUSED) &&
            (pcode != PCODE_MLD_GETDOCSUMS2_NOTUSED))
        {
            std::unique_ptr<FNET_Packet> aptr(FS4PacketFactory::CreateFS4Packet(pcode));
            ASSERT_TRUE(aptr.get() != NULL);
            EXPECT_EQUAL(pcode, aptr->GetPCODE());
        }
    }
}

TEST("testPersistentPacketStreamer") {
    for (uint32_t pcode = PCODE_BEGIN; pcode < PCODE_END; ++pcode) {
        if ((pcode == PCODE_QUERYX) ||
            (pcode != PCODE_MLD_QUERYRESULT2_NOTUSED) ||
             (pcode != PCODE_MLD_GETDOCSUMS2_NOTUSED))
        {
            continue;
        }
        std::unique_ptr<FNET_Packet> arg(FS4PacketFactory::CreateFS4Packet(pcode));
        std::unique_ptr<FNET_Packet> ret(testEncodeDecode(FS4PersistentPacketStreamer::Instance, *arg));
        EXPECT_TRUE(ret.get() != NULL);

        FNET_Packet *raw = testEncodeDecode(FS4PersistentPacketStreamer::Instance,
                                            *FS4PacketFactory::CreateFS4Packet(pcode));
        EXPECT_TRUE(raw != NULL);
    }
}

TEST("testProperties") {
    FS4Properties src;
    fillProperties(src, "foo", 32u);
    testProperties(src, "foo", 32u);

    FNET_DataBuffer buf;
    src.encode(buf);
    FNET_DataBuffer lhs;
    lhs.WriteBytes(buf.GetData(), buf.GetDataLen());

    uint32_t len = buf.GetDataLen();
    FS4Properties dst;
    dst.decode(buf, len);
    EXPECT_EQUAL(src.getLength(), dst.getLength());

    testProperties(dst, "foo", 32u);

    FNET_DataBuffer rhs;
    dst.encode(rhs);
    EXPECT_TRUE(lhs.Equals(&rhs));
}

TEST("testEol") {
    FS4Packet_EOL *src = dynamic_cast<FS4Packet_EOL*>(FS4PacketFactory::CreateFS4Packet(PCODE_EOL));
    ASSERT_TRUE(src != NULL);

    std::vector<FNET_Packet*> lst { src, testEncodeDecode(*src) };

    for (FNET_Packet * packet : lst) {
        FS4Packet_EOL *ptr = dynamic_cast<FS4Packet_EOL*>(packet);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_EOL, ptr->GetPCODE());
        EXPECT_EQUAL(0u, ptr->GetLength());

        delete ptr;
    }
}

TEST("testError") {
    FS4Packet_ERROR *src = dynamic_cast<FS4Packet_ERROR*>(FS4PacketFactory::CreateFS4Packet(PCODE_ERROR));
    ASSERT_TRUE(src != NULL);
    src->_errorCode = 1u;
    src->setErrorMessage("foo");

    std::vector<FNET_Packet*> lst { src, testEncodeDecode(*src) };

    for (FNET_Packet * packet : lst) {
        FS4Packet_ERROR *ptr = dynamic_cast<FS4Packet_ERROR*>(packet);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_ERROR, ptr->GetPCODE());
        EXPECT_EQUAL(11u, ptr->GetLength());
        EXPECT_EQUAL(1u, ptr->_errorCode);
        EXPECT_EQUAL("foo", ptr->_message);

        delete ptr;
    }
}

TEST("testDocsum") {
    FS4Packet_DOCSUM *src = dynamic_cast<FS4Packet_DOCSUM*>(FS4PacketFactory::CreateFS4Packet(PCODE_DOCSUM));
    ASSERT_TRUE(src != NULL);
    src->setGid(gid0);
    src->SetBuf("foo", 3u);

    std::vector<FNET_Packet*> lst { src, testEncodeDecode(*src) };

    for (FNET_Packet * packet : lst) {
        FS4Packet_DOCSUM *ptr = dynamic_cast<FS4Packet_DOCSUM*>(packet);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_DOCSUM, ptr->GetPCODE());
        EXPECT_EQUAL(3u + 12u, ptr->GetLength());
        EXPECT_EQUAL(gid0, ptr->getGid());
        EXPECT_EQUAL("foo", std::string(ptr->getBuf().c_str(), ptr->getBuf().size()));

        delete ptr;
    }
}

TEST("testMonitorQueryX") {
    FS4Packet_MONITORQUERYX *src = dynamic_cast<FS4Packet_MONITORQUERYX*>(FS4PacketFactory::CreateFS4Packet(PCODE_MONITORQUERYX));
    ASSERT_TRUE(src != NULL);
    src->_qflags = 1u;

    std::vector<FNET_Packet*> lst;
    for (uint32_t i = MQF_QFLAGS, len = (uint32_t)(MQF_QFLAGS << 1); i < len; ++i) {
        if (i & ~FNET_MQF_SUPPORTED_MASK) {
            continue; // not supported;
        }
        src->_features = i;
        lst.push_back(testEncodeDecode(*src));
    }
    src->_features = (uint32_t)-1;
    lst.push_back(src);

    for (FNET_Packet * packet : lst) {
        FS4Packet_MONITORQUERYX *ptr = dynamic_cast<FS4Packet_MONITORQUERYX*>(packet);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_MONITORQUERYX, ptr->GetPCODE());
        EXPECT_EQUAL(ptr->_features & MQF_QFLAGS ? 1u : 0u, ptr->_qflags);

        delete ptr;
    }
}

TEST("testMonitorResultX") {
    FS4Packet_MONITORRESULTX *src = dynamic_cast<FS4Packet_MONITORRESULTX*>(FS4PacketFactory::CreateFS4Packet(PCODE_MONITORRESULTX));
    ASSERT_TRUE(src != NULL);
    src->_partid = 1u;
    src->_timestamp = 2u;
    src->_totalNodes = 3u;
    src->_activeNodes = 4u;
    src->_totalParts = 5u;
    src->_activeParts = 6u;
    src->_rflags = 7u;

    std::vector<FNET_Packet*> lst;
    for (uint32_t i = MRF_MLD, len = (uint32_t)(MRF_RFLAGS << 1); i < len; ++i) {
        if (i & ~FNET_MRF_SUPPORTED_MASK) {
            continue; // not supported;
        }
        src->_features = i;
        lst.push_back(testEncodeDecode(*src));
    }
    src->_features = (uint32_t)-1;
    lst.push_back(src);

    for (FNET_Packet * packet : lst) {
        FS4Packet_MONITORRESULTX *ptr = dynamic_cast<FS4Packet_MONITORRESULTX*>(packet);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_MONITORRESULTX, ptr->GetPCODE());
        EXPECT_EQUAL(1u, ptr->_partid);
        EXPECT_EQUAL(2u, ptr->_timestamp);
        EXPECT_EQUAL(ptr->_features & MRF_MLD ? 3u : 0u, ptr->_totalNodes);
        EXPECT_EQUAL(ptr->_features & MRF_MLD ? 4u : 0u, ptr->_activeNodes);
        EXPECT_EQUAL(ptr->_features & MRF_MLD ? 5u : 0u, ptr->_totalParts);
        EXPECT_EQUAL(ptr->_features & MRF_MLD ? 6u : 0u, ptr->_activeParts);
        EXPECT_EQUAL(ptr->_features & MRF_RFLAGS ? 7u : 0u, ptr->_rflags);

        delete ptr;
    }
}

TEST("testQueryResultX") {
    FS4Packet_QUERYRESULTX *src = dynamic_cast<FS4Packet_QUERYRESULTX*>(FS4PacketFactory::CreateFS4Packet(PCODE_QUERYRESULTX));
    ASSERT_TRUE(src != NULL);
    src->_offset = 1u;
    src->_totNumDocs = 2u;
    src->_maxRank = (search::HitRank)3;
    src->setDistributionKey(4u);
    src->_coverageDocs = 6u;
    src->_activeDocs = 7u;
    src->_soonActiveDocs = 8;
    src->_coverageDegradeReason = 0x17;
    src->setNodesQueried(12);
    src->setNodesReplied(11);
    uint32_t sortIndex[3] = { 0u, 1u, 3u /* size of data */}; // numDocs + 1
    src->SetSortDataRef(2, sortIndex, "foo");
    src->SetGroupDataRef("baz", 3u);
    src->AllocateHits(2);
    src->_hits[0]._gid = gid0;
    src->_hits[0]._metric = (search::HitRank)2;
    src->_hits[0]._partid = 3u;
    src->_hits[0].setDistributionKey(4u);
    src->_hits[1]._gid = gid1;
    src->_hits[1]._metric = (search::HitRank)3;
    src->_hits[1]._partid = 4u;
    src->_hits[1].setDistributionKey(5u);

    std::vector<FNET_Packet*> lst;
    for (uint32_t i = QRF_MLD, len = (uint32_t)(QRF_GROUPDATA << 1); i < len; ++i) {
        if (i & ~FNET_QRF_SUPPORTED_MASK) {
            continue; // not supported;
        }
        src->_features = i;
        lst.push_back(testEncodeDecode(*src));
    }
    src->_features = (uint32_t)-1;
    lst.push_back(src);

    for (FNET_Packet * packet : lst) {
        FS4Packet_QUERYRESULTX *ptr = dynamic_cast<FS4Packet_QUERYRESULTX*>(packet);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_QUERYRESULTX, ptr->GetPCODE());

        EXPECT_EQUAL(1u, ptr->_offset);
        EXPECT_EQUAL(2u, ptr->_totNumDocs);
        EXPECT_EQUAL((search::HitRank)3, ptr->_maxRank);
        EXPECT_EQUAL(4u, ptr->getDistributionKey());
        EXPECT_EQUAL(ptr->_features & QRF_COVERAGE_NODES ? 12 : 1u, ptr->getNodesQueried());
        EXPECT_EQUAL(ptr->_features & QRF_COVERAGE_NODES ? 11 : 1u, ptr->getNodesReplied());
        EXPECT_EQUAL(6u, ptr->_coverageDocs);
        EXPECT_EQUAL(7u, ptr->_activeDocs);
        EXPECT_EQUAL(8u, ptr->_soonActiveDocs);
        EXPECT_EQUAL(0x17u, ptr->_coverageDegradeReason);
        if (ptr->_features & QRF_SORTDATA) {
            EXPECT_EQUAL(0u, ptr->_sortIndex[0]);
            EXPECT_EQUAL(1u, ptr->_sortIndex[1]);
            EXPECT_EQUAL(3u, ptr->_sortIndex[2]);
            EXPECT_EQUAL("foo", std::string(ptr->_sortData, ptr->_sortIndex[2]));
        } else {
            EXPECT_EQUAL((void*)NULL, ptr->_sortIndex);
            EXPECT_EQUAL((void*)NULL, ptr->_sortData);
        }
        if (ptr->_features & QRF_GROUPDATA) {
            EXPECT_EQUAL("baz", std::string(ptr->_groupData, ptr->_groupDataLen));
        } else {
            EXPECT_EQUAL(0u, ptr->_groupDataLen);
            EXPECT_EQUAL((void*)NULL, ptr->_groupData);
        }
        EXPECT_EQUAL(2u, ptr->_numDocs);
        for (uint32_t i = 0; i < ptr->_numDocs; ++i) {
            EXPECT_EQUAL(i == 0 ? gid0 : gid1, ptr->_hits[i]._gid);
            EXPECT_EQUAL((search::HitRank)2 + i, ptr->_hits[i]._metric);
            EXPECT_EQUAL(ptr->_features & QRF_MLD ? 3u + i : 0u, ptr->_hits[i]._partid);
            EXPECT_EQUAL(ptr->_features & QRF_MLD ? 4u + i : ptr->getDistributionKey(), ptr->_hits[i].getDistributionKey());
        }

        delete ptr;
    }
}

FS4Packet_QUERYX *
createAndFill_QUERYX()
{
    FS4Packet_QUERYX *src = dynamic_cast<FS4Packet_QUERYX*>(FS4PacketFactory::CreateFS4Packet(PCODE_QUERYX));
    ASSERT_TRUE(src != NULL);
    src->_offset = 2u;
    src->_maxhits = 3u;
    src->setTimeout(fastos::TimeStamp(4*fastos::TimeStamp::MS));
    EXPECT_EQUAL(fastos::TimeStamp(4*fastos::TimeStamp::MS), src->getTimeout());
    src->setTimeout(fastos::TimeStamp(-4*fastos::TimeStamp::MS));
    EXPECT_EQUAL(0l, src->getTimeout());
    src->setTimeout(fastos::TimeStamp(4*fastos::TimeStamp::MS));
    EXPECT_EQUAL(fastos::TimeStamp(4*fastos::TimeStamp::MS), src->getTimeout());
    src->_qflags = 5u;
    src->setRanking("seven");
    src->_numStackItems = 14u;
    src->_propsVector.resize(2);
    fillProperties(src->_propsVector[0], "foo", 8);
    fillProperties(src->_propsVector[1], "bar", 16);
    src->setSortSpec("sortspec");
    src->setGroupSpec("groupspec");
    src->setLocation("location");
    src->setStackDump("stackdump");
    return src;
}

void
verifyQueryX(FS4Packet_QUERYX & queryX, uint32_t features)
{
    EXPECT_EQUAL((uint32_t)PCODE_QUERYX, queryX.GetPCODE());
    EXPECT_EQUAL(features, queryX._features);
    EXPECT_EQUAL(2u, queryX._offset);
    EXPECT_EQUAL(3u, queryX._maxhits);
    EXPECT_EQUAL(fastos::TimeStamp(4*fastos::TimeStamp::MS), queryX.getTimeout());
    EXPECT_EQUAL(0x5u, queryX._qflags);
    if (queryX._features & QF_RANKP) {
        EXPECT_EQUAL("seven", queryX._ranking);
    } else {
        EXPECT_EQUAL("", queryX._ranking);
    }
    EXPECT_EQUAL(queryX._features & QF_PARSEDQUERY ? 14u : 0u, queryX._numStackItems);
    if (queryX._features & QF_PROPERTIES) {
        EXPECT_EQUAL(2u, queryX._propsVector.size());
        testProperties(queryX._propsVector[0], "foo", 8);
        testProperties(queryX._propsVector[1], "bar", 16);
    } else {
        EXPECT_EQUAL(0u, queryX._propsVector.size());
    }
    if (queryX._features & QF_SORTSPEC) {
        EXPECT_EQUAL("sortspec", queryX._sortSpec);
    } else {
        EXPECT_EQUAL(0u, queryX._sortSpec.size());
    }
    if (queryX._features & QF_GROUPSPEC) {
        EXPECT_EQUAL("groupspec", queryX._groupSpec);
    } else {
        EXPECT_EQUAL(0u, queryX._groupSpec.size());
    }
    if (queryX._features & QF_LOCATION) {
        EXPECT_EQUAL("location", queryX._location);
    } else {
        EXPECT_EQUAL(0u, queryX._location.size());
    }
    if (queryX._features & QF_PARSEDQUERY) {
        EXPECT_EQUAL("stackdump", queryX._stackDump);
    } else {
        EXPECT_EQUAL(0u, queryX._stackDump.size());
    }
}

TEST("testQueryX") {
    FS4Packet_QUERYX *src = createAndFill_QUERYX();
    std::vector<std::pair<FNET_Packet*, uint32_t>> lst;
    for (uint32_t i = QF_PARSEDQUERY, len = (uint32_t)(QF_GROUPSPEC << 1), skip = 0; i < len; ++i) {
        if (!(i & QF_PARSEDQUERY)) {
            continue; // skip most
        }
        if (i & ~FNET_QF_SUPPORTED_MASK) {
            continue; // not supported
        }
        if (++skip % 10) {
            continue; // skip most
        }
        src->_features = i;
        lst.emplace_back(testEncodeDecode(*src), i);
    }
    src->_features = uint32_t(-1);
    lst.emplace_back(src, -1);

    for (const auto & pfPair : lst) {
        FS4Packet_QUERYX *ptr = dynamic_cast<FS4Packet_QUERYX*>(pfPair.first);
        ASSERT_TRUE(ptr != NULL);
        verifyQueryX(*ptr, pfPair.second);

        delete ptr;
    }
}

TEST("testSharedPacket") {
    FNET_Packet::SP src(createAndFill_QUERYX());
    static_cast<FS4Packet_QUERYX *>(src.get())->_features=FNET_QF_SUPPORTED_MASK;
    FNET_Packet::SP decoded(testEncodeDecode(*src));
    verifyQueryX(*static_cast<FS4Packet_QUERYX *>(decoded.get()), FNET_QF_SUPPORTED_MASK);
    EXPECT_TRUE(decoded.get() != nullptr);
    FS4Packet_Shared shared(decoded);
    FNET_Packet::UP decoded2(testEncodeDecode(shared));
    EXPECT_TRUE(decoded2.get() != nullptr);
    EXPECT_TRUE(nullptr == dynamic_cast<const FS4Packet_Shared *>(decoded2.get()));
    EXPECT_TRUE(nullptr != dynamic_cast<const FS4Packet_QUERYX *>(decoded2.get()));
    EXPECT_EQUAL(src->GetLength(), decoded2->GetLength());
    verifyQueryX(*static_cast<FS4Packet_QUERYX *>(decoded2.get()), FNET_QF_SUPPORTED_MASK);
}

TEST("test pre serializing packets no compression") {
    FNET_Packet::UP src(createAndFill_QUERYX());
    FS4Packet_QUERYX * queryX = static_cast<FS4Packet_QUERYX *>(src.get());
    queryX->_features=FNET_QF_SUPPORTED_MASK;
    FNET_Packet::UP decoded(testEncodeDecode(*src));
    verifyQueryX(*static_cast<FS4Packet_QUERYX *>(decoded.get()), FNET_QF_SUPPORTED_MASK);
    EXPECT_EQUAL(500u, src->GetLength());
    EXPECT_EQUAL(src->GetLength(), decoded->GetLength());
    FS4Packet_PreSerialized serialized(*src);
    EXPECT_EQUAL(218u, serialized.GetPCODE());
    EXPECT_EQUAL(500u, serialized.GetLength());
    FNET_Packet::UP decoded2(testEncodeDecode(serialized));
    EXPECT_EQUAL(500u, decoded2->GetLength());
    verifyQueryX(*static_cast<FS4Packet_QUERYX *>(decoded2.get()), FNET_QF_SUPPORTED_MASK);
}

TEST("test pre serializing packets with compression") {
    FNET_Packet::UP src(createAndFill_QUERYX());
    FS4Packet_QUERYX * queryX = static_cast<FS4Packet_QUERYX *>(src.get());
    queryX->_features=FNET_QF_SUPPORTED_MASK;
    FNET_Packet::UP decoded(testEncodeDecode(*src));
    verifyQueryX(*static_cast<FS4Packet_QUERYX *>(decoded.get()), FNET_QF_SUPPORTED_MASK);
    EXPECT_EQUAL(500u, src->GetLength());
    EXPECT_EQUAL(src->GetLength(), decoded->GetLength());
    FS4PersistentPacketStreamer::Instance.SetCompressionLimit(100);
    FS4Packet_PreSerialized serialized(*src);
    EXPECT_EQUAL(218u | (CompressionConfig::LZ4 << 24), serialized.GetPCODE());
    EXPECT_GREATER_EQUAL(321u, serialized.GetLength());
    FNET_Packet::UP decoded2(testEncodeDecode(serialized));
    EXPECT_EQUAL(500u, decoded2->GetLength());
    verifyQueryX(*static_cast<FS4Packet_QUERYX *>(decoded2.get()), FNET_QF_SUPPORTED_MASK);
}
    

TEST("testGetDocsumsX") {
    FS4Packet_GETDOCSUMSX *src = dynamic_cast<FS4Packet_GETDOCSUMSX*>(FS4PacketFactory::CreateFS4Packet(PCODE_GETDOCSUMSX));
    ASSERT_TRUE(src != NULL);
    src->setTimeout(fastos::TimeStamp(2*fastos::TimeStamp::MS));
    src->setRanking("four");
    src->_qflags = 5u;
    src->_stackItems = 7u;
    src->_propsVector.resize(2);
    fillProperties(src->_propsVector[0], "foo", 8);
    fillProperties(src->_propsVector[1], "bar", 16);
    src->setResultClassName("resultclassname");
    src->setStackDump("stackdump");
    src->setLocation("location");
    src->_flags = GDFLAG_IGNORE_ROW;
    src->AllocateDocIDs(2);
    src->_docid[0]._gid = gid0;
    src->_docid[0]._partid = 2u;
    src->_docid[1]._gid = gid1;
    src->_docid[1]._partid = 3u;

    std::vector<std::pair<FNET_Packet*, uint32_t>> lst;
    for (uint32_t i = GDF_MLD, len = (uint32_t)(GDF_FLAGS << 1); i < len; ++i) {
        if (i & ~FNET_GDF_SUPPORTED_MASK) {
            continue; // not supported
        }
        src->_features = i;
        lst.emplace_back(testEncodeDecode(*src), i);
    }
    src->_features = uint32_t(-1);
    lst.emplace_back(src, uint32_t(-1));

    for (const auto & pfPair : lst) {
        FS4Packet_GETDOCSUMSX *ptr = dynamic_cast<FS4Packet_GETDOCSUMSX*>(pfPair.first);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_GETDOCSUMSX, ptr->GetPCODE());
        EXPECT_EQUAL(pfPair.second, ptr->_features);
        EXPECT_EQUAL(fastos::TimeStamp(2*fastos::TimeStamp::MS), ptr->getTimeout());
        if (ptr->_features & GDF_RANKP_QFLAGS) {
            EXPECT_EQUAL("four", ptr->_ranking);
        } else {
            EXPECT_EQUAL("", ptr->_ranking);
        }
        EXPECT_EQUAL(ptr->_features & GDF_RANKP_QFLAGS ? 5u : 0u, ptr->_qflags);
        EXPECT_EQUAL(ptr->_features & GDF_QUERYSTACK ? 7u : 0u, ptr->_stackItems);
        if (ptr->_features & GDF_PROPERTIES) {
            EXPECT_EQUAL(2u, ptr->_propsVector.size());
            testProperties(ptr->_propsVector[0], "foo", 8);
            testProperties(ptr->_propsVector[1], "bar", 16);
        } else {
            EXPECT_EQUAL(0u, ptr->_propsVector.size());
        }
        if (ptr->_features & GDF_RESCLASSNAME) {
            EXPECT_EQUAL("resultclassname", ptr->_resultClassName);
        } else {
            EXPECT_EQUAL(0u, ptr->_resultClassName.size());
        }
        if (ptr->_features & GDF_QUERYSTACK) {
            EXPECT_EQUAL("stackdump", ptr->_stackDump);
        } else {
            EXPECT_EQUAL(0u, ptr->_stackDump.size());
        }
        if (ptr->_features & GDF_LOCATION) {
            EXPECT_EQUAL("location", ptr->_location);
        } else {
            EXPECT_EQUAL(0u, ptr->_location.size());
        }
        if (ptr->_features & GDF_FLAGS) {
            EXPECT_EQUAL(static_cast<uint32_t>(GDFLAG_IGNORE_ROW),
                         ptr->_flags);
        } else {
            EXPECT_EQUAL(0u, ptr->_flags);
        }
        EXPECT_EQUAL(2u, ptr->_docid.size());
        for (uint32_t i = 0; i < ptr->_docid.size(); ++i) {
            EXPECT_EQUAL(i == 0u ? gid0 : gid1, ptr->_docid[i]._gid);
            EXPECT_EQUAL(ptr->_features & GDF_MLD ? 2u + i : 0u, ptr->_docid[i]._partid);
        }

        delete ptr;
    }
}

TEST("require that FS4PersistentPacketStreamer can compress packets") {
    FS4Packet_ERROR *packet = static_cast<FS4Packet_ERROR*>(FS4PacketFactory::CreateFS4Packet(PCODE_ERROR));
    packet->_errorCode = 1u;
    packet->setErrorMessage(string(1000, 'a'));

    FS4PersistentPacketStreamer streamer(FS4PacketFactory::CreateFS4Packet);

    FNET_DataBuffer buf1;
    streamer.Encode(packet, 1u, &buf1);
    EXPECT_EQUAL(1020u, buf1.GetDataLen());

    streamer.SetCompressionLimit(100);
    FNET_DataBuffer buf2;
    streamer.Encode(packet, 1u, &buf2);
    EXPECT_EQUAL(38u, buf2.GetDataLen());

    std::vector<FNET_Packet*> lst{ packet, testEncodeDecode(streamer, *packet) };

    for (FNET_Packet * fnetPacket : lst) {
        FS4Packet_ERROR *ptr = dynamic_cast<FS4Packet_ERROR*>(fnetPacket);
        ASSERT_TRUE(ptr != NULL);
        EXPECT_EQUAL((uint32_t)PCODE_ERROR, ptr->GetPCODE());
        EXPECT_EQUAL(1008u, ptr->GetLength());
        delete ptr;
    }
}

TEST("require that FS4PersistentPacketStreamer can avoid compressing small packets") {
    FS4Packet_ERROR *packet = static_cast<FS4Packet_ERROR*>(FS4PacketFactory::CreateFS4Packet(PCODE_ERROR));
    packet->_errorCode = 1u;
    packet->setErrorMessage("a");

    FS4PersistentPacketStreamer streamer(FS4PacketFactory::CreateFS4Packet);

    FNET_DataBuffer buf1;
    streamer.Encode(packet, 1u, &buf1);
    EXPECT_EQUAL(21u, buf1.GetDataLen());

    streamer.SetCompressionLimit(10);
    FNET_DataBuffer buf2;
    streamer.Encode(packet, 1u, &buf2);
    EXPECT_EQUAL(21u, buf2.GetDataLen());

    delete packet;
}

TEST_MAIN() { TEST_RUN_ALL(); }
