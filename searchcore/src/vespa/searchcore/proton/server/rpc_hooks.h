// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/frt.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/closure.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchlib/common/packets.h>

#include "ooscli.h"

namespace proton {

class Proton;
class DocsumByRPC;

class RPCHooksBase : public FRT_Invokable
{
private:
    class Session {
    private:
        fastos::TimeStamp _createTime;
        int64_t		  _numDocs;
        vespalib::string  _badConfigs;	
        int64_t           _gen;
        bool		  _down;
    public:
        typedef std::shared_ptr<Session> SP;
        Session();
        int64_t                  getGen() const { return _gen; }
        fastos::TimeStamp getCreateTime() const { return _createTime; }
        Session & setGen(int64_t gen) { _gen = gen; return *this; }

        int64_t getNumDocs(void) const { return _numDocs; }
        void setNumDocs(int64_t numDocs) { _numDocs = numDocs; }
        bool getDown(void) const { return _down; }
        void setDown(void) { _down = true; }

        const vespalib::string & getBadConfigs(void) const {
            return _badConfigs;
        }

        void setBadConfigs(const vespalib::string &badConfigs) {
            _badConfigs = badConfigs;
        }

    };
    struct StateArg {
        typedef std::unique_ptr<StateArg> UP;
        StateArg(Session::SP session, FRT_RPCRequest * req, fastos::TimeStamp dueTime) :
            _session(session),
            _req(req),
            _dueTime(dueTime)
        { }
        Session::SP _session;
        FRT_RPCRequest * _req;
        fastos::TimeStamp _dueTime;
    };

    Proton                      & _proton;
    std::unique_ptr<DocsumByRPC>  _docsumByRPC;
    FRT_Supervisor                _orb;
    slobrok::api::RegisterAPI     _regAPI;
    vespalib::Monitor             _stateMonitor;
    vespalib::ThreadStackExecutor _executor;
    OosCli                        _ooscli;

    void initRPC();
    void letProtonDo(vespalib::Closure::UP closure);

    void triggerFlush(FRT_RPCRequest *req);
    void prepareRestart(FRT_RPCRequest *req);
    void enableSearching(FRT_RPCRequest * req);
    void disableSearching(FRT_RPCRequest * req);
    void checkState(StateArg::UP arg);
    void reportState(Session & session, FRT_RPCRequest * req) __attribute__((noinline));
    void getProtonStatus(FRT_RPCRequest * req);
    void listDocTypes(FRT_RPCRequest *req);
    void listSchema(FRT_RPCRequest *req);
    void getConfigGeneration(FRT_RPCRequest *req);
    void getDocsums(FRT_RPCRequest *req);

    static const Session::SP & getSession(FRT_RPCRequest *req);
public:
    typedef std::unique_ptr<RPCHooksBase> UP;
    struct Params : public OosCli::OosParams {
        vespalib::string identity;
        uint32_t rtcPort;

        Params(Proton &parent, uint32_t port, const vespalib::string &ident)
            : OosParams(parent),
              identity(ident),
              rtcPort(port)
        {
            my_oos_name = identity;
            my_oos_name.append("/feed-destination");
        }
    };
    RPCHooksBase(const RPCHooksBase &) = delete;
    RPCHooksBase & operator = (const RPCHooksBase &) = delete;
    RPCHooksBase(Params &params);
    virtual ~RPCHooksBase();
    void close();

    void rpc_enableSearching(FRT_RPCRequest *req);
    void rpc_disableSearching(FRT_RPCRequest *req);
    void rpc_GetState(FRT_RPCRequest *req);
    void rpc_GetProtonStatus(FRT_RPCRequest *req);
    void rpc_getIncrementalState(FRT_RPCRequest *req);
    void rpc_Shutdown(FRT_RPCRequest *req);
    void rpc_die(FRT_RPCRequest *req);
    void rpc_triggerFlush(FRT_RPCRequest *req);
    void rpc_prepareRestart(FRT_RPCRequest *req);
    void rpc_listDocTypes(FRT_RPCRequest *req);
    void rpc_listSchema(FRT_RPCRequest *req);
    void rpc_getConfigGeneration(FRT_RPCRequest *req);
    void rpc_getDocSums(FRT_RPCRequest *req);

    void initSession(FRT_RPCRequest *req);
    void finiSession(FRT_RPCRequest *req);
    void downSession(FRT_RPCRequest *req);
    void mismatch(FRT_RPCRequest *req);
protected:
    void open(Params & params);
};

class RPCHooks : public RPCHooksBase
{
public:
    RPCHooks(Params &params);
};


} // namespace proton

