// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpc_target.h"
#include <vespa/fnet/frt/invokable.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <atomic>
#include <memory>

class FRT_RPCRequest;
class FRT_Target;

namespace document { class DocumentTypeRepo; }

namespace storage {

class MessageDispatcher;

namespace api {
class StorageCommand;
class StorageMessage;
class StorageMessageAddress;
class StorageReply;
}

namespace rpc {

class CachingRpcTargetResolver;
class MessageCodecProvider;
class SharedRpcResources;

class StorageApiRpcService : public FRT_Invokable, public FRT_IRequestWait {
public:
    struct Params {
        vespalib::compression::CompressionConfig compression_config;
        size_t num_rpc_targets_per_node;

        Params();
        ~Params();
    };
private:
    MessageDispatcher&    _message_dispatcher;
    SharedRpcResources&   _rpc_resources;
    MessageCodecProvider& _message_codec_provider;
    const Params          _params;
    std::unique_ptr<CachingRpcTargetResolver> _target_resolver;
public:
    StorageApiRpcService(MessageDispatcher& message_dispatcher,
                         SharedRpcResources& rpc_resources,
                         MessageCodecProvider& message_codec_provider,
                         const Params& params);
    ~StorageApiRpcService() override;

    // Bypasses resolver cache and returns whether local Slobrok mirror has at least 1 spec for the given address.
    [[nodiscard]] bool address_visible_in_slobrok_uncached(const api::StorageMessageAddress& addr) const noexcept;

    void RPC_rpc_v1_send(FRT_RPCRequest* req);
    void encode_rpc_v1_response(FRT_RPCRequest& request, api::StorageReply& reply);
    void send_rpc_v1_request(std::shared_ptr<api::StorageCommand> cmd);

    static constexpr const char* rpc_v1_method_name() noexcept {
        return "storageapi.v1.send";
    }
private:
    void detach_and_forward_to_enqueuer(std::shared_ptr<api::StorageMessage> cmd, FRT_RPCRequest* req);

    struct RpcRequestContext {
        std::shared_ptr<api::StorageCommand> _originator_cmd;

        explicit RpcRequestContext(std::shared_ptr<api::StorageCommand> cmd)
            : _originator_cmd(std::move(cmd))
        {}
    };

    void register_server_methods(SharedRpcResources&);
    template <typename PayloadCodecCallback>
    [[nodiscard]] bool uncompress_rpc_payload(const FRT_Values& params, PayloadCodecCallback payload_callback);
    template <typename MessageType>
    void encode_and_compress_rpc_payload(const MessageType& msg, FRT_Values& params);
    void RequestDone(FRT_RPCRequest* request) override;

    void handle_request_done_rpc_error(FRT_RPCRequest& req, const RpcRequestContext& req_ctx);
    void handle_request_done_decode_error(const RpcRequestContext& req_ctx,
                                          vespalib::stringref description);
    void create_and_dispatch_error_reply(api::StorageCommand& cmd, api::ReturnCode error);

    api::ReturnCode map_frt_error_to_storage_api_error(FRT_RPCRequest& req, const RpcRequestContext& req_ctx);
    api::ReturnCode make_no_address_for_service_error(const api::StorageMessageAddress& addr) const;
};

} // rpc
} // storage
