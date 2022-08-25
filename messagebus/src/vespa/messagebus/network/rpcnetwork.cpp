// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpcnetwork.h"
#include "rpcservicepool.h"
#include "rpcsendv2.h"
#include "rpctargetpool.h"
#include "rpcnetworkparams.h"
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/transport.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/iprotocol.h>
#include <vespa/messagebus/routing/routingnode.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/fastos/thread.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".rpcnetwork");

using vespalib::make_string;
using namespace std::chrono_literals;

namespace mbus {

namespace {

/**
 * Implements a helper class for {@link RPCNetwork#sync()}. It provides a
 * blocking method {@link #await()} that will wait until the internal state
 * of this object is set to 'done'. By scheduling this task in the network
 * thread and then calling this method, we achieve handshaking with the network
 * thread.
 */
class SyncTask : public FNET_Task {
private:
    vespalib::Gate _gate;

public:
    SyncTask(FNET_Scheduler &s) :
        FNET_Task(&s),
        _gate() {
        ScheduleNow();
    }
    ~SyncTask() override {
        Kill();
    }

    void await() {
        _gate.await();
    }

    void PerformTask() override {
        _gate.countDown();
    }
};

struct TargetPoolTask : public FNET_Task {
    RPCTargetPool &_pool;

    TargetPoolTask(FNET_Scheduler &scheduler, RPCTargetPool &pool)
        : FNET_Task(&scheduler),
        _pool(pool)
    {
        ScheduleNow();
    }
    ~TargetPoolTask() override {
        Kill();
    }
    void PerformTask() override {
        _pool.flushTargets(false);
        Schedule(1.0);
    }
};

fnet::TransportConfig
toFNETConfig(const RPCNetworkParams & params) {
    return fnet::TransportConfig(params.getNumNetworkThreads())
              .maxInputBufferSize(params.getMaxInputBufferSize())
              .maxOutputBufferSize(params.getMaxOutputBufferSize())
              .tcpNoDelay(params.getTcpNoDelay())
              .events_before_wakeup(params.events_before_wakeup());
}

}

RPCNetwork::SendContext::SendContext(RPCNetwork &net, const Message &msg,
                                     const std::vector<RoutingNode*> &recipients)
    : _net(net),
      _msg(msg),
      _traceLevel(msg.getTrace().getLevel()),
      _recipients(recipients),
      _hasError(false),
      _pending(_recipients.size()),
      _version(_net.getVersion())
{ }

void
RPCNetwork::SendContext::handleVersion(const vespalib::Version *version)
{
    bool shouldSend = false;
    {
        std::lock_guard guard(_lock);
        if (version == nullptr) {
            _hasError = true;
        } else if (*version < _version) {
            _version = *version;
        }
        if (--_pending == 0) {
            shouldSend = true;
        }
    }
    if (shouldSend) {
        _net.send(*this);
        delete this;
    }
}

RPCNetwork::RPCNetwork(const RPCNetworkParams &params) :
    _owner(nullptr),
    _ident(params.getIdentity()),
    _threadPool(std::make_unique<FastOS_ThreadPool>(128_Ki, 0)),
    _transport(std::make_unique<FNET_Transport>(toFNETConfig(params))),
    _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
    _scheduler(*_transport->GetScheduler()),
    _slobrokCfgFactory(std::make_unique<slobrok::ConfiguratorFactory>(params.getSlobrokConfig())),
    _mirror(std::make_unique<slobrok::api::MirrorAPI>(*_orb, *_slobrokCfgFactory)),
    _regAPI(std::make_unique<slobrok::api::RegisterAPI>(*_orb, *_slobrokCfgFactory)),
    _requestedPort(params.getListenPort()),
    _targetPool(std::make_unique<RPCTargetPool>(params.getConnectionExpireSecs(), params.getNumRpcTargets())),
    _targetPoolTask(std::make_unique<TargetPoolTask>(_scheduler, *_targetPool)),
    _servicePool(std::make_unique<RPCServicePool>(*_mirror, 4_Ki)),
    _sendV2(std::make_unique<RPCSendV2>()),
    _sendAdapters(),
    _compressionConfig(params.getCompressionConfig()),
    _required_capabilities(params.required_capabilities())
{
}

RPCNetwork::~RPCNetwork()
{
    shutdown();
}

FRT_RPCRequest *
RPCNetwork::allocRequest()
{
    return _orb->AllocRPCRequest();
}

RPCTarget::SP
RPCNetwork::getTarget(const RPCServiceAddress &address)
{
    return _targetPool->getTarget(*_orb, address);
}

void
RPCNetwork::replyError(const SendContext &ctx, uint32_t errCode, const string &errMsg)
{
    for (RoutingNode * rnode : ctx._recipients) {
        Reply::UP reply(new EmptyReply());
        reply->setTrace(Trace(ctx._traceLevel));
        reply->addError(Error(errCode, errMsg));
        _owner->deliverReply(std::move(reply), *rnode);
    }
}

int RPCNetwork::getPort() const { return _orb->GetListenPort(); }


void
RPCNetwork::flushTargetPool()
{
    _targetPool->flushTargets(true);
}

const vespalib::Version &
RPCNetwork::getVersion() const
{
    return vespalib::Vtag::currentVersion;
}

void
RPCNetwork::attach(INetworkOwner &owner)
{
    LOG_ASSERT(_owner == nullptr);
    _owner = &owner;

    _sendV2->attach(*this, _required_capabilities);
    _sendAdapters[vespalib::Version(6, 149)] = _sendV2.get();

    FRT_ReflectionBuilder builder(_orb.get());
    builder.DefineMethod("mbus.getVersion", "", "s", FRT_METHOD(RPCNetwork::invoke), this);
    builder.MethodDesc("Retrieves the message bus version.");
    builder.ReturnDesc("version", "The message bus version.");
    builder.RequestAccessFilter(FRT_RequireCapabilities::of(_required_capabilities));
}

void
RPCNetwork::invoke(FRT_RPCRequest *req)
{
    req->GetReturn()->AddString(getVersion().toString().c_str());
}

const string
RPCNetwork::getConnectionSpec() const
{
    return make_string("tcp/%s:%d", _ident.getHostname().c_str(), _orb->GetListenPort());
}

RPCSendAdapter *
RPCNetwork::getSendAdapter(const vespalib::Version &version)
{
    if (version < _sendAdapters.begin()->first) {
        return nullptr;
    }
    return (--_sendAdapters.upper_bound(version))->second;
}

bool
RPCNetwork::start()
{
    if (!_transport->Start(_threadPool.get())) {
        return false;
    }
    if (!_orb->Listen(_requestedPort)) {
        return false;
    }
    return true;
}

bool
RPCNetwork::waitUntilReady(duration timeout) const
{
    slobrok::api::SlobrokList brokerList;
    slobrok::Configurator::UP configurator = _slobrokCfgFactory->create(brokerList);
    bool hasConfig = false;
    for (int64_t i = 0; i < vespalib::count_ms(timeout)/10; ++i) {
        if (configurator->poll()) {
            hasConfig = true;
        }
        if (_mirror->ready()) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    if (! hasConfig) {
        LOG(error, "failed to get config for slobroks in %2.2f seconds", vespalib::to_s(timeout));
    } else if (! _mirror->ready()) {
        auto brokers = brokerList.logString();
        LOG(error, "mirror (of %s) failed to become ready in %2.2f seconds", brokers.c_str(), vespalib::to_s(timeout));
    }
    return false;
}

void
RPCNetwork::registerSession(const string &session)
{
    if (_ident.getServicePrefix().empty()) {
        LOG(warning, "The session (%s) will not be registered in the Slobrok since this network has no identity.",
            session.c_str());
        return;
    }
    string name = _ident.getServicePrefix();
    name += "/";
    name += session;
    _regAPI->registerName(name);
}

void
RPCNetwork::unregisterSession(const string &session)
{
    if (_ident.getServicePrefix().empty()) {
        return;
    }
    if (getPort() <= 0) {
        return;
    }
    string name = _ident.getServicePrefix();
    name += "/";
    name += session;
    _regAPI->unregisterName(name);
}

bool
RPCNetwork::allocServiceAddress(RoutingNode &recipient)
{
    const Hop &hop = recipient.getRoute().getHop(0);
    string service = hop.getServiceName();
    Error error = resolveServiceAddress(recipient, service);
    if (error.getCode() == ErrorCode::NONE) {
        return true; // service address resolved
    }
    recipient.setError(error);
    return false; // service adddress not resolved
}

Error
RPCNetwork::resolveServiceAddress(RoutingNode &recipient, const string &serviceName)
{
    RPCServiceAddress::UP ret = _servicePool->resolve(serviceName);
    if ( ! ret) {
        return Error(ErrorCode::NO_ADDRESS_FOR_SERVICE,
                     make_string("The address of service '%s' could not be resolved. It is not currently "
                                 "registered with the Vespa name server. "
                                 "The service must be having problems, or the routing configuration is wrong. "
                                 "Address resolution attempted from host '%s'",
                                 serviceName.c_str(), getIdentity().getHostname().c_str()));
    }
    RPCTarget::SP target = _targetPool->getTarget(*_orb, *ret);
    if ( ! target) {
        return Error(ErrorCode::CONNECTION_ERROR,
                     make_string("Failed to connect to service '%s' from host '%s'.",
                                 serviceName.c_str(), getIdentity().getHostname().c_str()));
    }
    ret->setTarget(std::move(target)); // free by freeServiceAddress()
    recipient.setServiceAddress(std::move(ret));
    return Error();
}

void
RPCNetwork::freeServiceAddress(RoutingNode &recipient)
{
    recipient.setServiceAddress(IServiceAddress::UP());
}

void
RPCNetwork::send(const Message &msg, const std::vector<RoutingNode*> &recipients)
{
    SendContext &ctx = *(new SendContext(*this, msg, recipients)); // deletes self
    duration timeout = ctx._msg.getTimeRemainingNow();
    for (uint32_t i = 0, len = ctx._recipients.size(); i < len; ++i) {
        RoutingNode *&recipient = ctx._recipients[i];

        RPCServiceAddress &address = static_cast<RPCServiceAddress&>(recipient->getServiceAddress());
        LOG_ASSERT(address.hasTarget());

        address.getTarget().resolveVersion(timeout, ctx);
    }
}

namespace {

void
emit_recipient_endpoint(vespalib::asciistream& stream, const RoutingNode& recipient) {
    if (recipient.hasServiceAddress()) {
        // At this point the service addresses _should_ be RPCServiceAddress instances,
        // but stay on the safe side of the tracks anyway.
        const auto* rpc_addr = dynamic_cast<const RPCServiceAddress*>(&recipient.getServiceAddress());
        if (rpc_addr) {
            stream << rpc_addr->getServiceName() << " at " << rpc_addr->getConnectionSpec();
        } else {
            stream << "<non-RPC service address>";
        }
    } else {
        stream << "<unknown service address>";
    }
}

}

vespalib::string
RPCNetwork::buildRecipientListString(const SendContext& ctx) {
    vespalib::asciistream s;
    bool first = true;
    for (const auto* recipient : ctx._recipients) {
        if (!first) {
            s << ", ";
        }
        first = false;
        emit_recipient_endpoint(s, *recipient);
    }
    return s.str();
}

void
RPCNetwork::send(RPCNetwork::SendContext &ctx)
{
    if (ctx._hasError) {
        replyError(ctx, ErrorCode::HANDSHAKE_FAILED,
                make_string("An error occurred while resolving version of recipient(s) [%s] from host '%s'.",
                            buildRecipientListString(ctx).c_str(), getIdentity().getHostname().c_str()));
    } else {
        duration timeRemaining = ctx._msg.getTimeRemainingNow();
        Blob payload = _owner->getProtocol(ctx._msg.getProtocol())->encode(ctx._version, ctx._msg);
        RPCSendAdapter *adapter = getSendAdapter(ctx._version);
        if (adapter == nullptr) {
            replyError(ctx, ErrorCode::INCOMPATIBLE_VERSION,
                       make_string("Can not send to version '%s' recipient.", ctx._version.toString().c_str()));
        } else if (timeRemaining == 0ms) {
            replyError(ctx, ErrorCode::TIMEOUT, "Aborting transmission because zero time remains.");
        } else if (payload.size() == 0) {
            replyError(ctx, ErrorCode::ENCODE_ERROR,
                       make_string("Protocol '%s' failed to encode message.", ctx._msg.getProtocol().c_str()));
        } else if (ctx._recipients.size() == 1) {
            adapter->sendByHandover(*ctx._recipients.front(), ctx._version, std::move(payload), timeRemaining);
        } else {
            for (auto & recipient : ctx._recipients) {
                adapter->send(*recipient, ctx._version, payload, timeRemaining);
            }
        }
    }
}

void
RPCNetwork::sync()
{
    SyncTask task(_scheduler);
    task.await();
}

void
RPCNetwork::shutdown()
{
    // Unschedule any pending target pool flush task that may race with shutdown target flushing
    _scheduler.Kill(_targetPoolTask.get());
    _transport->ShutDown(true);
    _threadPool->Close();
}

void
RPCNetwork::postShutdownHook()
{
    _scheduler.CheckTasks();
}

const slobrok::api::IMirrorAPI &
RPCNetwork::getMirror() const
{
    return *_mirror;
}

} // namespace mbus
