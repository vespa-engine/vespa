// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/messagebus/common.h>
#include <vector>
#include <set>

namespace mbus {

/**
 * This class keeps track of OOS information obtained from a single
 * server. This class is used by the OOSManager class. Note that since
 * this class is only used inside the transport thread it has no
 * synchronization. Using it directly will lead to race conditions and
 * possible crashes.
 **/
class OOSClient : public FNET_Task,
                  public FRT_IRequestWait
{
private:
    typedef std::vector<string> StringList;

    FRT_Supervisor &_orb;
    string          _spec;
    StringList      _oosList;
    uint32_t        _reqGen;  // server gen used for request
    uint32_t        _listGen; // server gen of the oosList
    uint32_t        _dumpGen; // server gen used for the last dump
    bool            _reqDone;
    FRT_Target     *_target;
    FRT_RPCRequest *_req;

    OOSClient(const OOSClient &);
    OOSClient &operator=(const OOSClient &);

    /**
     * Handle a server reply.
     **/
    void handleReply();

    /**
     * Handle server (re)connect.
     **/
    void handleConnect();

    /**
     * Handle server invocation.
     **/
    void handleInvoke();

    /**
     * From FNET_Task, performs overall server poll logic.
     **/
    void PerformTask() override;

    /**
     * From FRT_IRequestWait, picks up server replies.
     *
     * @param req the request that has completed
     **/
    void RequestDone(FRT_RPCRequest *req) override;

public:
    /**
     * Data structure used to aggregate OOS information
     **/
    typedef std::set<string> StringSet;

    /**
     * Convenience typedef for a shared pointer to a OOSClient object.
     **/
    typedef std::shared_ptr<OOSClient> SP;

    /**
     * Create a new OOSClient polling oos information from the given
     * server.
     *
     * @param orb object used for RPC operations
     * @param spec fnet connect spec for oos server
     **/
    OOSClient(FRT_Supervisor &orb, const string &spec);

    /**
     * Destructor.
     **/
    virtual ~OOSClient();

    /**
     * Obtain the connect spec of the OOS server this client is
     * talking to.
     *
     * @return OOS server connect spec
     **/
    const string &getSpec() const { return _spec; }

    /**
     * Check if this client has changed. A client has changed if it
     * has obtain now information after the dumpState method was last
     * invoked.
     *
     * @return true is this client has changed
     **/
    bool isChanged() const { return (_listGen != _dumpGen); }

    /**
     * Returns whether or not this client has receieved any reply
     * at all from the server it is connected to.
     *
     * @return True if initial request has returned.
     */
    bool isReady() const { return _listGen != 0; }

    /**
     * Dump the current oos information known by this client into the
     * given string set.
     *
     * @param dst object used to aggregate oos information
     **/
    void dumpState(StringSet &dst);
};

} // namespace mbus

