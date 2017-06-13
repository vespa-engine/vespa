// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/gencnt.h>
#include <vespa/slobrok/backoff.h>
#include <vespa/fnet/frt/invoker.h>

class FRT_Target;

namespace slobrok::api {

/**
 * @brief Defines an interface for the name server lookup.
 **/
class IMirrorOld {
protected:
    static bool match(const char *name, const char *pattern);

public:
    /**
     * @brief Release any allocated resources.
     **/
    virtual ~IMirrorOld() { }

    /**
     * @brief vector of <name, connectionspec> pairs.
     *
     * The first element of each pair is a string containing the
     * service name.  The second is the connection spec, typically
     * "tcp/foo.bar.com:42"
     **/
    typedef std::vector< std::pair<std::string, std::string> > SpecList;

    /**
     * Obtain all the services matching a given pattern.
     *
     * The pattern is matched against all service names in the local
     * mirror repository. A service name may contain '/' as a
     * separator token. A pattern may contain '*' to match anything up
     * to the next '/' (or the end of the name). This means that the
     * pattern 'foo/<!-- slash-star -->*<!-- star-slash -->/baz' would
     * match the service names 'foo/bar/baz' and 'foo/xyz/baz'. The
     * pattern 'foo/b*' would match 'foo/bar', but neither 'foo/xyz'
     * nor 'foo/bar/baz'. The pattern 'a*b' will never match anything.
     *
     * @return a list of all matching services, with corresponding connect specs
     * @param pattern The pattern used for matching
     **/
    virtual SpecList lookup(const std::string & pattern) const = 0;

    /**
     * Obtain the number of updates seen by this mirror. The value may
     * wrap, but will never become 0 again. This can be used for name
     * lookup optimization, because the results returned by lookup()
     * will never change unless this number also changes.
     *
     * @return number of slobrok updates seen
     **/
    virtual uint32_t updates() const = 0;
};

/**
 * @brief A MirrorOld object is used to keep track of the services
 * registered with a slobrok cluster.
 *
 * Updates to the service repository are fetched in the
 * background. Lookups against this object is done using an internal
 * mirror of the service repository.
 **/
class MirrorOld : public FNET_Task,
                  public FRT_IRequestWait,
                  public IMirrorOld
{
public:
    /**
     * @brief Create a new MirrorOld using the given Supervisor and slobrok
     * connect specs.
     *
     * @param orb the Supervisor to use
     * @param slobroks slobrok connect spec list
     **/
    MirrorOld(FRT_Supervisor &orb, const std::vector<std::string> &slobroks);

    /**
     * @brief Clean up.
     **/
    ~MirrorOld();

    SpecList lookup(const std::string & pattern) const override;
    uint32_t updates() const override { return _updates.getAsInt(); }

    /**
     * @brief Ask if the MirrorOld has got any useful information from
     * the Slobrok
     *
     * On application startup it is often useful to run the event loop
     * for some time until this functions returns true (or if it never
     * does, time out and tell the user there was no answer from any
     * Service Location Broker).
     *
     * @return true if the MirrorOld object has
     * asked for updates from a Slobrok and got any answer back
     **/
    bool ready() const { return _updates.getAsInt() != 0; }

private:
    MirrorOld(const MirrorOld &);
    MirrorOld &operator=(const MirrorOld &);

    void PerformTask() override;
    void RequestDone(FRT_RPCRequest *req) override;

    FRT_Supervisor          &_orb;
    mutable FastOS_Mutex     _lock;
    bool                     _reqDone;
    SpecList                 _specs;
    vespalib::GenCnt         _specsGen;
    vespalib::GenCnt         _updates;
    std::vector<std::string> _slobrokspecs;
    uint32_t                 _idx;
    BackOff                  _backOff;
    FRT_Target              *_target;
    FRT_RPCRequest          *_req;
};

} // namespace slobrok::api
