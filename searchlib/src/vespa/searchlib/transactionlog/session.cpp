// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "session.h"
#include "domain.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/log/log.h>

LOG_SETUP(".transactionlog.session");

using vespalib::LockGuard;

namespace search::transactionlog {

namespace {
    const double NEVER(-1.0);
}

vespalib::Executor::Task::UP
Session::createTask(const Session::SP & session)
{
    return Task::UP(new VisitTask(session));
}

Session::VisitTask::VisitTask(const Session::SP & session)
    : _session(session)
{
    _session->startVisit();
}
Session::VisitTask::~VisitTask() = default;

void
Session::VisitTask::run()
{
    _session->visitOnly();
}

bool
Session::visit(FastOS_FileInterface & file, DomainPart & dp) {
    Packet packet(size_t(-1));
    bool more(false);
    if (dp.isClosed()) {
        more = dp.visit(file, _range, packet);
    } else {
        more = dp.visit(_range, packet);
    }
    if ( ! packet.getHandle().empty()) {
        send(packet);
    }
    return more;
}

void
Session::visit()
{
    LOG(debug, "[%d] : Visiting %" PRIu64 " - %" PRIu64, _id, _range.from(), _range.to());
    for (DomainPart::SP dpSafe = _domain->findPart(_range.from()); dpSafe.get() && (_range.from() < _range.to()) && (dpSafe.get()->range().from() <= _range.to()); dpSafe = _domain->findPart(_range.from())) {
        // Must use findPart and iterate until no candidate parts found.
        DomainPart * dp(dpSafe.get());
        LOG(debug, "[%d] : Visiting the interval %" PRIu64 " - %" PRIu64 " in domain part [%" PRIu64 ", %" PRIu64 "]", _id, _range.from(), _range.to(), dp->range().from(), dp->range().to());
        Fast_BufferedFile file;
        file.EnableDirectIO();
        for(bool more(true); ok() && more && (_range.from() < _range.to()); ) {
            more = visit(file, *dp);
        }
        // Nothing more in this DomainPart, force switch to next one.
        if (_range.from() < dp->range().to()) {
            _range.from(std::min(dp->range().to(), _range.to()));
        }
    }

    LOG(debug, "[%d] : Done visiting, starting subscribe %" PRIu64 " - %" PRIu64, _id, _range.from(), _range.to());
}

void
Session::startVisit() {
    assert(!_visitRunning);
    _visitRunning = true;
}
void
Session::visitOnly()
{
    visit();
    sendDone();
    finalize();
    _visitRunning = false;
}

bool Session::finished() const {
    return _finished || (_connection->GetState() != FNET_Connection::FNET_CONNECTED);
}

void
Session::finalize()
{
    if (!ok()) {
        LOG(error, "[%d] : Error in %s(%" PRIu64 " - %" PRIu64 "), stopping since I have no idea on what to do.", _id, "visitor", _range.from(), _range.to());
    }
    LOG(debug, "[%d] : Stopped %" PRIu64 " - %" PRIu64, _id, _range.from(), _range.to());
    _finished = true;
}

int32_t
Session::rpc(FRT_RPCRequest * req)
{
    int32_t retval(-7);
    LOG(debug, "rpc %s starting.", req->GetMethodName());
    FRT_Supervisor::InvokeSync(_supervisor.GetTransport(), _connection, req, NEVER);
    if (req->GetErrorCode() == FRTE_NO_ERROR) {
        retval = (req->GetReturn()->GetValue(0)._intval32);
        LOG(debug, "rpc %s = %d\n", req->GetMethodName(), retval);
    } else if (req->GetErrorCode() == FRTE_RPC_TIMEOUT) {
        LOG(warning, "rpc %s timed out. Will allow to continue: error(%d): %s\n", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
        retval = -req->GetErrorCode();
    } else {
        if (req->GetErrorCode() != FRTE_RPC_CONNECTION) {
            LOG(warning, "rpc %s: error(%d): %s\n", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
        }
        retval = -req->GetErrorCode();
        _ok = false;
    }
    return retval;
}

void
Session::RequestDone(FRT_RPCRequest * req)
{
    _ok = (req->GetErrorCode() == FRTE_NO_ERROR);
    if (req->GetErrorCode() != FRTE_NO_ERROR) {
        LOG(warning, "rpcAsync failed %s: error(%d): %s\n", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
    } else {
        int32_t retval = req->GetReturn()->GetValue(0)._intval32;
        if (retval != RPC::OK) {
            LOG(error, "Return value != OK in RequestDone for method '%s'", req->GetMethodName());
        }
    }
    req->SubRef();
}

Session::Session(int sId, const SerialNumRange & r, const Domain::SP & d,
                 FRT_Supervisor & supervisor, FNET_Connection *conn) :
    _supervisor(supervisor),
    _connection(conn),
    _domain(d),
    _range(r),
    _id(sId),
    _ok(true),
    _visitRunning(false),
    _inSync(false),
    _finished(false),
    _startTime()
{
    _connection->AddRef();
}

Session::~Session()
{
    _connection->SubRef();
}

bool
Session::send(const Packet & packet)
{
    FRT_RPCRequest *req = _supervisor.AllocRPCRequest();
    req->SetMethodName("visitCallback");
    req->GetParams()->AddString(_domain->name().c_str());
    req->GetParams()->AddInt32(id());
    req->GetParams()->AddData(packet.getHandle().c_str(), packet.getHandle().size());
    return send(req);
}

bool
Session::send(FRT_RPCRequest * req)
{
    int32_t retval = rpc(req);
    if ( ! ((retval == RPC::OK) || (retval == FRTE_RPC_CONNECTION)) ) {
        LOG(error, "Return value != OK(%d) in send for method 'visitCallback'.", retval);
    }
    req->SubRef();

    return (retval == RPC::OK);
}

bool
Session::sendDone()
{
    FRT_RPCRequest *req = _supervisor.AllocRPCRequest();
    req->SetMethodName("eofCallback");
    req->GetParams()->AddString(_domain->name().c_str());
    req->GetParams()->AddInt32(id());
    bool retval(send(req));
    _inSync = true;
    return retval;
}

}
