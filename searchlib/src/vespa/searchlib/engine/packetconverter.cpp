// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packetconverter.h"
#include <vespa/log/log.h>
LOG_SETUP(".engine.packetconverter");

namespace {

bool checkFeature(uint32_t features, uint32_t mask) {
    return ((features & mask) != 0);
}

struct FS4PropertiesBuilder : public search::fef::IPropertiesVisitor {
    uint32_t idx;
    search::fs4transport::FS4Properties &props;
    FS4PropertiesBuilder(search::fs4transport::FS4Properties &p) : idx(0), props(p) {}
    void visitProperty(const search::fef::Property::Value &key,
                               const search::fef::Property &values) override
    {
        for (uint32_t i = 0; i < values.size(); ++i) {
            props.setKey(idx, key.data(), key.size());
            props.setValue(idx, values.getAt(i).data(), values.getAt(i).size());
            ++idx;
        }
    }
};

} // namespace <unnamed>

namespace search::engine {

using namespace search::fs4transport;

void
PacketConverter::fillPacketProperties(const PropertiesMap &source, PropsVector& target)
{
    target.resize(source.size());
    PropertiesMap::ITR itr = source.begin();
    PropertiesMap::ITR end = source.end();
    for (uint32_t i = 0; itr != end; ++itr, ++i) {
        const vespalib::string &name = itr->first;
        const search::fef::Properties &values = itr->second;
        target[i].setName(name.c_str(), name.size());
        target[i].allocEntries(values.numValues());
        FS4PropertiesBuilder builder(target[i]);
        values.visitProperties(builder);
        LOG_ASSERT(builder.idx == target[i].size());
        LOG_ASSERT(builder.idx == values.numValues());
    }
}

void
PacketConverter::toSearchRequest(const QUERYX &packet, SearchRequest &request)
{
    request.offset     = packet._offset;
    request.maxhits    = packet._maxhits;
    request.setTimeout(packet.getTimeout());
    request.queryFlags = packet._qflags;
    request.ranking    = packet._ranking;

    for (uint32_t i = 0; i < packet._propsVector.size(); ++i) {
        const FS4Properties &src = packet._propsVector[i];
        search::fef::Properties &dst = request.propertiesMap.lookupCreate(src.getName());
        for (uint32_t e = 0; e < src.size(); ++e) {
            dst.add(vespalib::stringref(src.getKey(e), src.getKeyLen(e)),
                    vespalib::stringref(src.getValue(e), src.getValueLen(e)));
        }
    }
    request.sortSpec        = packet._sortSpec;
    request.groupSpec.assign( packet._groupSpec.begin(), packet._groupSpec.end());
    request.sessionId.assign( packet._sessionId.begin(), packet._sessionId.end());
    request.location        = packet._location;
    request.stackItems      = packet._numStackItems;
    request.stackDump.assign( packet._stackDump.begin(), packet._stackDump.end());
}

void
PacketConverter::fromSearchRequest(const SearchRequest &request, QUERYX &packet)
{
    // not needed yet
    (void) packet;
    (void) request;
    LOG_ABORT("not implemented");
}

void
PacketConverter::toSearchReply(const QUERYRESULTX &packet, SearchReply &reply)
{
    // not needed yet
    (void) packet;
    (void) reply;
    LOG_ABORT("not implemented");
}

void
PacketConverter::fromSearchReply(const SearchReply &reply, QUERYRESULTX &packet)
{
    packet._offset     = reply.offset;
    packet._numDocs    = reply.hits.size();
    packet._totNumDocs = reply.totalHitCount;
    packet._maxRank    = reply.maxRank;
    packet.setDistributionKey(reply.getDistributionKey());
    if (reply.sortIndex.size() > 0) {
        packet._features |= QRF_SORTDATA;
        uint32_t idxCnt = reply.sortIndex.size();
        LOG_ASSERT(reply.sortIndex.size() == reply.hits.size()+1);
        // allocate for N hits (will make space for N+1 indexes)
        packet.AllocateSortIndex(reply.hits.size());
        packet.AllocateSortData(reply.sortData.size());
        for (uint32_t i = 0; i < idxCnt; ++i) {
            packet._sortIndex[i] = reply.sortIndex[i];
        }
        memcpy(packet._sortData, &(reply.sortData[0]), reply.sortData.size());
    }
    if (reply.groupResult.size() > 0) {
        packet._features |= QRF_GROUPDATA;
        packet.AllocateGroupData(reply.groupResult.size());
        memcpy(packet._groupData, &(reply.groupResult[0]), reply.groupResult.size());
    }
    packet._coverageDocs = reply.coverage.getCovered();
    packet._activeDocs = reply.coverage.getActive();
    packet._soonActiveDocs = reply.coverage.getSoonActive();
    packet._coverageDegradeReason = reply.coverage.getDegradeReason();
    packet.setNodesQueried(reply.coverage.getNodesQueried());
    packet.setNodesReplied(reply.coverage.getNodesReplied());
    if (reply.request && (reply.request->queryFlags & QFLAG_COVERAGE_NODES)) {
        packet._features |= QRF_COVERAGE_NODES;
    }
    if (reply.useWideHits) {
        packet._features |= QRF_MLD;
    }
    if (reply.propertiesMap.size() > 0) {
        fillPacketProperties(reply.propertiesMap, packet._propsVector);
        packet._features |= QRF_PROPERTIES;
    }
    uint32_t hitCnt = reply.hits.size();
    packet.AllocateHits(hitCnt);
    for (uint32_t i = 0; i < hitCnt; ++i) {
        packet._hits[i]._gid      = reply.hits[i].gid;
        packet._hits[i]._metric   = reply.hits[i].metric;
        packet._hits[i]._partid   = reply.hits[i].path;
        packet._hits[i].setDistributionKey(reply.hits[i].getDistributionKey());
    }
}

void
PacketConverter::toDocsumRequest(const GETDOCSUMSX &packet, DocsumRequest &request)
{
    request.setTimeout(packet.getTimeout());
    request.ranking           = packet._ranking;
    request.queryFlags        = packet._qflags;
    request.resultClassName   = packet._resultClassName;
    for (uint32_t i = 0; i < packet._propsVector.size(); ++i) {
        const FS4Properties &src = packet._propsVector[i];
        search::fef::Properties &dst = request.propertiesMap.lookupCreate(src.getName());
        for (uint32_t e = 0; e < src.size(); ++e) {
            dst.add(vespalib::stringref(src.getKey(e), src.getKeyLen(e)),
                    vespalib::stringref(src.getValue(e), src.getValueLen(e)));
        }
    }
    request.stackItems = packet._stackItems;
    request.stackDump.assign(packet._stackDump.begin(), packet._stackDump.end());
    request.location = packet._location;
    request._flags = packet._flags;
    request.useWideHits = checkFeature(packet._features, GDF_MLD);
    uint32_t hitCnt = packet._docid.size();
    request.hits.resize(hitCnt);
    for (uint32_t i = 0; i < hitCnt; ++i) {
        request.hits[i].gid      = packet._docid[i]._gid;
        request.hits[i].path     = packet._docid[i]._partid;
    }
    search::fef::Property sessionId =
        request.propertiesMap.rankProperties().lookup("sessionId");
    if (sessionId.found()) {
        vespalib::string id = sessionId.get();
        request.sessionId.assign(id.begin(), id.end());
    }
}

void
PacketConverter::fromDocsumRequest(const DocsumRequest &request, GETDOCSUMSX &packet)
{
    // not needed yet
    (void) packet;
    (void) request;
    LOG_ABORT("not implemented");
}

void
PacketConverter::toDocsumReplyElement(const DOCSUM &packet, DocsumReply::Docsum &docsum)
{
    // not needed yet
    (void) packet;
    (void) docsum;
    LOG_ABORT("not implemented");
}

void
PacketConverter::fromDocsumReplyElement(const DocsumReply::Docsum &docsum, DOCSUM &packet)
{
    if (docsum.data.get() != 0) {
        packet.SetBuf(docsum.data.c_str(), docsum.data.size());
    }
    packet.setGid(docsum.gid);
}

void
PacketConverter::toMonitorRequest(const MONITORQUERYX &packet, MonitorRequest &request)
{
    request.flags   = packet._qflags;
    if ((packet._qflags & MQFLAG_REPORT_ACTIVEDOCS) != 0) {
        request.reportActiveDocs = true;
    }
}

void
PacketConverter::fromMonitorRequest(const MonitorRequest &request, MONITORQUERYX &packet)
{
    // not needed yet
    (void) packet;
    (void) request;
    LOG_ABORT("not implemented");
}

void
PacketConverter::toMonitorReply(const MONITORRESULTX &packet, MonitorReply &reply)
{
    // not needed yet
    (void) packet;
    (void) reply;
    LOG_ABORT("not implemented");
}

void
PacketConverter::fromMonitorReply(const MonitorReply &reply, MONITORRESULTX &packet)
{
    if (reply.mld) {
        packet._features |= MRF_MLD;
    }
    if (reply.activeDocsRequested) {
        packet._features |= MRF_ACTIVEDOCS;
        packet._activeDocs = reply.activeDocs;
    }
    packet._partid      = reply.partid;
    packet._timestamp   = reply.timestamp;
    packet._totalNodes  = reply.totalNodes;
    packet._activeNodes = reply.activeNodes;
    packet._totalParts  = reply.totalParts;
    packet._activeParts = reply.activeParts;
    packet._rflags      = reply.flags;
    if (packet._rflags != 0) {
        packet._features |= MRF_RFLAGS;
    }
}

void
PacketConverter::fromTraceReply(const TraceReply &reply, TRACEREPLY &packet)
{
    fillPacketProperties(reply.propertiesMap, packet._propsVector);
}

}

