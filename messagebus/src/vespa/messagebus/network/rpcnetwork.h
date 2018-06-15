// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "inetwork.h"
#include "rpcsendadapter.h"
#include "rpctarget.h"
#include "identity.h"
#include <vespa/messagebus/blob.h>
#include <vespa/messagebus/blobref.h>
#include <vespa/messagebus/message.h>
#include <vespa/messagebus/reply.h>
#include <vespa/slobrok/imirrorapi.h>
#include <vespa/vespalib/component/versionspecification.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/fnet/frt/invokable.h>

class FNET_Transport;

namespace slobrok {
    namespace api { class RegisterAPI; }
    class ConfiguratorFactory;
}

namespace mbus {

class RPCServicePool;
class RPCTargetPool;
class RPCNetworkParams;
class RPCServiceAddress;

/**
 * Network implementation based on RPC. This class is responsible for
 * keeping track of services and for sending messages to services.
 **/
class RPCNetwork : public INetwork,
                   public FRT_Invokable {
private:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    struct SendContext : public RPCTarget::IVersionHandler {
        vespalib::Lock            _lock;
        RPCNetwork               &_net;
        const Message            &_msg;
        uint32_t                  _traceLevel;
        std::vector<RoutingNode*> _recipients;
        bool                      _hasError;
        uint32_t                  _pending;
        vespalib::Version         _version;

        SendContext(RPCNetwork &net, const Message &msg, const std::vector<RoutingNode*> &recipients);
        void handleVersion(const vespalib::Version *version) override;
    };

    struct TargetPoolTask : public FNET_Task {
        RPCTargetPool &_pool;

        TargetPoolTask(FNET_Scheduler &scheduler, RPCTargetPool &pool);
        void PerformTask() override;
    };

    using SendAdapterMap = std::map<vespalib::Version, RPCSendAdapter*>;

    INetworkOwner                                  *_owner;
    Identity                                        _ident;
    std::unique_ptr<FastOS_ThreadPool>              _threadPool;
    std::unique_ptr<FNET_Transport>                 _transport;
    std::unique_ptr<FRT_Supervisor>                 _orb;
    FNET_Scheduler                                 &_scheduler;
    std::unique_ptr<RPCTargetPool>                  _targetPool;
    TargetPoolTask                                  _targetPoolTask;
    std::unique_ptr<RPCServicePool>                 _servicePool;
    std::unique_ptr<slobrok::ConfiguratorFactory>   _slobrokCfgFactory;
    std::unique_ptr<slobrok::api::IMirrorAPI>       _mirror;
    std::unique_ptr<slobrok::api::RegisterAPI>      _regAPI;
    int                                             _requestedPort;
    std::unique_ptr<vespalib::ThreadStackExecutor>  _executor;
    std::unique_ptr<RPCSendAdapter>                 _sendV1;
    std::unique_ptr<RPCSendAdapter>                 _sendV2;
    SendAdapterMap                                  _sendAdapters;
    CompressionConfig                               _compressionConfig;
    bool                                            _allowDispatchForEncode;
    bool                                            _allowDispatchForDecode;


    /**
     * Resolves and assigns a service address for the given recipient using the
     * given address. This is called by the {@link
     * #allocServiceAddress(RoutingNode)} method. The target allocated here is
     * released when the routing node calls {@link
     * #freeServiceAddress(RoutingNode)}.
     *
     * @param recipient   The recipient to assign the service address to.
     * @param serviceName The name of the service to resolve.
     * @return Any error encountered, or ErrorCode::NONE.
     */
    Error resolveServiceAddress(RoutingNode &recipient, const string &serviceName);

    /**
     * This method is a callback invoked after {@link #send(Message, List)} once
     * the version of all recipients have been resolved. If all versions were
     * resolved ahead of time, this method is invoked by the same thread as the
     * former.  If not, this method is invoked by the network thread during the
     * version callback.
     *
     * @param ctx All the required send-data.
     */
    void send(SendContext &ctx);

protected:
    /**
     * Returns the version of this network. This gets called when the
     * "mbus.getVersion" method is invoked on this network, and is separated
     * into its own function so that unit tests can override it to simulate
     * other versions than current.
     *
     * @return The version to claim to be.
     */
    virtual const vespalib::Version &getVersion() const;

    /**
     * The network uses a cache of RPC targets (see {@link RPCTargetPool}) that
     * allows it to save time by reusing open connections. It works by keeping a
     * set of the most recently used targets open. Calling this method forces
     * all unused connections to close immediately.
     */
    void flushTargetPool();

public:
    /**
     * Create an RPCNetwork. The servicePrefix is combined with session names to
     * create service names. If the service prefix is 'a/b' and the session name
     * is 'c', the resulting service name that identifies the session on the
     * message bus will be 'a/b/c'
     *
     * @param params A complete set of parameters.
     */
    RPCNetwork(const RPCNetworkParams &params);

    /**
     * Destruct
     **/
    ~RPCNetwork() override;

    /**
     * Obtain the owner of this network. This method may only be invoked after
     * the network has been attached to its owner.
     *
     * @return network owner
     **/
    INetworkOwner &getOwner() { return *_owner; }

    /**
     * Returns the identity of this network.
     *
     * @return The identity.
     */
    const Identity &getIdentity() const { return _ident; }

    /**
     * Obtain the port number this network is listening to. This method will
     * return 0 until the start method has been invoked.
     *
     * @return port number
     **/
    int getPort() const;

    /**
     * Allocate a new rpc request object. The caller of this method gets the
     * ownership of the returned request.
     *
     * @return a new rpc request
     **/
    FRT_RPCRequest *allocRequest();

    /**
     * Returns an RPC target for the given service address.
     *
     * @param address The address for which to return a target.
     * @return The target to send to.
     */
    RPCTarget::SP getTarget(const RPCServiceAddress &address);

    /**
     * Obtain a reference to the internal scheduler. This will be mostly used
     * for testing.
     *
     * @return internal scheduler
     **/
    FNET_Scheduler &getScheduler() { return _scheduler; }

    /**
     * Obtain a reference to the internal supervisor. This is used by
     * the request adapters to register FRT methods.
     *
     * @return The supervisor.
     */
    FRT_Supervisor &getSupervisor() { return *_orb; }

    /**
     * Deliver an error reply to the recipients of a {@link SendContext} in a
     * way that avoids entanglement.
     *
     * @param ctx     The send context that contains the recipient data.
     * @param errCode The error code to return.
     * @param errMsg  The error string to return.
     */
    void replyError(const SendContext &ctx, uint32_t errCode, const string &errMsg);

    /**
     * Determines and returns the send adapter that is compatible with the given
     * version. If no adapter can be found, this method returns null.
     *
     * @param version The version for which to return an adapter.
     * @return The compatible adapter.
     */
    RPCSendAdapter *getSendAdapter(const vespalib::Version &version);

    void attach(INetworkOwner &owner) override;
    const string getConnectionSpec() const override;
    bool start() override;
    bool waitUntilReady(double seconds) const override;
    void registerSession(const string &session) override;
    void unregisterSession(const string &session) override;
    bool allocServiceAddress(RoutingNode &recipient) override;
    void freeServiceAddress(RoutingNode &recipient) override;
    void send(const Message &msg, const std::vector<RoutingNode*> &recipients) override;
    void sync() override;
    void shutdown() override;
    void postShutdownHook() override;
    const slobrok::api::IMirrorAPI &getMirror() const override;
    CompressionConfig getCompressionConfig() { return _compressionConfig; }
    void invoke(FRT_RPCRequest *req);
    vespalib::Executor & getExecutor() const { return *_executor; }
    bool allowDispatchForEncode() const { return _allowDispatchForEncode; }
    bool allowDispatchForDecode() const { return _allowDispatchForDecode; }

};

} // namespace mbus
