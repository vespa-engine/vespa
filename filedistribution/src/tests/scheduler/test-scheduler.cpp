// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN

#include <boost/test/unit_test.hpp>

#include <vespa/filedistribution/distributor/scheduler.h>

#include <iostream>

#include <boost/thread/barrier.hpp>

using filedistribution::Scheduler;
using namespace std::literals;

namespace asio = boost::asio;

class TestException {};


struct CallRun {
    volatile bool _caughtException;

    CallRun()
        :_caughtException(false)
    {}

    void operator()(asio::io_service& ioService) {
        try {
            //No reset needed after handling exceptions.
            ioService.run();
        } catch(const TestException& e ) {
            _caughtException = true;
        }
    }
};

struct Fixture {
    CallRun callRun;
    Scheduler scheduler;

    Fixture()
        : scheduler(std::ref(callRun))
    {}
};


BOOST_FIXTURE_TEST_SUITE(SchedulerTest, Fixture)


struct RepeatedTask : Scheduler::Task {
    void doHandle() override {
        std::cout <<"RepeatedTask::doHandle " <<std::endl;
        schedule(boost::posix_time::seconds(1));
    }

    RepeatedTask(Scheduler& scheduler) : Task(scheduler) {}
};

BOOST_AUTO_TEST_CASE(require_tasks_does_not_keep_scheduler_alive) {
    RepeatedTask::SP task(new RepeatedTask(scheduler));
    task->schedule(boost::posix_time::hours(10));
}

struct EnsureInvokedTask : Scheduler::Task {
    boost::barrier& _barrier;

    void doHandle() override {
        _barrier.wait();
    }

    EnsureInvokedTask(Scheduler& scheduler, boost::barrier& barrier) :
        Task(scheduler),
        _barrier(barrier)
    {}
};


BOOST_AUTO_TEST_CASE(require_task_invoked) {
    boost::barrier barrier(2);

    EnsureInvokedTask::SP task(new EnsureInvokedTask(scheduler, barrier));
    task->schedule(boost::posix_time::milliseconds(50));

    barrier.wait();
}

struct ThrowExceptionTask : Scheduler::Task {
    void doHandle() override {
        throw TestException();
    }

    ThrowExceptionTask(Scheduler& scheduler) :
        Task(scheduler)
    {}
};

BOOST_AUTO_TEST_CASE(require_exception_from_tasks_can_be_caught) {
    ThrowExceptionTask::SP task(new ThrowExceptionTask(scheduler));
    task->scheduleNow();

    for (int i=0; i<200 && !callRun._caughtException; ++i)  {
        std::this_thread::sleep_for(100ms);
    }

    BOOST_CHECK(callRun._caughtException);
}

BOOST_AUTO_TEST_SUITE_END()
