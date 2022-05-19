// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "backoff.h"
#include "sblist.h"
#include "cfg.h"
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/invokable.h>
#include <atomic>

class FRT_Target;

namespace slobrok::api {

/**
 * @brief A RegisterAPI object is used to register and unregister
 * services with a slobrok cluster.
 *
 * The register/unregister operations performed against this object
 * are stored in a todo list that will be performed asynchronously
 * against the slobrok cluster as soon as possible.
 **/
class RegisterAPI : public FNET_Task,
                    public FRT_IRequestWait
{
public:
    /**
     * @brief Create a new RegisterAPI using the given Supervisor and config.
     *
     * @param orb the Supervisor to use
     * @param config used to obtain the slobrok connect spec list
     **/
    RegisterAPI(FRT_Supervisor &orb, const ConfiguratorFactory & config);

    /**
     * @brief Clean up (deregisters all service names).
     **/
    ~RegisterAPI();

    /**
     * @brief Register a service with the slobrok cluster.
     * @param name service name to register
     **/
    void registerName(vespalib::stringref name);

    /**
     * @brief Unregister a service with the slobrok cluster
     * @param name service name to unregister
     **/
    void unregisterName(vespalib::stringref name);

    /**
     * @brief Check progress
     *
     * @return true if there are outstanding registration requests
     **/
    bool busy() const { return _busy.load(std::memory_order_relaxed); }

private:
    class RPCHooks: public FRT_Invokable
    {
    private:
        RegisterAPI  &_owner;
        void rpc_listNamesServed(FRT_RPCRequest *req);
        void rpc_notifyUnregistered(FRT_RPCRequest *req);
    public:
        RPCHooks(RegisterAPI &owner);
        ~RPCHooks();
    };
    friend class RPCHooks;

    RegisterAPI(const RegisterAPI &);
    RegisterAPI &operator=(const RegisterAPI &);

    bool match(const char *name, const char *pattern);

    /** from FNET_Task, poll slobrok **/
    void PerformTask() override;
    void handleReqDone();   // implementation detail of PerformTask
    void handleReconnect(); // implementation detail of PerformTask
    void handlePending();   // implementation detail of PerformTask

    /** from FRT_IRequestWait **/
    void RequestDone(FRT_RPCRequest *req) override;

    FRT_Supervisor          &_orb;
    RPCHooks                 _hooks;
    std::mutex               _lock;
    std::atomic<bool>        _reqDone;
    bool                     _logOnSuccess;
    std::atomic<bool>        _busy;
    SlobrokList              _slobrokSpecs;
    Configurator::UP         _configurator;
    vespalib::string         _currSlobrok;
    uint32_t                 _idx;
    BackOff                  _backOff;
    std::vector<vespalib::string> _names;   // registered service names
    std::vector<vespalib::string> _pending; // pending service name registrations
    std::vector<vespalib::string> _unreg;   // pending service name unregistrations
    FRT_Target              *_target;
    FRT_RPCRequest          *_req;
};

} // namespace slobrok::api
