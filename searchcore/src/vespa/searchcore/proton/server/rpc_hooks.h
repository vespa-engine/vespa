// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/slobrok/sbregister.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/closure.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchlib/common/packets.h>
#include <mutex>
#include <condition_variable>

namespace proton {

class Proton;
class DocsumByRPC;

class RPCHooksBase : public FRT_Invokable
{
private:
    class Session {
    private:
        fastos::TimeStamp _createTime;
        int64_t           _numDocs;
        vespalib::string  _delayedConfigs;
        int64_t           _gen;
        bool              _down;
    public:
        typedef std::shared_ptr<Session> SP;
        Session();
        int64_t                  getGen() const { return _gen; }
        fastos::TimeStamp getCreateTime() const { return _createTime; }
        Session & setGen(int64_t gen) { _gen = gen; return *this; }

        int64_t getNumDocs() const { return _numDocs; }
        void setNumDocs(int64_t numDocs) { _numDocs = numDocs; }
        bool getDown() const { return _down; }
        void setDown() { _down = true; }

        const vespalib::string & getDelayedConfigs() const {
            return _delayedConfigs;
        }

        void setDelayedConfigs(const vespalib::string &delayedConfigs) {
            _delayedConfigs = delayedConfigs;
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

    Proton                         & _proton;
    std::unique_ptr<DocsumByRPC>     _docsumByRPC;
    std::unique_ptr<FRT_Supervisor>  _orb;
    slobrok::api::RegisterAPI        _regAPI;
    std::mutex                       _stateLock;
    std::condition_variable          _stateCond;
    vespalib::ThreadStackExecutor    _executor;

    void initRPC();
    void letProtonDo(vespalib::Closure::UP closure);

    void triggerFlush(FRT_RPCRequest *req);
    void prepareRestart(FRT_RPCRequest *req);
    void checkState(StateArg::UP arg);
    void reportState(Session & session, FRT_RPCRequest * req) __attribute__((noinline));
    void getProtonStatus(FRT_RPCRequest * req);
    void getDocsums(FRT_RPCRequest *req);

    static const Session::SP & getSession(FRT_RPCRequest *req);
public:
    typedef std::unique_ptr<RPCHooksBase> UP;
    struct Params {
        Proton           &proton;
        config::ConfigUri slobrok_config;
        vespalib::string  identity;
        uint32_t          rtcPort;

        Params(Proton &parent, uint32_t port, const vespalib::string &ident);
        ~Params();
    };
    RPCHooksBase(const RPCHooksBase &) = delete;
    RPCHooksBase & operator = (const RPCHooksBase &) = delete;
    RPCHooksBase(Params &params);
    virtual ~RPCHooksBase();
    void close();

    void rpc_GetState(FRT_RPCRequest *req);
    void rpc_GetProtonStatus(FRT_RPCRequest *req);
    void rpc_getIncrementalState(FRT_RPCRequest *req);
    void rpc_Shutdown(FRT_RPCRequest *req);
    void rpc_die(FRT_RPCRequest *req);
    void rpc_triggerFlush(FRT_RPCRequest *req);
    void rpc_prepareRestart(FRT_RPCRequest *req);
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

