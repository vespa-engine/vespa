// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "session.h"
#include "domain.h"
#include "domainpart.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".transactionlog.session");


namespace search::transactionlog {

vespalib::Executor::Task::UP
Session::createTask(Session::SP session)
{
    return std::make_unique<VisitTask>(std::move(session));
}

Session::VisitTask::VisitTask(Session::SP session)
    : _session(std::move(session))
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
    bool more = dp.visit(file, _range, packet);

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
    return _finished || ! _destination->connected();
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

Session::Session(int sId, const SerialNumRange & r, const Domain::SP & d,
                 std::unique_ptr<Destination> destination) :
    _destination(std::move(destination)),
    _domain(d),
    _range(r),
    _id(sId),
    _visitRunning(false),
    _inSync(false),
    _finished(false),
    _startTime()
{
}

Session::~Session() = default;

bool
Session::send(const Packet & packet)
{
    return _destination->send(_id, _domain->name(), packet);
}

bool
Session::sendDone()
{
    bool retval = _destination->sendDone(_id, _domain->name());
    _inSync = true;
    return retval;
}

}
