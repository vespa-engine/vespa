// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/frt.h>
#include <vespa/vespalib/util/gencnt.h>
#include <vespa/vespalib/util/sync.h>
#include "backoff.h"
#include "sblist.h"
#include "cfg.h"
#include <string>
#include <vector>

namespace slobrok {
namespace api {

/**
 * @brief Defines an interface for the name server lookup.
 **/
class IMirrorAPI {
protected:
    static bool match(const char *name, const char *pattern);

public:
    /**
     * @brief Release any allocated resources.
     **/
    virtual ~IMirrorAPI() { }

    /**
     * @brief pair of <name, connectionspec>.
     *
     * The first element of pair is a string containing the service name.
     * The second is the connection spec, typically "tcp/foo.bar.com:42"
     **/
    typedef std::pair<std::string, std::string> Spec;

    /**
     * @brief vector of <name, connectionspec> pairs.
     *
     * The first element of each pair is a string containing the service name.
     * The second is the connection spec, typically "tcp/foo.bar.com:42"
     **/
    typedef std::vector<Spec> SpecList;

    /**
     * Obtain all the services matching a given pattern.
     *
     * The pattern is matched against all service names in the local mirror repository. A service name may contain '/'
     * as a separator token. A pattern may contain '*' to match anything up to the next '/' (or the end of the
     * name). This means that the pattern 'foo/<!-- slash-star -->*<!-- star-slash -->/baz' would match the service
     * names 'foo/bar/baz' and 'foo/xyz/baz'. The pattern 'foo/b*' would match 'foo/bar', but neither 'foo/xyz' nor
     * 'foo/bar/baz'. The pattern 'a*b' will never match anything.
     *
     * @return a list of all matching services, with corresponding connect specs
     * @param pattern The pattern used for matching
     **/
    virtual SpecList lookup(const std::string & pattern) const = 0;

    /**
     * Obtain the number of updates seen by this mirror. The value may wrap, but will never become 0 again. This can be
     * used for name lookup optimization, because the results returned by lookup() will never change unless this number
     * also changes.
     *
     * @return number of slobrok updates seen
     **/
    virtual uint32_t updates() const = 0;
};

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

    /**
     * @brief Clean up.
     **/
    ~MirrorAPI();

    // Inherit doc from IMirrorAPI.
    SpecList lookup(const std::string & pattern) const;

    // Inherit doc from IMirrorAPI.
    uint32_t updates() const { return _updates.getAsInt(); }

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
    bool ready() const;

private:
    MirrorAPI(const MirrorAPI &);
    MirrorAPI &operator=(const MirrorAPI &);

    /** from FNET_Task, polls slobrok **/
    void PerformTask();

    /** from FRT_IRequestWait **/
    void RequestDone(FRT_RPCRequest *req);

    void updateTo(SpecList& newSpecs, uint32_t newGen);

    bool handleIncrementalFetch();
    bool handleMirrorFetch();

    void handleReconfig();
    bool handleReqDone();
    void handleReconnect();
    void makeRequest();

    void reSched(double seconds);

    FRT_Supervisor          &_orb;
    mutable vespalib::Lock   _lock;
    bool                     _reqPending;
    bool                     _scheduled;
    bool                     _reqDone;
    bool                     _useOldProto;
    SpecList                 _specs;
    vespalib::GenCnt         _specsGen;
    vespalib::GenCnt         _updates;
    SlobrokList              _slobrokSpecs;
    Configurator::UP         _configurator;
    std::string              _currSlobrok;
    int                      _rpc_ms;
    uint32_t                 _idx;
    BackOff                  _backOff;
    FRT_Target              *_target;
    FRT_RPCRequest          *_req;
};

} // namespace api
} // namespace slobrok

