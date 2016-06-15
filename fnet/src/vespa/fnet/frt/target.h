// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/atomic.h>

class FRT_Target
{
private:
    int              _refcnt;
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

    void AddRef() { vespalib::Atomic::postInc(&_refcnt); }
    void SubRef() {
        if (vespalib::Atomic::postDec(&_refcnt) == 1) {
            delete this;
        }
    }

    int GetRefCnt() const { return _refcnt; }

    bool IsValid()
    {
        return ((_conn != NULL) &&
                (_conn->GetState() <= FNET_Connection::FNET_CONNECTED));
    }

    void InvokeAsync(FRT_RPCRequest *req, double timeout, FRT_IRequestWait *waiter)
    {
        FRT_Supervisor::InvokeAsync(_scheduler, _conn, req, timeout, waiter);
    }

    void InvokeVoid(FRT_RPCRequest *req)
    {
        FRT_Supervisor::InvokeVoid(_conn, req);
    }

    void InvokeSync(FRT_RPCRequest *req, double timeout)
    {
        FRT_Supervisor::InvokeSync(_scheduler, _conn, req, timeout);
    }
};
