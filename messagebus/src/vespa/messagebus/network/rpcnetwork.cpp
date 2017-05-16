// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "inetworkowner.h"
#include "rpcnetwork.h"
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/iprotocol.h>
#include <vespa/messagebus/tracelevel.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingnode.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/log/log.h>

LOG_SETUP(".rpcnetwork");

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
    ~SyncTask() {}

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
                                     const std::vector<RoutingNode*> &recipients) :
    _net(net),
    _msg(msg),
    _traceLevel(msg.getTrace().getLevel()),
    _recipients(recipients),
    _hasError(false),
    _pending(_recipients.size()),
    _version(_net.getVersion())
{
    // empty
}

void
RPCNetwork::SendContext::handleVersion(const vespalib::Version *version)
{
    bool shouldSend = false;
    {
        vespalib::LockGuard guard(_lock);
        if (version == NULL) {
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

RPCNetwork::TargetPoolTask::TargetPoolTask(
        FNET_Scheduler &scheduler,
        RPCTargetPool &pool) :
    FNET_Task(&scheduler),
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
    _owner(0),
    _ident(params.getIdentity()),
    _threadPool(128000, 0),
    _transport(),
    _orb(&_transport, NULL),
    _scheduler(*_transport.GetScheduler()),
    _targetPool(params.getConnectionExpireSecs()),
    _targetPoolTask(_scheduler, _targetPool),
    _servicePool(*this, 4096),
    _slobrokCfgFactory(params.getSlobrokConfig()),
    _mirror(std::make_unique<slobrok::api::MirrorAPI>(_orb, _slobrokCfgFactory)),
    _regAPI(std::make_unique<slobrok::api::RegisterAPI>(_orb, _slobrokCfgFactory)),
    _oosManager(_orb, *_mirror, params.getOOSServerPattern()),
    _requestedPort(params.getListenPort()),
    _sendV1(),
    _sendAdapters()
{
    _transport.SetDirectWrite(false);
    _transport.SetMaxInputBufferSize(params.getMaxInputBufferSize());
    _transport.SetMaxOutputBufferSize(params.getMaxOutputBufferSize());
}

RPCNetwork::~RPCNetwork()
{
    shutdown();
}

FRT_RPCRequest *
RPCNetwork::allocRequest()
{
    return _orb.AllocRPCRequest();
}

RPCTarget::SP
RPCNetwork::getTarget(const RPCServiceAddress &address)
{
    return _targetPool.getTarget(_orb, address);
}

void
RPCNetwork::replyError(const SendContext &ctx, uint32_t errCode,
                       const string &errMsg)
{
    for (std::vector<RoutingNode*>::const_iterator it = ctx._recipients.begin();
         it != ctx._recipients.end(); ++it)
    {
        Reply::UP reply(new EmptyReply());
        reply->setTrace(Trace(ctx._traceLevel));
        reply->addError(Error(errCode, errMsg));
        _owner->deliverReply(std::move(reply), **it);
    }
}

void
RPCNetwork::flushTargetPool()
{
    _targetPool.flushTargets(true);
}

const vespalib::Version &
RPCNetwork::getVersion() const
{
    return vespalib::Vtag::currentVersion;
}

void
RPCNetwork::attach(INetworkOwner &owner)
{
    LOG_ASSERT(_owner == 0);
    _owner = &owner;

    _sendV1.attach(*this);
    _sendAdapters.insert(SendAdapterMap::value_type(vespalib::VersionSpecification(5), &_sendV1));
    _sendAdapters.insert(SendAdapterMap::value_type(vespalib::VersionSpecification(6), &_sendV1));

    FRT_ReflectionBuilder builder(&_orb);
    builder.DefineMethod("mbus.getVersion", "", "s", true,
                         FRT_METHOD(RPCNetwork::invoke), this);
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
    return vespalib::make_vespa_string("tcp/%s:%d", _ident.getHostname().c_str(), _orb.GetListenPort());
}

RPCSendAdapter *
RPCNetwork::getSendAdapter(const vespalib::Version &version)
{
    for (SendAdapterMap::iterator it = _sendAdapters.begin();
         it != _sendAdapters.end(); ++it)
    {
        if (it->first.matches(version)) {
            return it->second;
        }
    }
    return NULL;
}

bool
RPCNetwork::start()
{
    if (!_orb.Listen(_requestedPort)) {
        return false;
    }
    if (!_transport.Start(&_threadPool)) {
        return false;
    }
    return true;
}



bool
RPCNetwork::waitUntilReady(double seconds) const
{
    slobrok::api::SlobrokList brokerList;
    slobrok::Configurator::UP configurator = _slobrokCfgFactory.create(brokerList);
    bool hasConfig = false;
    for (uint32_t i = 0; i < seconds * 100; ++i) {
        if (configurator->poll()) {
            hasConfig = true;
        }
        if (_mirror->ready() && _oosManager.isReady()) {
            return true;
        }
        FastOS_Thread::Sleep(10);
    }
    if (! hasConfig) {
        LOG(error, "failed to get config for slobroks in %d seconds", (int)seconds);
    } else if (! _mirror->ready()) {
        std::string brokers = brokerList.logString();
        LOG(error, "mirror (of %s) failed to become ready in %d seconds",
            brokers.c_str(), (int)seconds);
    } else if (! _oosManager.isReady()) {
        LOG(error, "OOS manager failed to become ready in %d seconds", (int)seconds);
    }
    return false;
}

void
RPCNetwork::registerSession(const string &session)
{
    if (_ident.getServicePrefix().size() == 0) {
        LOG(warning, "The session (%s) will not be registered"
            "in the Slobrok since this network has no identity.",
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
    if (_ident.getServicePrefix().size() == 0) {
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
    if (_oosManager.isOOS(serviceName)) {
        return Error(ErrorCode::SERVICE_OOS,
                     vespalib::make_vespa_string("The service '%s' has been marked as out of service.",
                                           serviceName.c_str()));
    }
    RPCServiceAddress::UP ret = _servicePool.resolve(serviceName);
    if (ret.get() == NULL) {
        return Error(ErrorCode::NO_ADDRESS_FOR_SERVICE,
                     vespalib::make_vespa_string("The address of service '%s' could not be resolved. It is not currently "
                                           "registered with the Vespa name server. "
                                           "The service must be having problems, or the routing configuration is wrong.",
                                           serviceName.c_str()));
    }
    RPCTarget::SP target = _targetPool.getTarget(_orb, *ret);
    if (target.get() == NULL) {
        return Error(ErrorCode::CONNECTION_ERROR,
                     vespalib::make_vespa_string("Failed to connect to service '%s'.",
                                           serviceName.c_str()));
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
        LOG_ASSERT(recipient != NULL);

        RPCServiceAddress &address = static_cast<RPCServiceAddress&>(recipient->getServiceAddress());
        LOG_ASSERT(address.hasTarget());

        address.getTarget().resolveVersion(timeout, ctx);
    }
}

void
RPCNetwork::send(RPCNetwork::SendContext &ctx)
{
    if (ctx._hasError) {
        replyError(ctx, ErrorCode::HANDSHAKE_FAILED,
                   "An error occured while resolving version.");
    } else {
        uint64_t timeRemaining = ctx._msg.getTimeRemainingNow();
        Blob payload = _owner->getProtocol(ctx._msg.getProtocol())->encode(ctx._version, ctx._msg);
        RPCSendAdapter *adapter = getSendAdapter(ctx._version);
        if (adapter == NULL) {
            replyError(ctx, ErrorCode::INCOMPATIBLE_VERSION,
                       vespalib::make_vespa_string(
                               "Can not send to version '%s' recipient.",
                               ctx._version.toString().c_str()));
        } else if (timeRemaining == 0) {
            replyError(ctx, ErrorCode::TIMEOUT,
                       "Aborting transmission because zero time remains.");
        } else if (payload.size() == 0) {
            replyError(ctx, ErrorCode::ENCODE_ERROR,
                       vespalib::make_vespa_string(
                               "Protocol '%s' failed to encode message.",
                               ctx._msg.getProtocol().c_str()));
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
    _transport.ShutDown(false);
    _threadPool.Close();
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

