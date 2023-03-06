// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "caching_rpc_target_resolver.h"
#include "message_codec_provider.h"
#include "rpc_envelope_proto.h"
#include "shared_rpc_resources.h"
#include "storage_api_rpc_service.h"
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storage/common/bucket_utils.h>
#include <vespa/storage/storageserver/communicationmanager.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storage/storageserver/rpcrequestwrapper.h>
#include <vespa/storageapi/mbusprot/protocolserialization7.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/trace/tracelevel.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".storage.storage_api_rpc_service");

using vespalib::compression::CompressionConfig;
using vespalib::TraceLevel;

namespace storage::rpc {

StorageApiRpcService::StorageApiRpcService(MessageDispatcher& message_dispatcher,
                                           SharedRpcResources& rpc_resources,
                                           MessageCodecProvider& message_codec_provider,
                                           const Params& params)
    : _message_dispatcher(message_dispatcher),
      _rpc_resources(rpc_resources),
      _message_codec_provider(message_codec_provider),
      _params(params),
      _target_resolver(std::make_unique<CachingRpcTargetResolver>(_rpc_resources.slobrok_mirror(), _rpc_resources.target_factory(),
                                                                  params.num_rpc_targets_per_node))
{
    register_server_methods(rpc_resources);
}

StorageApiRpcService::~StorageApiRpcService() = default;

StorageApiRpcService::Params::Params()
    : compression_config(),
      num_rpc_targets_per_node(1)
{}

StorageApiRpcService::Params::~Params() = default;

void StorageApiRpcService::register_server_methods(SharedRpcResources& rpc_resources) {
    FRT_ReflectionBuilder rb(&rpc_resources.supervisor());
    rb.DefineMethod(rpc_v1_method_name(), "bixbix", "bixbix", FRT_METHOD(StorageApiRpcService::RPC_rpc_v1_send), this);
    rb.RequestAccessFilter(FRT_RequireCapabilities::of(vespalib::net::tls::Capability::content_storage_api()));
    rb.MethodDesc("V1 of StorageAPI direct RPC protocol");
    rb.ParamDesc("header_encoding", "0=raw, 6=lz4");
    rb.ParamDesc("header_decoded_size", "Uncompressed header blob size");
    rb.ParamDesc("header_payload", "The message header blob");
    rb.ParamDesc("body_encoding", "0=raw, 6=lz4");
    rb.ParamDesc("body_decoded_size", "Uncompressed body blob size");
    rb.ParamDesc("body_payload", "The message body blob");
    rb.ReturnDesc("header_encoding",  "0=raw, 6=lz4");
    rb.ReturnDesc("header_decoded_size", "Uncompressed header blob size");
    rb.ReturnDesc("header_payload", "The reply header blob");
    rb.ReturnDesc("body_encoding",  "0=raw, 6=lz4");
    rb.ReturnDesc("body_decoded_size", "Uncompressed body blob size");
    rb.ReturnDesc("body_payload", "The reply body blob");
}

void StorageApiRpcService::detach_and_forward_to_enqueuer(std::shared_ptr<api::StorageMessage> cmd, FRT_RPCRequest* req) {
    // Create a request object to avoid needing a separate transport type
    cmd->setTransportContext(std::make_unique<StorageTransportContext>(std::make_unique<RPCRequestWrapper>(req)));
    req->Detach();
    _message_dispatcher.dispatch_sync(std::move(cmd));
}

namespace {

struct SubRefDeleter {
    template <typename T>
    void operator()(T* v) const noexcept {
        v->internal_subref();
    }
};

template <typename HeaderType>
bool decode_header_from_rpc_params(const FRT_Values& params, HeaderType& hdr) {
    const auto compression_type = vespalib::compression::CompressionConfig::toType(params[0]._intval8);
    const uint32_t uncompressed_length = params[1]._intval32;

    if (compression_type == vespalib::compression::CompressionConfig::NONE) {
        // Fast-path in the common case where request header is not compressed.
        return hdr.ParseFromArray(params[2]._data._buf, params[2]._data._len);
    } else {
        vespalib::DataBuffer uncompressed(params[2]._data._buf, params[2]._data._len);
        vespalib::ConstBufferRef blob(params[2]._data._buf, params[2]._data._len);
        decompress(compression_type, uncompressed_length, blob, uncompressed, true);
        assert(uncompressed_length == uncompressed.getDataLen());
        return hdr.ParseFromArray(uncompressed.getData(), uncompressed.getDataLen());
    }
}

// Must be done prior to adding payload
template <typename HeaderType>
void encode_header_into_rpc_params(HeaderType& hdr, FRT_Values& params) {
    params.AddInt8(vespalib::compression::CompressionConfig::Type::NONE); // TODO when needed
    const auto header_size = hdr.ByteSizeLong();
    assert(header_size <= UINT32_MAX);
    params.AddInt32(static_cast<uint32_t>(header_size));
    auto* header_buf = reinterpret_cast<uint8_t*>(params.AddData(header_size));
    hdr.SerializeWithCachedSizesToArray(header_buf);
}

void compress_and_add_payload_to_rpc_params(mbus::BlobRef payload,
                                            FRT_Values& params,
                                            const CompressionConfig& compression_cfg) {
    assert(payload.size() <= UINT32_MAX);
    vespalib::ConstBufferRef to_compress(payload.data(), payload.size());
    vespalib::DataBuffer buf(vespalib::roundUp2inN(payload.size()));
    auto comp_type = compress(compression_cfg, to_compress, buf, false);
    assert(buf.getDataLen() <= UINT32_MAX);

    params.AddInt8(comp_type);
    params.AddInt32(static_cast<uint32_t>(to_compress.size()));
    params.AddData(std::move(buf));
}

} // anon ns

template <typename MessageType>
void StorageApiRpcService::encode_and_compress_rpc_payload(const MessageType& msg, FRT_Values& params) {
    auto wrapped_codec = _message_codec_provider.wrapped_codec();
    auto payload = wrapped_codec->codec().encode(msg);

    compress_and_add_payload_to_rpc_params(payload, params, _params.compression_config);
}

template <typename PayloadCodecCallback>
bool StorageApiRpcService::uncompress_rpc_payload(
        const FRT_Values& params,
        PayloadCodecCallback payload_callback)
{
    const auto compression_type = vespalib::compression::CompressionConfig::toType(params[3]._intval8);
    const uint32_t uncompressed_length = params[4]._intval32;
    // TODO fast path if uncompressed?
    vespalib::DataBuffer uncompressed(params[5]._data._buf, params[5]._data._len);
    vespalib::ConstBufferRef blob(params[5]._data._buf, params[5]._data._len);
    decompress(compression_type, uncompressed_length, blob, uncompressed, true);
    assert(uncompressed_length == uncompressed.getDataLen());
    assert(uncompressed_length <= UINT32_MAX);
    auto wrapped_codec = _message_codec_provider.wrapped_codec();

    try {
        payload_callback(wrapped_codec->codec(), mbus::BlobRef(uncompressed.getData(), uncompressed_length));
    } catch (std::exception& e) {
        LOG(debug, "Caught exception during decode callback: '%s'", e.what());
        return false;
    }
    return true;
}

void StorageApiRpcService::RPC_rpc_v1_send(FRT_RPCRequest* req) {
    LOG(spam, "Server: received rpc.v1 request");
    // TODO do we need to manually check the parameter/return spec here?
    const auto& params = *req->GetParams();
    protobuf::RequestHeader hdr;
    if (!decode_header_from_rpc_params(params, hdr)) {
        req->SetError(FRTE_RPC_METHOD_FAILED, "Unable to decode RPC request header protobuf");
        return;
    }
    std::unique_ptr<mbusprot::StorageCommand> cmd;
    uint32_t uncompressed_size = 0;
    bool ok = uncompress_rpc_payload(params, [&cmd, &uncompressed_size](auto& codec, auto payload) {
        cmd = codec.decodeCommand(payload);
        uncompressed_size = static_cast<uint32_t>(payload.size());
    });
    if (ok) {
        assert(cmd && cmd->has_command());
        auto scmd = cmd->steal_command();
        scmd->setApproxByteSize(uncompressed_size);
        scmd->getTrace().setLevel(hdr.trace_level());
        scmd->setTimeout(std::chrono::milliseconds(hdr.time_remaining_ms()));
        req->DiscardBlobs();
        if (scmd->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
            scmd->getTrace().trace(TraceLevel::SEND_RECEIVE,
                                   vespalib::make_string("Request received at '%s' (tcp/%s:%d) with %u bytes of payload",
                                                         _rpc_resources.handle().c_str(),
                                                         _rpc_resources.hostname().c_str(),
                                                         _rpc_resources.listen_port(),
                                                         uncompressed_size));
        }
        detach_and_forward_to_enqueuer(std::move(scmd), req);
    } else {
        req->SetError(FRTE_RPC_METHOD_FAILED, "Unable to decode RPC request payload");
    }
}

void StorageApiRpcService::encode_rpc_v1_response(FRT_RPCRequest& request, api::StorageReply& reply) {
    LOG(spam, "Server: encoding rpc.v1 response header and payload");
    auto* ret = request.GetReturn();

    if (reply.getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        reply.getTrace().trace(TraceLevel::SEND_RECEIVE,
                               vespalib::make_string("Sending response from '%s'", _rpc_resources.handle().c_str()));
    }
    // TODO skip encoding header altogether if no relevant fields set?
    protobuf::ResponseHeader hdr;
    if (reply.getTrace().getLevel() > 0) {
        hdr.set_trace_payload(reply.getTrace().encode());
    }
    // TODO consistent naming...
    encode_header_into_rpc_params(hdr, *ret);
    encode_and_compress_rpc_payload<api::StorageReply>(reply, *ret);
}

void StorageApiRpcService::send_rpc_v1_request(std::shared_ptr<api::StorageCommand> cmd) {
    LOG(spam, "Client: sending rpc.v1 request for message of type %s to %s",
        cmd->getType().getName().c_str(), cmd->getAddress()->toString().c_str());

    assert(cmd->getAddress() != nullptr);
    auto target = _target_resolver->resolve_rpc_target(*cmd->getAddress(),
                                                       get_super_bucket_key(cmd->getBucketId()));
    if (!target) {
        auto reply = cmd->makeReply();
        reply->setResult(make_no_address_for_service_error(*cmd->getAddress()));
        if (reply->getTrace().shouldTrace(TraceLevel::ERROR)) {
            reply->getTrace().trace(TraceLevel::ERROR, reply->getResult().getMessage());
        }
        // Always dispatch async for synchronously generated replies, or we risk nuking the
        // stack if the reply receiver keeps resending synchronously as well.
        _message_dispatcher.dispatch_async(std::move(reply));
        return;
    }
    if (cmd->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        cmd->getTrace().trace(TraceLevel::SEND_RECEIVE,
                              vespalib::make_string("Sending request from '%s' to '%s' (%s) with timeout of %g seconds",
                                                    _rpc_resources.handle().c_str(),
                                                    CachingRpcTargetResolver::address_to_slobrok_id(*cmd->getAddress()).c_str(),
                                                    target->spec().c_str(), vespalib::to_s(cmd->getTimeout())));
    }
    std::unique_ptr<FRT_RPCRequest, SubRefDeleter> req(_rpc_resources.supervisor().AllocRPCRequest());
    req->SetMethodName(rpc_v1_method_name());

    protobuf::RequestHeader req_hdr;
    req_hdr.set_time_remaining_ms(std::chrono::duration_cast<std::chrono::milliseconds>(cmd->getTimeout()).count());
    req_hdr.set_trace_level(cmd->getTrace().getLevel());

    auto* params = req->GetParams();
    encode_header_into_rpc_params(req_hdr, *params);
    encode_and_compress_rpc_payload<api::StorageCommand>(*cmd, *params);

    const auto timeout = cmd->getTimeout();
    // TODO verify it's fine that we alloc this on the request stash and use it this way
    auto& req_ctx = req->getStash().create<RpcRequestContext>(std::move(cmd));
    req->SetContext(FNET_Context(&req_ctx));

    target->get()->InvokeAsync(req.release(), vespalib::to_s(timeout), this);
}

void StorageApiRpcService::RequestDone(FRT_RPCRequest* raw_req) {
    std::unique_ptr<FRT_RPCRequest, SubRefDeleter> req(raw_req);
    auto* req_ctx = static_cast<RpcRequestContext*>(req->GetContext()._value.VOIDP);
    auto& cmd = *req_ctx->_originator_cmd;
    if (!req->CheckReturnTypes("bixbix")) {
        handle_request_done_rpc_error(*req, *req_ctx);
        return;
    }
    LOG(spam, "Client: received rpc.v1 OK response");
    const auto& ret = *req->GetReturn();
    protobuf::ResponseHeader hdr;
    if (!decode_header_from_rpc_params(ret, hdr)) {
        handle_request_done_decode_error(*req_ctx, "Failed to decode RPC response header protobuf");
        return;
    }
    std::unique_ptr<mbusprot::StorageReply> wrapped_reply;
    uint32_t uncompressed_size = 0;
    bool ok = uncompress_rpc_payload(ret, [&wrapped_reply, &uncompressed_size, req_ctx](auto& codec, auto payload) {
        wrapped_reply = codec.decodeReply(payload, *req_ctx->_originator_cmd);
        uncompressed_size = payload.size();
    });
    if (!ok) {
        assert(!wrapped_reply);
        handle_request_done_decode_error(*req_ctx, "Failed to decode RPC response payload");
        return;
    }
    // TODO the reply wrapper does lazy deserialization. Can we/should we ever defer?
    auto reply = wrapped_reply->getInternalMessage(); // TODO message stealing
    assert(reply);

    if (!hdr.trace_payload().empty()) {
        cmd.getTrace().addChild(mbus::TraceNode::decode(hdr.trace_payload()));
    }
    if (cmd.getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        cmd.getTrace().trace(TraceLevel::SEND_RECEIVE,
                             vespalib::make_string("Response received at '%s' with %u bytes of payload",
                                                   _rpc_resources.handle().c_str(),
                                                   uncompressed_size));
    }
    reply->getTrace().swap(cmd.getTrace());
    reply->setApproxByteSize(uncompressed_size);

    // TODO ensure that no implicit long-lived refs end up pointing into RPC memory...!
    req->DiscardBlobs();
    _message_dispatcher.dispatch_sync(std::move(reply));
}

void StorageApiRpcService::handle_request_done_rpc_error(FRT_RPCRequest& req,
                                                         const RpcRequestContext& req_ctx) {
    auto& cmd = *req_ctx._originator_cmd;
    api::ReturnCode error;
    if (req.GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD) {
        error = api::ReturnCode(api::ReturnCode::NOT_CONNECTED, "Legacy MessageBus StorageAPI transport is no longer supported. "
                                                                "Old nodes must be upgraded to a newer Vespa version.");
    } else {
        error = map_frt_error_to_storage_api_error(req, req_ctx);
    }
    create_and_dispatch_error_reply(cmd, std::move(error));
}

void StorageApiRpcService::handle_request_done_decode_error(const RpcRequestContext& req_ctx,
                                                            vespalib::stringref description) {
    auto& cmd = *req_ctx._originator_cmd;
    assert(cmd.has_transport_context()); // Otherwise, reply already (destructively) generated by codec
    create_and_dispatch_error_reply(cmd, api::ReturnCode(
            static_cast<api::ReturnCode::Result>(mbus::ErrorCode::DECODE_ERROR), description));
}

void StorageApiRpcService::create_and_dispatch_error_reply(api::StorageCommand& cmd, api::ReturnCode error) {
    auto error_reply = cmd.makeReply();
    LOG(debug, "Client: rpc.v1 failed for target '%s': '%s'",
        cmd.getAddress()->toString().c_str(), error.toString().c_str());
    error_reply->getTrace().swap(cmd.getTrace());
    if (error_reply->getTrace().shouldTrace(TraceLevel::ERROR)) {
        error_reply->getTrace().trace(TraceLevel::ERROR, error.getMessage());
    }
    error_reply->setResult(std::move(error));
    _message_dispatcher.dispatch_sync(std::move(error_reply));
}

api::ReturnCode
StorageApiRpcService::map_frt_error_to_storage_api_error(FRT_RPCRequest& req,
                                                         const RpcRequestContext& req_ctx) {
    // TODO determine all codes that must be (re)mapped. Current remapping is adapted from RPCSend
    const auto& cmd = *req_ctx._originator_cmd;
    auto target_service = CachingRpcTargetResolver::address_to_slobrok_id(*cmd.getAddress());
    switch (req.GetErrorCode()) {
    case FRTE_RPC_TIMEOUT:
        return api::ReturnCode(
                static_cast<api::ReturnCode::Result>(mbus::ErrorCode::TIMEOUT),
                vespalib::make_string("A timeout occurred while waiting for '%s' (%g seconds expired); %s",
                                      target_service.c_str(), vespalib::to_s(cmd.getTimeout()), req.GetErrorMessage()));
    case FRTE_RPC_CONNECTION:
        return api::ReturnCode(
                static_cast<api::ReturnCode::Result>(mbus::ErrorCode::CONNECTION_ERROR),
                vespalib::make_string("A connection error occurred for '%s'; %s",
                                      target_service.c_str(), req.GetErrorMessage()));
    default:
        return api::ReturnCode(
                static_cast<api::ReturnCode::Result>(mbus::ErrorCode::NETWORK_ERROR),
                vespalib::make_string("A network error occurred for '%s'; %s",
                                      target_service.c_str(), req.GetErrorMessage()));
    }
}

api::ReturnCode
StorageApiRpcService::make_no_address_for_service_error(const api::StorageMessageAddress& addr) const {
    auto error_code = static_cast<api::ReturnCode::Result>(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE);
    auto error_msg = vespalib::make_string(
            "The address of service '%s' could not be resolved. It is not currently "
            "registered with the Vespa name server. "
            "The service must be having problems, or the routing configuration is wrong. "
            "Address resolution attempted from host '%s'",
            CachingRpcTargetResolver::address_to_slobrok_id(addr).c_str(),
            _rpc_resources.hostname().c_str());
    return api::ReturnCode(error_code, std::move(error_msg));
}

bool
StorageApiRpcService::address_visible_in_slobrok_uncached(
        const api::StorageMessageAddress& addr) const noexcept
{
    auto sb_id = CachingRpcTargetResolver::address_to_slobrok_id(addr);
    auto specs = _rpc_resources.slobrok_mirror().lookup(sb_id);
    return !specs.empty();
}


/*
 * Major TODOs:
 *   - lifetime semantics of FRT targets vs requests created from them?
 *   - lifetime of document type/fieldset repos vs messages
 *     - is repo ref squirreled away into the messages anywhere?
 *   - everything else! :3
 */

}
