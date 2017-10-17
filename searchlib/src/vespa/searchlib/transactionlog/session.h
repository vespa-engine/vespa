// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/fnet/frt/invoker.h>
#include <deque>

class FastOS_FileInterface;

namespace search::transactionlog {

class Domain;
class DomainPart;
using DomainSP = std::shared_ptr<Domain>;

class Session : public FRT_IRequestWait
{
private:
    typedef vespalib::Executor::Task Task;

public:
    typedef std::shared_ptr<Session> SP;
    Session(const Session &) = delete;
    Session & operator = (const Session &) = delete;
    Session(int sId, const SerialNumRange & r, const DomainSP & d, FRT_Supervisor & supervisor, FNET_Connection *conn);
    ~Session();
    const SerialNumRange & range() const { return _range; }
    int                       id() const { return _id; }
    bool inSync()    const;
    bool ok()        const { return _ok; }
    bool finished()  const;
    static void enQ(const SP & session, SerialNum serial, const Packet & packet);
    static Task::UP createTask(const Session::SP & session);
private:
    struct QPacket {
        QPacket() : _serial(0), _packet() {}
        QPacket(SerialNum s, const Packet & p)
            : _serial(s),
              _packet(new Packet(p))
        { }
        SerialNum _serial;
        std::unique_ptr<Packet>    _packet;
    };
    class VisitTask : public Task {
    public:
        VisitTask(const Session::SP & session) : _session(session) { }
    private:
        void run() override;
        Session::SP _session;
    };

    class SendTask : public Task {
    public:
        SendTask(const Session::SP & session) : _session(session) { }
        void run() override;
    private:
        Session::SP _session;
    };
    bool send(FRT_RPCRequest * req, bool wait);
    void RequestDone(FRT_RPCRequest *req) override;
    bool send(const Packet & packet);
    void sendPacket(SerialNum serial, const Packet & packet);
    bool sendDone();
    void sendPending();
    void visit();
    void visitOnly();
    void finalize();
    bool visit(FastOS_FileInterface & file, DomainPart & dp) __attribute__((noinline));
    int32_t rpc(FRT_RPCRequest * req);
    int32_t rpcAsync(FRT_RPCRequest * req);
    FRT_Supervisor          & _supervisor;
    FNET_Connection         * _connection;
    DomainSP                  _domain;
    SerialNumRange            _range;
    int                       _id;
    bool                      _inSync;
    bool                      _ok;
    bool                      _finished;
    std::deque<QPacket>       _packetQ;
    vespalib::Lock            _lock;
};

}
