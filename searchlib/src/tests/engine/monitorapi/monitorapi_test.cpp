// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("monitorapi_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/engine/monitorapi.h>
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
    void convertToRequest();
    void convertFromReply();
    int Main() override;
};

void
Test::convertToRequest()
{
    FS4Packet_MONITORQUERYX src;
    src._features |= MQF_QFLAGS;
    src._qflags = 1u;

    { // copy all
        FS4Packet_MONITORQUERYX cpy;
        copyPacket(src, cpy);

        MonitorRequest dst;
        PacketConverter::toMonitorRequest(cpy, dst);
        EXPECT_EQUAL(dst.flags, 1u);
    }
}

void
Test::convertFromReply()
{
    MonitorReply src;
    src.mld = true;
    src.partid = 1u;
    src.timestamp = 2u;
    src.totalNodes = 3u;
    src.activeNodes = 4u;
    src.totalParts = 5u;
    src.activeParts = 6u;
    src.flags = 7u;
    src.activeDocs = 8u;
    src.activeDocsRequested = true;

    { // full copy
        MonitorReply cpy = src;

        FS4Packet_MONITORRESULTX dst;
        PacketConverter::fromMonitorReply(cpy, dst);
        EXPECT_EQUAL(dst._partid, 1u);
        EXPECT_EQUAL(dst._timestamp, 2u);
        EXPECT_TRUE(checkFeature(dst._features, MRF_MLD));
        EXPECT_EQUAL(dst._totalNodes, 3u);
        EXPECT_EQUAL(dst._activeNodes, 4u);
        EXPECT_EQUAL(dst._totalParts, 5u);
        EXPECT_EQUAL(dst._activeParts, 6u);
        EXPECT_TRUE(checkFeature(dst._features, MRF_RFLAGS));
        EXPECT_EQUAL(dst._rflags, 7u);
        EXPECT_EQUAL(dst._activeDocs, 8u);
        EXPECT_TRUE(checkFeature(dst._features, MRF_ACTIVEDOCS));
    }
    { // non-mld
        MonitorReply cpy = src;
        cpy.mld = false;

        FS4Packet_MONITORRESULTX dst;
        PacketConverter::fromMonitorReply(cpy, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, MRF_MLD));
    }
    { // without flags
        MonitorReply cpy = src;
        cpy.flags = 0;

        FS4Packet_MONITORRESULTX dst;
        PacketConverter::fromMonitorReply(cpy, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, MRF_RFLAGS));
        EXPECT_EQUAL(dst._rflags, 0u);
    }
    { // without activedocs
        MonitorReply cpy = src;
        cpy.activeDocsRequested = false;

        FS4Packet_MONITORRESULTX dst;
        PacketConverter::fromMonitorReply(cpy, dst);
        EXPECT_TRUE(checkNotFeature(dst._features, MRF_ACTIVEDOCS));
        EXPECT_EQUAL(dst._activeDocs, 0u);
    }
}

int
Test::Main()
{
    TEST_INIT("monitorapi_test");
    convertToRequest();
    convertFromReply();
    TEST_DONE();
}

TEST_APPHOOK(Test);
