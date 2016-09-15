// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "scheduler.h"

#include <boost/bind.hpp>

#include <iostream>

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
    _timer.async_wait(boost::bind(&Task::handle, shared_from_this(), _1));
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


Scheduler::Scheduler(boost::function<void (asio::io_service&)> callRun)
    :_keepAliveWork(ioService),
     _workerThread(boost::bind(callRun, boost::ref(ioService)))
{}

Scheduler::~Scheduler() {
    ioService.stop();
    _workerThread.interrupt();
    _workerThread.join();
    ioService.reset();
}
