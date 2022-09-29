// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <vespa/vespalib/util/executor.h>
#include <atomic>

class FastOS_FileInterface;

namespace search::transactionlog {

class Domain;
class DomainPart;
using DomainSP = std::shared_ptr<Domain>;

class Session
{
private:
    using Task = vespalib::Executor::Task;
    using steady_time = vespalib::steady_time;

public:
    using SP = std::shared_ptr<Session>;
    Session(const Session &) = delete;
    Session & operator = (const Session &) = delete;
    Session(int sId, const SerialNumRange & r, const DomainSP & d, std::unique_ptr<Destination> destination);
    ~Session();
    const SerialNumRange & range() const { return _range; }
    int                       id() const { return _id; }
    bool inSync()    const { return _inSync; }
    bool finished()  const;
    static Task::UP createTask(Session::SP session);
    void setStartTime(steady_time startTime) { _startTime = startTime; }
    steady_time getStartTime() const { return _startTime; }
    bool isVisitRunning() const { return _visitRunning; }
private:
    class VisitTask : public Task {
    public:
        VisitTask(Session::SP session);
        ~VisitTask();
    private:
        void run() override;
        Session::SP _session;
    };

    bool ok()        const { return _destination->ok(); }
    bool send(const Packet & packet);
    bool sendDone();
    void visit();
    void visitOnly();
    void startVisit();
    void finalize();
    bool visit(FastOS_FileInterface & file, DomainPart & dp) __attribute__((noinline));
    std::unique_ptr<Destination> _destination;
    DomainSP                     _domain;
    SerialNumRange               _range;
    int                          _id;
    std::atomic<bool>            _visitRunning;
    std::atomic<bool>            _inSync;
    std::atomic<bool>            _finished;
    steady_time                  _startTime;
};

}
