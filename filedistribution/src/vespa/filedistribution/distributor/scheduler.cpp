// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduler.h"

namespace asio = boost::asio;

using filedistribution::Scheduler;
typedef Scheduler::Task Task;

Task::Task(Scheduler& scheduler)
    : _timer(scheduler.ioService)
{}

void
Task::schedule(asio::deadline_timer::duration_type delay)
{
    _timer.expires_from_now(delay);
    std::shared_ptr<Task> self = shared_from_this();;
    _timer.async_wait([self](const auto & e) { self->handle(e); });
}

void
Task::scheduleNow()
{
    schedule(boost::posix_time::seconds(0));
}

void
Task::handle(const boost::system::error_code& code) {
    if (code != asio::error::operation_aborted) {
        doHandle();
    }
}


Scheduler::Scheduler(std::function<void (asio::io_service&)> callRun)
    :_keepAliveWork(ioService),
     _workerThread([&, callRun]() { callRun(ioService); })
{}

Scheduler::~Scheduler() {
    ioService.stop();
    _workerThread.join();
    ioService.reset();
}
