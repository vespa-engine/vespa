// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/connection.h>
#include <atomic>

class FNET_Scheduler;
class FRT_RPCRequest;
class FRT_IRequestWait;

class FRT_Target
{
private:
    std::atomic<int> _refcnt;
    FNET_Scheduler  *_scheduler;
    FNET_Connection *_conn;

    FRT_Target(const FRT_Target &);
    FRT_Target &operator=(const FRT_Target &);

public:
    FRT_Target(FNET_Scheduler *scheduler, FNET_Connection *conn)
        : _refcnt(1),
          _scheduler(scheduler),
          _conn(conn) {}

    ~FRT_Target();

    FNET_Connection *GetConnection() const { return _conn; }

    void AddRef() { _refcnt.fetch_add(1); }
    void SubRef() {
        if (_refcnt.fetch_sub(1) == 1) {
            delete this;
        }
    }

    int GetRefCnt() const { return _refcnt; }

    bool IsValid() {
        return ((_conn != nullptr) &&
                (_conn->GetState() <= FNET_Connection::FNET_CONNECTED));
    }

    void InvokeAsync(FRT_RPCRequest *req, double timeout, FRT_IRequestWait *waiter);
    void InvokeVoid(FRT_RPCRequest *req);
    void InvokeSync(FRT_RPCRequest *req, double timeout);
};
