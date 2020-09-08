// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpc_target.h"
#include <vespa/fnet/frt/invokable.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/vespalib/stllike/string.h>
#include <atomic>
#include <functional>
#include <memory>

class FRT_RPCRequest;
class FRT_Target;

namespace document { class DocumentTypeRepo; }
namespace documentapi { class LoadTypeSet; }

namespace storage {

class MessageEnqueuer;

namespace api {
class StorageCommand;
class StorageMessage;
class StorageMessageAddress;
class StorageReply;
}

namespace rpc {

class SharedRpcResources;
class CachingRpcTargetResolver;

class StorageApiRpcService : public FRT_Invokable, public FRT_IRequestWait {
    MessageEnqueuer&    _message_enqueuer;
    SharedRpcResources& _rpc_resources;
public:
    StorageApiRpcService(MessageEnqueuer& messageEnqueuer,
                         SharedRpcResources& rpc_resources,
                         // TODO temporary!
                         std::function<std::shared_ptr<const document::DocumentTypeRepo>()> doctype_repo_func,
                         std::function<std::shared_ptr<documentapi::LoadTypeSet>()> loadtype_set_func);
    ~StorageApiRpcService() override;

    void RPC_rpc_v1_send(FRT_RPCRequest* req);
    void encode_rpc_v1_response(FRT_RPCRequest& request, const api::StorageReply& reply);
    void send_rpc_v1_request(std::shared_ptr<api::StorageCommand> cmd);
private:
    // TODO dedupe
    void detach_and_forward_to_enqueuer(std::shared_ptr<api::StorageMessage> cmd, FRT_RPCRequest* req);

    struct RpcRequestContext {
        std::shared_ptr<api::StorageCommand> _originator_cmd;
        std::chrono::nanoseconds _timeout;

        RpcRequestContext(std::shared_ptr<api::StorageCommand> cmd, std::chrono::nanoseconds timeout)
            : _originator_cmd(std::move(cmd)),
              _timeout(timeout)
        {}
    };

    std::function<std::shared_ptr<const document::DocumentTypeRepo>()> _doctype_repo_func;
    std::function<std::shared_ptr<documentapi::LoadTypeSet>()> _loadtype_set_func;
    std::unique_ptr<CachingRpcTargetResolver> _target_resolver;

    void register_server_methods(SharedRpcResources&);
    template <typename PayloadCodecCallback>
    void uncompress_rpc_payload(const FRT_Values& params, PayloadCodecCallback payload_callback);
    template <typename MessageType>
    void encode_and_compress_rpc_payload(const MessageType& msg, FRT_Values& params);
    void RequestDone(FRT_RPCRequest* request) override;
};

} // rpc
} // storage
