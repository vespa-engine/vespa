// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcsendv2.h"
#include "rpcnetwork.h"
#include "rpcserviceaddress.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/tracelevel.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/fnet/frt/reflection.h>

using vespalib::make_string;
using vespalib::compression::CompressionConfig;
using vespalib::compression::decompress;
using vespalib::compression::compress;
using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using vespalib::stringref;
using vespalib::Memory;
using vespalib::Slime;
using vespalib::Version;
using namespace vespalib::slime;

namespace mbus {

namespace {

const char *METHOD_NAME   = "mbus.slime";
const char *METHOD_PARAMS = "bixbix";
const char *METHOD_RETURN = "bixbix";

Memory VERSION_F("version");
Memory ROUTE_F("route");
Memory SESSION_F("session");
Memory USERETRY_F("useretry");
Memory RETRYDELAY_F("retrydelay");
Memory RETRY_F("retry");
Memory TIMELEFT_F("timeleft");
Memory PROTOCOL_F("prot");
Memory TRACELEVEL_F("tracelevel");
Memory TRACE_F("trace");
Memory BLOB_F("msg");
Memory ERRORS_F("errors");
Memory CODE_F("code");
Memory MSG_F("msg");
Memory SERVICE_F("service");

}

bool RPCSendV2::isCompatible(stringref method, stringref request, stringref response)
{
    return  (method == METHOD_NAME) &&
            (request == METHOD_PARAMS) &&
            (response == METHOD_RETURN);
}

void
RPCSendV2::build(FRT_ReflectionBuilder & builder)
{
    builder.DefineMethod(METHOD_NAME, METHOD_PARAMS, METHOD_RETURN, true, FRT_METHOD(RPCSendV2::invoke), this);
    builder.MethodDesc("Send a message bus slime request and get a reply back.");
    builder.ParamDesc("header_encoding", "0=raw, 6=lz4");
    builder.ParamDesc("header_decoded_size", "Uncompressed header blob size");
    builder.ParamDesc("header_payload", "The message header blob in slime");
    builder.ParamDesc("body_encoding", "0=raw, 6=lz4");
    builder.ParamDesc("body_decoded_size", "Uncompressed body blob size");
    builder.ParamDesc("body_payload", "The message body blob in slime");
    builder.ReturnDesc("header_encoding",  "0=raw, 6=lz4");
    builder.ReturnDesc("header_decoded_size", "Uncompressed header blob size");
    builder.ReturnDesc("header_payload", "The reply header blob in slime.");
    builder.ReturnDesc("body_encoding",  "0=raw, 6=lz4");
    builder.ReturnDesc("body_decoded_size", "Uncompressed body blob size");
    builder.ReturnDesc("body_payload", "The reply body blob in slime.");
}

const char *
RPCSendV2::getReturnSpec() const {
    return METHOD_RETURN;
}

namespace {
class OutputBuf : public vespalib::Output {
public:
    explicit OutputBuf(size_t estimatedSize) : _buf(estimatedSize) { }
    DataBuffer & getBuf() { return _buf; }
private:
    vespalib::WritableMemory reserve(size_t bytes) override {
        _buf.ensureFree(bytes);
        return vespalib::WritableMemory(_buf.getFree(), _buf.getFreeLen());
    }
    Output &commit(size_t bytes) override {
        _buf.moveFreeToData(bytes);
        return *this;
    }
    DataBuffer _buf;
};
}

void
RPCSendV2::encodeRequest(FRT_RPCRequest &req, const Version &version, const Route & route,
                         const RPCServiceAddress & address, const Message & msg, uint32_t traceLevel,
                         const PayLoadFiller &filler, uint64_t timeRemaining) const
{
    FRT_Values &args = *req.GetParams();
    req.SetMethodName(METHOD_NAME);
    // Place holder for auxillary data to be transfered later.
    args.AddInt8(CompressionConfig::NONE);
    args.AddInt32(0);
    args.AddData("", 0);

    Slime slime;
    Cursor & root = slime.setObject();

    root.setString(VERSION_F, version.toString());
    root.setString(ROUTE_F, route.toString());
    root.setString(SESSION_F, address.getSessionName());
    root.setBool(USERETRY_F, msg.getRetryEnabled());
    root.setLong(RETRY_F, msg.getRetry());
    root.setLong(TIMELEFT_F, timeRemaining);
    root.setString(PROTOCOL_F, msg.getProtocol());
    root.setLong(TRACELEVEL_F, traceLevel);
    filler.fill(BLOB_F, root);

    OutputBuf rBuf(8192);
    BinaryFormat::encode(slime, rBuf);
    ConstBufferRef toCompress(rBuf.getBuf().getData(), rBuf.getBuf().getDataLen());
    DataBuffer buf(vespalib::roundUp2inN(rBuf.getBuf().getDataLen()));
    CompressionConfig::Type type = compress(_net->getCompressionConfig(), toCompress, buf, false);

    args.AddInt8(type);
    args.AddInt32(toCompress.size());
    const auto bufferLength = buf.getDataLen();
    assert(bufferLength <= INT32_MAX);
    args.AddData(buf.stealBuffer(), bufferLength);
}

namespace {

class ParamsV2 : public RPCSend::Params
{
public:
    ParamsV2(const FRT_Values &arg)
        : _slime()
    {
        uint8_t encoding = arg[3]._intval8;
        uint32_t uncompressedSize = arg[4]._intval32;
        DataBuffer uncompressed(arg[5]._data._buf, arg[5]._data._len);
        ConstBufferRef blob(arg[5]._data._buf, arg[5]._data._len);
        decompress(CompressionConfig::toType(encoding), uncompressedSize, blob, uncompressed, true);
        assert(uncompressedSize == uncompressed.getDataLen());
        BinaryFormat::decode(Memory(uncompressed.getData(), uncompressed.getDataLen()), _slime);
    }

    uint32_t getTraceLevel() const override { return _slime.get()[TRACELEVEL_F].asLong(); }
    bool useRetry() const override { return _slime.get()[USERETRY_F].asBool(); }
    uint32_t getRetries() const override { return _slime.get()[RETRY_F].asLong(); }
    uint64_t getRemainingTime() const override { return _slime.get()[TIMELEFT_F].asLong(); }

    Version getVersion() const override {
        return Version(_slime.get()[VERSION_F].asString().make_stringref());
    }
    stringref getRoute() const override {
        return _slime.get()[ROUTE_F].asString().make_stringref();
    }
    stringref getSession() const override {
        return _slime.get()[SESSION_F].asString().make_stringref();
    }
    stringref getProtocol() const override {
        return _slime.get()[PROTOCOL_F].asString().make_stringref();
    }
    BlobRef getPayload() const override {
        Memory m = _slime.get()[BLOB_F].asData();
        return BlobRef(m.data, m.size);
    }
private:
    Slime _slime;
};

}

std::unique_ptr<RPCSend::Params>
RPCSendV2::toParams(const FRT_Values &args) const
{
    return std::make_unique<ParamsV2>(args);
}

std::unique_ptr<Reply>
RPCSendV2::createReply(const FRT_Values & ret, const string & serviceName,
                       Error & error, vespalib::TraceNode & rootTrace) const
{
    uint8_t encoding = ret[3]._intval8;
    uint32_t uncompressedSize = ret[4]._intval32;
    DataBuffer uncompressed(ret[5]._data._buf, ret[5]._data._len);
    ConstBufferRef blob(ret[5]._data._buf, ret[5]._data._len);
    decompress(CompressionConfig::toType(encoding), uncompressedSize, blob, uncompressed, true);
    assert(uncompressedSize == uncompressed.getDataLen());
    Slime slime;
    BinaryFormat::decode(Memory(uncompressed.getData(), uncompressed.getDataLen()), slime);
    Inspector & root = slime.get();
    Version version(root[VERSION_F].asString().make_string());
    Memory payload = root[BLOB_F].asData();

    Reply::UP reply;
    if (payload.size > 0) {
        reply = decode(root[PROTOCOL_F].asString().make_stringref(), version, BlobRef(payload.data, payload.size), error);
    }
    if ( ! reply ) {
        reply.reset(new EmptyReply());
    }
    reply->setRetryDelay(root[RETRYDELAY_F].asDouble());
    Inspector & errors = root[ERRORS_F];
    for (uint32_t i = 0; i < errors.entries(); ++i) {
        Inspector & e = errors[i];
        Memory service = e[SERVICE_F].asString();
        reply->addError(Error(e[CODE_F].asLong(), e[MSG_F].asString().make_string(),
                              (service.size > 0) ? service.make_string() : serviceName));
    }
    rootTrace.addChild(TraceNode::decode(root[TRACE_F].asString().make_string()));
    return reply;
}

void
RPCSendV2::createResponse(FRT_Values & ret, const string & version, Reply & reply, Blob payload) const
{
    // Place holder for auxillary data to be transfered later.
    ret.AddInt8(CompressionConfig::NONE);
    ret.AddInt32(0);
    ret.AddData("", 0);

    Slime slime;
    Cursor & root = slime.setObject();

    root.setString(VERSION_F, version);
    root.setDouble(RETRYDELAY_F, reply.getRetryDelay());
    root.setString(PROTOCOL_F, reply.getProtocol());
    root.setData(BLOB_F, vespalib::Memory(payload.data(), payload.size()));
    if (reply.getTrace().getLevel() > 0) {
        root.setString(TRACE_F, reply.getTrace().getRoot().encode());
    }

    if (reply.getNumErrors() > 0) {
        Cursor & array = root.setArray(ERRORS_F);
        for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
            Cursor & error = array.addObject();
            error.setLong(CODE_F, reply.getError(i).getCode());
            error.setString(MSG_F, reply.getError(i).getMessage());
            error.setString(SERVICE_F, reply.getError(i).getService().c_str());
        }
    }

    OutputBuf rBuf(8192);
    BinaryFormat::encode(slime, rBuf);
    ConstBufferRef toCompress(rBuf.getBuf().getData(), rBuf.getBuf().getDataLen());
    DataBuffer buf(vespalib::roundUp2inN(rBuf.getBuf().getDataLen()));
    CompressionConfig::Type type = compress(_net->getCompressionConfig(), toCompress, buf, false);

    ret.AddInt8(type);
    ret.AddInt32(toCompress.size());
    const auto bufferLength = buf.getDataLen();
    assert(bufferLength <= INT32_MAX);
    ret.AddData(buf.stealBuffer(), bufferLength);

}

} // namespace mbus
