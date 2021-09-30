// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsum_by_slime.h"
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.summaryengine.docsum_by_slime");

namespace proton {

using search::engine::DocsumRequest;
using search::engine::DocsumReply;
using vespalib::slime::Inspector;
using vespalib::slime::Cursor;
using vespalib::slime::ObjectSymbolInserter;
using vespalib::Memory;
using vespalib::slime::Symbol;
using vespalib::slime::BinaryFormat;
using vespalib::slime::ArrayTraverser;
using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using vespalib::compression::CompressionConfig;

namespace {

Memory SESSIONID("sessionid");
Memory RANKING("ranking");
Memory LOCATION("location");
Memory SUMMARYCLASS("class");
Memory DOCUMENTTYPE("doctype");
Memory GIDS("gids");
Memory DOCSUM("docsum");
Memory DOCSUMS("docsums");

class GidTraverser : public ArrayTraverser
{
public:
    GidTraverser(std::vector<DocsumRequest::Hit> & hits) : _hits(hits) { }
    void entry(size_t idx, const Inspector &inspector) override {
        (void) idx;
        Memory data(inspector.asData());
        assert(data.size >= document::GlobalId::LENGTH);
        _hits.emplace_back(document::GlobalId(data.data));
    }
private:
    std::vector<DocsumRequest::Hit> & _hits;
};

CompressionConfig
getCompressionConfig()
{
    using search::fs4transport::FS4PersistentPacketStreamer;
    const FS4PersistentPacketStreamer & streamer = FS4PersistentPacketStreamer::Instance;
    return CompressionConfig(streamer.getCompressionType(), streamer.getCompressionLevel(), 80, streamer.getCompressionLimit());
}

}

DocsumRequest::UP
DocsumBySlime::slimeToRequest(const Inspector & request)
{
    DocsumRequest::UP docsumRequest(std::make_unique<DocsumRequest>(true));

    docsumRequest->resultClassName = request[SUMMARYCLASS].asString().make_string();

    Memory m = request[SESSIONID].asData();
    if (m.size > 0) {
        docsumRequest->sessionId.resize(m.size);
        memcpy(&docsumRequest->sessionId[0], m.data, m.size);
        docsumRequest->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    }

    Memory d = request[DOCUMENTTYPE].asString();
    if (d.size > 0) {
        docsumRequest->propertiesMap.lookupCreate(search::MapNames::MATCH).add("documentdb.searchdoctype", d.make_string());
    }

    docsumRequest->ranking = request[RANKING].asString().make_string();
    docsumRequest->location = request[LOCATION].asString().make_string();
    Inspector & gids = request[GIDS];
    docsumRequest->hits.reserve(gids.entries());
    GidTraverser gidFiller(docsumRequest->hits);
    gids.traverse(gidFiller);

    return docsumRequest;
}

vespalib::Slime::UP
DocsumBySlime::getDocsums(const Inspector & req)
{
    DocsumReply::UP reply = _docsumServer.getDocsums(slimeToRequest(req));
    if (reply && reply->_root) {
        return std::move(reply->_root);
    } else {
        LOG(warning, "got <null> docsum reply from back-end");
    }
    return std::make_unique<vespalib::Slime>();
}

DocsumByRPC::DocsumByRPC(DocsumBySlime & slimeDocsumServer) :
    _slimeDocsumServer(slimeDocsumServer)
{
}

void
DocsumByRPC::getDocsums(FRT_RPCRequest & req)
{
    using vespalib::compression::decompress;
    using vespalib::compression::compress;
    FRT_Values &arg = *req.GetParams();
    uint8_t encoding = arg[0]._intval8;
    uint32_t uncompressedSize = arg[1]._intval32;
    DataBuffer uncompressed(arg[2]._data._buf, arg[2]._data._len);
    ConstBufferRef blob(arg[2]._data._buf, arg[2]._data._len);
    decompress(CompressionConfig::toType(encoding), uncompressedSize, blob, uncompressed, true);
    assert(uncompressedSize == uncompressed.getDataLen());
    vespalib::Slime summariesToGet;
    BinaryFormat::decode(Memory(uncompressed.getData(), uncompressed.getDataLen()), summariesToGet);

    vespalib::Slime::UP summaries = _slimeDocsumServer.getDocsums(summariesToGet.get());
    assert(summaries);  // Mandatory, not optional.

    search::RawBuf rbuf(4_Ki);
    search::SlimeOutputRawBufAdapter output(rbuf);
    BinaryFormat::encode(*summaries, output);
    ConstBufferRef buf(rbuf.GetDrainPos(), rbuf.GetUsedLen());
    DataBuffer compressed(rbuf.GetWritableDrainPos(0), rbuf.GetUsedLen());
    CompressionConfig::Type type = compress(getCompressionConfig(), buf, compressed, true);

    FRT_Values &ret = *req.GetReturn();
    ret.AddInt8(type);
    ret.AddInt32(buf.size());
    ret.AddData(compressed.getData(), compressed.getDataLen());
}

}
