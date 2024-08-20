// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "client_common.h"
#include "client_session.h"
#include <vespa/fnet/frt/invokable.h>
#include <map>
#include <vector>
#include <mutex>
#include <memory>

class FNET_Transport;
class FRT_Supervisor;
class FRT_Target;

namespace vespalib { class ThreadStackExecutorBase; }
namespace search::transactionlog::client {

class Session;
class Visitor;

class TransLogClient : private FRT_Invokable
{
public:
    TransLogClient(FNET_Transport & transport, const std::string & rpctarget);
    TransLogClient(const TransLogClient &) = delete;
    TransLogClient& operator=(const TransLogClient &) = delete;
    ~TransLogClient() override;

    /// Here you create a new domain
    bool create(const std::string & domain);
    /// Here you remove a domain
    bool remove(const std::string & domain);
    /// Here you open an existing domain
    std::unique_ptr<Session> open(const std::string & domain);
    /// Here you can get a list of available domains.
    bool listDomains(std::vector<std::string> & dir);
    std::unique_ptr<Visitor> createVisitor(const std::string & domain, Callback & callBack);

    bool isConnected() const;
    void disconnect();
    bool reconnect();
    const std::string &getRPCTarget() const { return _rpcTarget; }
private:
    friend Session;
    friend Visitor;
    void exportRPC(FRT_Supervisor & supervisor);
    void do_visitCallbackRPC(FRT_RPCRequest *req);
    void do_eofCallbackRPC(FRT_RPCRequest *req);
    void visitCallbackRPC_hook(FRT_RPCRequest *req);
    void eofCallbackRPC_hook(FRT_RPCRequest *req);
    int32_t rpc(FRT_RPCRequest * req);
    Session * findSession(const std::string & domain, int sessionId);

    using SessionMap = std::map< SessionKey, Session * >;

    std::unique_ptr<vespalib::ThreadStackExecutorBase> _executor;
    std::string                   _rpcTarget;
    SessionMap                         _sessions;
    //Brute force lock for subscriptions. For multithread safety.
    std::mutex                         _lock;
    std::unique_ptr<FRT_Supervisor>    _supervisor;
    FRT_Target                       * _target;
};

}

