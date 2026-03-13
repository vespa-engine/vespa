// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcsendv2.h"
#include "rpcnetwork.h"
#include "rpcserviceaddress.h"
#include <vespa/fnet/frt/reflection.h>
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/metadata_extractor.h>
#include <vespa/messagebus/metadata_injector.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;
using vespalib::compression::CompressionConfig;
using vespalib::compression::decompress;
using vespalib::compression::compress;
using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using std::string_view;
using vespalib::Memory;
using vespalib::Slime;
using vespalib::Version;
using namespace vespalib::slime;

namespace mbus {

namespace {

const char *METHOD_NAME   = "mbus.slime";
const char *METHOD_PARAMS = "bixbix";
const char *METHOD_RETURN = "bixbix";

// Header fields:
Memory KVS_F("kvs");
// Body fields:
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

bool
RPCSendV2::isCompatible(string_view method, string_view request, string_view response)
{
    return  (method == METHOD_NAME) &&
            (request == METHOD_PARAMS) &&
            (response == METHOD_RETURN);
}

void
RPCSendV2::build(FRT_ReflectionBuilder & builder, CapabilitySet required_capabilities)
{
    builder.DefineMethod(METHOD_NAME, METHOD_PARAMS, METHOD_RETURN, FRT_METHOD(RPCSendV2::invoke), this);
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
    builder.RequestAccessFilter(FRT_RequireCapabilities::of(required_capabilities));
}

const char *
RPCSendV2::getReturnSpec() const {
    return METHOD_RETURN;
}

namespace {
class OutputBuf : public vespalib::Output {
public:
    explicit OutputBuf(size_t estimatedSize) : _buf(estimatedSize) { }
    ~OutputBuf() override;
    DataBuffer & getBuf() { return _buf; }
private:
    vespalib::WritableMemory reserve(size_t bytes) override {
        _buf.ensureFree(bytes);
        return {_buf.getFree(), _buf.getFreeLen()};
    }
    Output &commit(size_t bytes) override {
        _buf.moveFreeToData(bytes);
        return *this;
    }
    DataBuffer _buf;
};
OutputBuf::~OutputBuf() = default;

class SlimeMetadataInjector final : public MetadataInjector {
    struct LazySlimeState {
        Slime slime;
        Cursor& kv_cursor;

        LazySlimeState();
        ~LazySlimeState();
    };
    std::optional<LazySlimeState> _lazy_slime;
public:
    SlimeMetadataInjector();
    ~SlimeMetadataInjector() override;

    void inject_key_value(std::string_view key, std::string_view value) override {
        if (!_lazy_slime) {
            _lazy_slime.emplace();
        }
        _lazy_slime->kv_cursor.setString(key, value);
    }

    [[nodiscard]] bool has_metadata() const noexcept {
        return static_cast<bool>(_lazy_slime);
    }

    // Precondition: has_metadata() == true
    void encode_into(OutputBuf& out_buf) const {
        BinaryFormat::encode(_lazy_slime->slime, out_buf);
    }
};

SlimeMetadataInjector::SlimeMetadataInjector()  = default;
SlimeMetadataInjector::~SlimeMetadataInjector() = default;

SlimeMetadataInjector::LazySlimeState::LazySlimeState()
    : slime(),
      kv_cursor(slime.setObject().setObject(KVS_F))
{}
SlimeMetadataInjector::LazySlimeState::~LazySlimeState() = default;

void encode_message_header_metadata(FRT_Values& args, const Message& msg) {
    // The KV header is never compressed. This is intentional and is done to prevent
    // compression oracle attacks (a-la CRIME/BREACH) that can be used to deduce the
    // value of secret tokens from observing the change in ciphertext sizes on the
    // wire across many messages.
    args.AddInt8(CompressionConfig::NONE);

    SlimeMetadataInjector injector;
    msg.injectMetadata(injector);

    if (!injector.has_metadata()) {
        args.AddInt32(0);
        args.AddData("", 0);
    } else {
        OutputBuf hdr_buf(128); // TODO empirically choose this based on likely header KV sizes
        injector.encode_into(hdr_buf);
        args.AddInt32(hdr_buf.getBuf().getDataLen());
        args.AddData(hdr_buf.getBuf().getData(), hdr_buf.getBuf().getDataLen());
    }
}

} // namespace

void
RPCSendV2::encodeRequest(FRT_RPCRequest &req, const Version &version, const Route & route,
                         const RPCServiceAddress & address, const Message & msg, uint32_t traceLevel,
                         const PayLoadFiller &filler, duration timeRemaining) const
{
    FRT_Values &args = *req.GetParams();
    req.SetMethodName(METHOD_NAME);
    encode_message_header_metadata(args, msg);

    Slime slime;
    Cursor & root = slime.setObject();

    root.setString(VERSION_F, version.toAbbreviatedString());
    root.setString(ROUTE_F, route.toString());
    root.setString(SESSION_F, address.getSessionName());
    root.setBool(USERETRY_F, msg.getRetryEnabled());
    root.setLong(RETRY_F, msg.getRetry());
    root.setLong(TIMELEFT_F, vespalib::count_ms(timeRemaining));
    root.setString(PROTOCOL_F, msg.getProtocol());
    root.setLong(TRACELEVEL_F, traceLevel);
    filler.fill(BLOB_F, root);

    OutputBuf rBuf(8_Ki);
    BinaryFormat::encode(slime, rBuf);
    ConstBufferRef toCompress(rBuf.getBuf().getData(), rBuf.getBuf().getDataLen());
    DataBuffer buf(vespalib::roundUp2inN(rBuf.getBuf().getDataLen()));
    CompressionConfig::Type type = compress(_net->getCompressionConfig(), toCompress, buf, false);

    args.AddInt8(type);
    args.AddInt32(toCompress.size());
    const auto bufferLength = buf.getDataLen();
    assert(bufferLength <= INT32_MAX);
    args.AddData(std::move(buf).stealBuffer(), bufferLength);
}

namespace {

class SlimeMetadataExtractor final : public MetadataExtractor {
    Slime            _slime;
    const Inspector* _kvs_inspector;
public:
    SlimeMetadataExtractor(const Memory& memory)
        : _slime(),
          _kvs_inspector(nullptr)
    {
        BinaryFormat::decode(memory, _slime);
        _kvs_inspector = &_slime.get()[KVS_F];
    }
    ~SlimeMetadataExtractor() override = default;

    std::optional<std::string> extract_value(std::string_view key) const override {
        const Inspector& v = (*_kvs_inspector)[key];
        return v.valid() ? std::optional(v.asString().make_string()) : std::nullopt;
    }
};

class ParamsV2 final : public RPCSend::Params
{
public:
    explicit ParamsV2(const FRT_Values &arg)
        : _slime(),
          _meta_extractor()
    {
        decode_header_if_present(arg);
        decode_body(arg);
    }

    void decode_header_if_present(const FRT_Values& arg) {
        const uint8_t encoding = arg[0]._intval8;
        const uint32_t hdr_blob_size = arg[1]._intval32;
        // DataBuffer::getDataLen() has different semantics depending on ctor buffer constness...
        const DataBuffer hdr_blob(static_cast<const void*>(arg[2]._data._buf), arg[2]._data._len);
        if ((hdr_blob_size > 0) && (hdr_blob.getDataLen() == hdr_blob_size) &&
            CompressionConfig::toType(encoding) == CompressionConfig::NONE)
        {
            _meta_extractor = std::make_unique<SlimeMetadataExtractor>(Memory(hdr_blob.getData(), hdr_blob.getDataLen()));
        }
    }

    void decode_body(const FRT_Values& arg) {
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
    duration getRemainingTime() const override { return std::chrono::milliseconds(_slime.get()[TIMELEFT_F].asLong()); }

    Version getVersion() const override {
        return Version(_slime.get()[VERSION_F].asString().make_string());
    }
    string_view getRoute() const override {
        return _slime.get()[ROUTE_F].asString().make_stringview();
    }
    string_view getSession() const override {
        return _slime.get()[SESSION_F].asString().make_stringview();
    }
    string_view getProtocol() const override {
        return _slime.get()[PROTOCOL_F].asString().make_stringview();
    }
    BlobRef getPayload() const override {
        Memory m = _slime.get()[BLOB_F].asData();
        return BlobRef(m.data, m.size);
    }
    std::unique_ptr<MetadataExtractor> steal_metadata_extractor() noexcept override {
        return std::move(_meta_extractor);
    }
private:
    Slime _slime;
    std::unique_ptr<SlimeMetadataExtractor> _meta_extractor; // nullptr if no header metadata
};

}

std::unique_ptr<RPCSend::Params>
RPCSendV2::toParams(const FRT_Values &args) const
{
    return std::make_unique<ParamsV2>(args);
}

std::unique_ptr<Reply>
RPCSendV2::createReply(const FRT_Values & ret, const string & serviceName,
                       Error & error, vespalib::Trace & rootTrace) const
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
        reply = decode(root[PROTOCOL_F].asString().make_stringview(), version, BlobRef(payload.data, payload.size), error);
    }
    if ( ! reply ) {
        reply = std::make_unique<EmptyReply>();
    }
    reply->setRetryDelay(root[RETRYDELAY_F].asDouble());
    Inspector & errors = root[ERRORS_F];
    for (uint32_t i = 0; i < errors.entries(); ++i) {
        Inspector & e = errors[i];
        Memory service = e[SERVICE_F].asString();
        reply->addError(Error(e[CODE_F].asLong(), e[MSG_F].asString().make_string(),
                              (service.size > 0) ? service.make_string() : serviceName));
    }
    Inspector & trace = root[TRACE_F];
    if (trace.valid() && (trace.asString().size > 0)) {
        rootTrace.addChild(TraceNode::decode(trace.asString().make_string()));
    }
    return reply;
}

void
RPCSendV2::createResponse(FRT_Values & ret, const string & version, Reply & reply, Blob payload) const
{
    // We don't currently encode headers for replies, only requests. This is
    // partly because MessageBus may transparently merge multiple replies from
    // forked message request paths, and it's not clear what the correct conflict
    // resolution strategy would be for multiple values for the same key.
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
        root.setString(TRACE_F, reply.getTrace().encode());
    }

    if (reply.getNumErrors() > 0) {
        Cursor & array = root.setArray(ERRORS_F);
        for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
            Cursor & error = array.addObject();
            error.setLong(CODE_F, reply.getError(i).getCode());
            error.setString(MSG_F, reply.getError(i).getMessage());
            error.setString(SERVICE_F, reply.getError(i).getService());
        }
    }

    OutputBuf rBuf(8_Ki);
    BinaryFormat::encode(slime, rBuf);
    ConstBufferRef toCompress(rBuf.getBuf().getData(), rBuf.getBuf().getDataLen());
    DataBuffer buf(vespalib::roundUp2inN(rBuf.getBuf().getDataLen()));
    CompressionConfig::Type type = compress(_net->getCompressionConfig(), toCompress, buf, false);

    ret.AddInt8(type);
    ret.AddInt32(toCompress.size());
    assert(buf.getDataLen() <= INT32_MAX);
    ret.AddData(std::move(buf));
}

} // namespace mbus
