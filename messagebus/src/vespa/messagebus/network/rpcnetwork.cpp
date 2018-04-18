// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpcnetwork.h"
#include "rpcservicepool.h"
#include "rpcsendv1.h"
#include "rpcsendv2.h"
#include "rpctargetpool.h"
#include "rpcnetworkparams.h"
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/iprotocol.h>
#include <vespa/messagebus/tracelevel.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingnode.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/frt/supervisor.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".rpcnetwork");

using vespalib::make_string;
using namespace std::chrono_literals;

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
    ~SyncTask() = default;

    void await() {
        _gate.await();
    }

    void PerformTask() override {
        _gate.countDown();
    }
};

} // namespace <unnamed>

namespace mbus {

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
        vespalib::LockGuard guard(_lock);
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

RPCNetwork::TargetPoolTask::TargetPoolTask(FNET_Scheduler &scheduler, RPCTargetPool &pool)
    : FNET_Task(&scheduler),
      _pool(pool)
{
    ScheduleNow();
}

void
RPCNetwork::TargetPoolTask::PerformTask()
{
    _pool.flushTargets(false);
    Schedule(1.0);
}

RPCNetwork::RPCNetwork(const RPCNetworkParams &params) :
    _owner(nullptr),
    _ident(params.getIdentity()),
    _threadPool(std::make_unique<FastOS_ThreadPool>(128000, 0)),
    _transport(std::make_unique<FNET_Transport>()),
    _orb(std::make_unique<FRT_Supervisor>(_transport.get(), nullptr)),
    _scheduler(*_transport->GetScheduler()),
    _targetPool(std::make_unique<RPCTargetPool>(params.getConnectionExpireSecs())),
    _targetPoolTask(_scheduler, *_targetPool),
    _servicePool(std::make_unique<RPCServicePool>(*this, 4096)),
    _slobrokCfgFactory(std::make_unique<slobrok::ConfiguratorFactory>(params.getSlobrokConfig())),
    _mirror(std::make_unique<slobrok::api::MirrorAPI>(*_orb, *_slobrokCfgFactory)),
    _regAPI(std::make_unique<slobrok::api::RegisterAPI>(*_orb, *_slobrokCfgFactory)),
    _requestedPort(params.getListenPort()),
    _executor(std::make_unique<vespalib::ThreadStackExecutor>(4,65536)),
    _sendV1(std::make_unique<RPCSendV1>()),
    _sendV2(std::make_unique<RPCSendV2>()),
    _sendAdapters(),
    _compressionConfig(params.getCompressionConfig())
{
    _transport->SetDirectWrite(false);
    _transport->SetMaxInputBufferSize(params.getMaxInputBufferSize());
    _transport->SetMaxOutputBufferSize(params.getMaxOutputBufferSize());
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

    _sendV1->attach(*this);
    _sendV2->attach(*this);
    _sendAdapters[vespalib::Version(5)] = _sendV1.get();
    _sendAdapters[vespalib::Version(6, 149)] = _sendV2.get();

    FRT_ReflectionBuilder builder(_orb.get());
    builder.DefineMethod("mbus.getVersion", "", "s", true, FRT_METHOD(RPCNetwork::invoke), this);
    builder.MethodDesc("Retrieves the message bus version.");
    builder.ReturnDesc("version", "The message bus version.");
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
    if (!_orb->Listen(_requestedPort)) {
        return false;
    }
    if (!_transport->Start(_threadPool.get())) {
        return false;
    }
    return true;
}

vespalib::Executor &
RPCNetwork::getExecutor() {
    return *_executor;
}

bool
RPCNetwork::waitUntilReady(double seconds) const
{
    slobrok::api::SlobrokList brokerList;
    slobrok::Configurator::UP configurator = _slobrokCfgFactory->create(brokerList);
    bool hasConfig = false;
    for (uint32_t i = 0; i < seconds * 100; ++i) {
        if (configurator->poll()) {
            hasConfig = true;
        }
        if (_mirror->ready()) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    if (! hasConfig) {
        LOG(error, "failed to get config for slobroks in %d seconds", (int)seconds);
    } else if (! _mirror->ready()) {
        auto brokers = brokerList.logString();
        LOG(error, "mirror (of %s) failed to become ready in %d seconds", brokers.c_str(), (int)seconds);
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
                                 "The service must be having problems, or the routing configuration is wrong.",
                                 serviceName.c_str()));
    }
    RPCTarget::SP target = _targetPool->getTarget(*_orb, *ret);
    if ( ! target) {
        return Error(ErrorCode::CONNECTION_ERROR,
                     make_string("Failed to connect to service '%s'.", serviceName.c_str()));
    }
    ret->setTarget(target); // free by freeServiceAddress()
    recipient.setServiceAddress(IServiceAddress::UP(ret.release()));
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
    double timeout = ctx._msg.getTimeRemainingNow() / 1000.0;
    for (uint32_t i = 0, len = ctx._recipients.size(); i < len; ++i) {
        RoutingNode *&recipient = ctx._recipients[i];

        RPCServiceAddress &address = static_cast<RPCServiceAddress&>(recipient->getServiceAddress());
        LOG_ASSERT(address.hasTarget());

        address.getTarget().resolveVersion(timeout, ctx);
    }
}

void
RPCNetwork::send(RPCNetwork::SendContext &ctx)
{
    if (ctx._hasError) {
        replyError(ctx, ErrorCode::HANDSHAKE_FAILED, "An error occured while resolving version.");
    } else {
        uint64_t timeRemaining = ctx._msg.getTimeRemainingNow();
        Blob payload = _owner->getProtocol(ctx._msg.getProtocol())->encode(ctx._version, ctx._msg);
        RPCSendAdapter *adapter = getSendAdapter(ctx._version);
        if (adapter == nullptr) {
            replyError(ctx, ErrorCode::INCOMPATIBLE_VERSION,
                       make_string("Can not send to version '%s' recipient.", ctx._version.toString().c_str()));
        } else if (timeRemaining == 0) {
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
    _executor->sync();
    task.await();
}

void
RPCNetwork::shutdown()
{
    _transport->ShutDown(false);
    _threadPool->Close();
    _executor->shutdown();
    _executor->sync();
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
