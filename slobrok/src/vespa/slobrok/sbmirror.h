// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "imirrorapi.h"
#include "backoff.h"
#include "sblist.h"
#include <vespa/vespalib/util/gencnt.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/fnet/frt/invoker.h>
#include <atomic>

class FRT_Target;

namespace slobrok::api {

/**
 * @brief A MirrorAPI object is used to keep track of the services registered
 * with a slobrok cluster.
 *
 * Updates to the service repository are
 * fetched in the background. Lookups against this object is done
 * using an internal mirror of the service repository.
 **/
class MirrorAPI : public FNET_Task,
                  public FRT_IRequestWait,
                  public IMirrorAPI
{
public:
    /**
     * @brief vector of <string> pairs.
     *
     * Elements are connection specs, typically "tcp/foo.bar.com:42"
     **/
    typedef std::vector<std::string> StringList;

    /**
     * @brief Create a new MirrorAPI object using config
     *
     * uses the given Supervisor and config to create a MirrorAPI object.
     *
     * @param orb      the Supervisor to use
     * @param config   how to get the connect spec list
     **/
    MirrorAPI(FRT_Supervisor &orb, const ConfiguratorFactory & config);
    MirrorAPI(const MirrorAPI &) = delete;
    MirrorAPI &operator=(const MirrorAPI &) = delete;

    /**
     * @brief Clean up.
     **/
    ~MirrorAPI();

    // Inherit doc from IMirrorAPI.
    SpecList lookup(vespalib::stringref pattern) const override;

    // Inherit doc from IMirrorAPI.
    uint32_t updates() const override { return _updates.getAsInt(); }

    /**
     * @brief Ask if the MirrorAPI has got any useful information from
     * the Slobrok
     *
     * On application startup it is often useful to run the event loop
     * for some time until this functions returns true (or if it never
     * does, time out and tell the user there was no answer from any
     * Service Location Broker).
     *
     * @return true if the MirrorAPI object has
     * asked for updates from a Slobrok and got any answer back
     **/
    bool ready() const override;

private:
    using SpecMap = vespalib::hash_map<vespalib::string, vespalib::string>;
    /** from FNET_Task, polls slobrok **/
    void PerformTask() override;

    /** from FRT_IRequestWait **/
    void RequestDone(FRT_RPCRequest *req) override;

    void updateTo(SpecMap newSpecs, uint32_t newGen);

    bool handleIncrementalFetch();

    void handleReconfig();
    bool handleReqDone();
    void handleReconnect();
    void makeRequest();

    void reSched(double seconds);

    FRT_Supervisor          &_orb;
    mutable std::mutex       _lock;
    bool                     _reqPending;
    bool                     _scheduled;
    std::atomic<bool>        _reqDone;
    bool                     _logOnSuccess;
    SpecMap                  _specs;
    vespalib::GenCnt         _specsGen;
    vespalib::GenCnt         _updates;
    SlobrokList              _slobrokSpecs;
    Configurator::UP         _configurator;
    std::string              _currSlobrok;
    int                      _rpc_ms;
    BackOff                  _backOff;
    FRT_Target              *_target;
    FRT_RPCRequest          *_req;
};

} // namespace slobrok::api
