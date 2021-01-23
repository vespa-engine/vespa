// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/slobrok/sbregister.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchlib/engine/proto_rpc_adapter.h>
#include <mutex>
#include <condition_variable>

class FNET_Transport;

namespace proton {

class Proton;
class DocsumByRPC;

class RPCHooksBase : public FRT_Invokable
{
private:
    using ProtoRpcAdapter = search::engine::ProtoRpcAdapter;

    class Session {
    private:
        int64_t           _numDocs;
        vespalib::string  _delayedConfigs;
        int64_t           _gen;
        bool              _down;
    public:
        typedef std::shared_ptr<Session> SP;
        Session();
        int64_t                  getGen() const { return _gen; }
        Session & setGen(int64_t gen) { _gen = gen; return *this; }

        int64_t getNumDocs() const { return _numDocs; }
        void setNumDocs(int64_t numDocs) { _numDocs = numDocs; }
        void setDown() { _down = true; }

        const vespalib::string & getDelayedConfigs() const {
            return _delayedConfigs;
        }

        void setDelayedConfigs(const vespalib::string &delayedConfigs) {
            _delayedConfigs = delayedConfigs;
        }

    };
    struct StateArg {
        StateArg(Session::SP session, FRT_RPCRequest * req, vespalib::steady_time dueTime) :
            _session(std::move(session)),
            _req(req),
            _dueTime(dueTime)
        { }
        Session::SP            _session;
        FRT_RPCRequest       * _req;
        vespalib::steady_time  _dueTime;
    };

    Proton                         & _proton;
    std::unique_ptr<DocsumByRPC>     _docsumByRPC;
    std::unique_ptr<FNET_Transport>  _transport;
    std::unique_ptr<FRT_Supervisor>  _orb;
    std::unique_ptr<ProtoRpcAdapter> _proto_rpc_adapter;
    slobrok::api::RegisterAPI        _regAPI;
    std::mutex                       _stateLock;
    std::condition_variable          _stateCond;
    vespalib::ThreadStackExecutor    _executor;

    void initRPC();
    void letProtonDo(vespalib::Executor::Task::UP task);

    void triggerFlush(FRT_RPCRequest *req);
    void prepareRestart(FRT_RPCRequest *req);
    void checkState(std::unique_ptr<StateArg> arg);
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
        uint32_t          numRpcThreads;

        Params(Proton &parent, uint32_t port, const vespalib::string &ident, uint32_t numRpcThreads);
        ~Params();
    };
    RPCHooksBase(const RPCHooksBase &) = delete;
    RPCHooksBase & operator = (const RPCHooksBase &) = delete;
    RPCHooksBase(Params &params);
    auto &proto_rpc_adapter_metrics() { return _proto_rpc_adapter->metrics(); }
    void set_online();
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

}
