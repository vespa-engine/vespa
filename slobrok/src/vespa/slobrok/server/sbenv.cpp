// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reconfigurable_stateserver.h"
#include "sbenv.h"
#include "selfcheck.h"
#include "remote_check.h"
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <vespa/config/helper/configfetcher.h>
#include <thread>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".sbenv");

using namespace std::chrono_literals;

namespace slobrok {

namespace {

void
discard(std::vector<std::string> &vec, const std::string & val)
{
    uint32_t i = 0;
    uint32_t size = vec.size();
    while (i < size) {
        if (vec[i] == val) {
            std::swap(vec[i], vec[size - 1]);
            vec.pop_back();
            --size;
        } else {
            ++i;
        }
    }
    LOG_ASSERT(size == vec.size());
}


class ConfigTask : public FNET_Task
{
private:
    Configurator& _configurator;

    ConfigTask(const ConfigTask &);
    ConfigTask &operator=(const ConfigTask &);
public:
    ConfigTask(FNET_Scheduler *sched, Configurator& configurator);

    ~ConfigTask();
    void PerformTask() override;
};


ConfigTask::ConfigTask(FNET_Scheduler *sched, Configurator& configurator)
    : FNET_Task(sched),
      _configurator(configurator)
{
    Schedule(1.0);
}


ConfigTask::~ConfigTask()
{
    Kill();
}


void
ConfigTask::PerformTask()
{
    Schedule(1.0);
    LOG(spam, "checking for new config");
    try {
        _configurator.poll();
    } catch (std::exception &e) {
        LOG(warning, "ConfigTask: poll failed: %s", e.what());
        Schedule(10.0);
    }
}

} // namespace slobrok::<unnamed>

SBEnv::SBEnv(const ConfigShim &shim)
    : _transport(std::make_unique<FNET_Transport>()),
      _supervisor(std::make_unique<FRT_Supervisor>(_transport.get(), nullptr)),
      _configShim(shim),
      _configurator(shim.factory().create(*this)),
      _shuttingDown(false),
      _partnerList(),
      _me(),
      _rpcHooks(*this, _rpcsrvmap, _rpcsrvmanager),
      _selfchecktask(std::make_unique<SelfCheck>(getSupervisor()->GetScheduler(), _rpcsrvmap, _rpcsrvmanager)),
      _remotechecktask(std::make_unique<RemoteCheck>(getSupervisor()->GetScheduler(), _rpcsrvmap, _rpcsrvmanager, _exchanger)),
      _health(),
      _metrics(_rpcHooks, *_transport),
      _components(),
      _rpcsrvmanager(*this),
      _exchanger(*this, _rpcsrvmap),
      _rpcsrvmap()
{
    srandom(time(nullptr) ^ getpid());
    _rpcHooks.initRPC(getSupervisor());
}


SBEnv::~SBEnv()
{
    getTransport()->WaitFinished();
}

FNET_Scheduler *
SBEnv::getScheduler() {
    return _transport->GetScheduler();
}

void
SBEnv::shutdown()
{
    _shuttingDown = true;
    getTransport()->ShutDown(false);
}

void
SBEnv::resume()
{
    // nop
}

namespace {

std::string
createSpec(int port)
{
    if (port == 0) {
        return std::string();
    }
    std::ostringstream str;
    str << "tcp/";
    str << vespalib::HostName::get();
    str << ":";
    str << port;
    return str.str();
}

vespalib::string
toString(const std::vector<std::string> & v) {
    vespalib::asciistream os;
    os << "[" << '\n';
    for (const std::string & partner : v) {
        os << "    " << partner << '\n';
    }
    os << ']';
    return os.str();
}

} // namespace <unnamed>

int
SBEnv::MainLoop()
{
    if (! getSupervisor()->Listen(_configShim.portNumber())) {
        LOG(error, "unable to listen to port %d", _configShim.portNumber());
        EV_STOPPING("slobrok", "could not listen");
        return 1;
    } else {
        LOG(config, "listening on port %d", _configShim.portNumber());
    }

    std::string myspec = createSpec(_configShim.portNumber());

    _me = std::make_unique<ManagedRpcServer>(myspec.c_str(), myspec.c_str(), _rpcsrvmanager);

    std::unique_ptr<ReconfigurableStateServer> stateServer;
    if (_configShim.enableStateServer()) {
        stateServer = std::make_unique<ReconfigurableStateServer>(_configShim.configId(), _health, _metrics, _components);
    }

    try {
        _configurator->poll();
        ConfigTask configTask(getScheduler(), *_configurator);
        LOG(debug, "slobrok: starting main event loop");
        EV_STARTED("slobrok");
        getTransport()->Main();
        LOG(debug, "slobrok: main event loop done");
    } catch (vespalib::Exception &e) {
        LOG(error, "invalid config: %s", e.what());
        EV_STOPPING("slobrok", "invalid config");
        return 1;
    } catch (...) {
        LOG(error, "unknown exception while configuring");
        EV_STOPPING("slobrok", "unknown config exception");
        return 1;
    }
    EV_STOPPING("slobrok", "clean shutdown");
    return 0;
}


void
SBEnv::setup(const std::vector<std::string> &cfg)
{
    _partnerList = cfg;
    std::vector<std::string> oldList = _exchanger.getPartnerList();
    LOG(debug, "(re-)configuring. oldlist size %d, configuration list size %d",
        (int)oldList.size(),
        (int)cfg.size());
    for (uint32_t i = 0; i < cfg.size(); ++i) {
        std::string slobrok = cfg[i];
        discard(oldList, slobrok);
        if (slobrok != mySpec()) {
            OkState res = _rpcsrvmanager.addPeer(slobrok.c_str(), slobrok.c_str());
            if (!res.ok()) {
                LOG(warning, "could not add peer %s: %s", slobrok.c_str(),
                    res.errorMsg.c_str());
            } else {
                LOG(config, "added peer %s", slobrok.c_str());
            }
        }
    }
    for (uint32_t i = 0; i < oldList.size(); ++i) {
        OkState res = _rpcsrvmanager.removePeer(oldList[i].c_str(), oldList[i].c_str());
        if (!res.ok()) {
            LOG(warning, "could not remove peer %s: %s", oldList[i].c_str(),
                res.errorMsg.c_str());
        } else {
            LOG(config, "removed peer %s", oldList[i].c_str());
        }
    }
    int64_t curGen = _configurator->getGeneration();
    vespalib::ComponentConfigProducer::Config current("slobroks", curGen, "ok");
    _components.addConfig(current);
}

OkState
SBEnv::addPeer(const std::string &name, const std::string &spec)
{
    if (spec == mySpec()) {
        return OkState(FRTE_RPC_METHOD_FAILED, "cannot add my own spec as peer");
    }
    if (_partnerList.size() != 0) {
        for (const std::string & partner : _partnerList) {
            if (partner == spec) {
                return OkState(0, "already configured with peer");
            }
        }
        vespalib::string peers = toString(_partnerList);
        LOG(warning, "got addPeer with non-configured peer %s, check config consistency. configured peers = %s",
                     spec.c_str(), peers.c_str());
        return OkState(FRTE_RPC_METHOD_FAILED, "configured partner list does not contain peer. configured peers = " + peers);
    }
    return _rpcsrvmanager.addPeer(name.c_str(), spec.c_str());
}

OkState
SBEnv::removePeer(const std::string &name, const std::string &spec)
{
    if (spec == mySpec()) {
        return OkState(FRTE_RPC_METHOD_FAILED, "cannot remove my own spec as peer");
    }
    for (const std::string & partner : _partnerList) {
        if (partner == spec) {
            return OkState(FRTE_RPC_METHOD_FAILED, "configured partner list contains peer, cannot remove");
        }
    }
    return _rpcsrvmanager.removePeer(name.c_str(), spec.c_str());
}

} // namespace slobrok
