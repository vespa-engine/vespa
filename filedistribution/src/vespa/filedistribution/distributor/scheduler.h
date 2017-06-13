// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/asio/io_service.hpp>
#include <boost/asio/deadline_timer.hpp>
#include <thread>


namespace filedistribution {

class Scheduler {
public:
    class Task : public std::enable_shared_from_this<Task> {
        boost::asio::deadline_timer _timer;
    public:
        typedef std::shared_ptr<Task> SP;

        Task(Scheduler& scheduler);

        virtual ~Task() {}

        void schedule(boost::asio::deadline_timer::duration_type delay);
        void scheduleNow();

        void handle(const boost::system::error_code& code);
    protected:
        virtual void doHandle() = 0;
    };

private:
    boost::asio::io_service ioService;

    //keeps io_service.run() from exiting until it has been destructed,
    //see http://www.boost.org/doc/libs/1_42_0/doc/html/boost_asio/reference/io_service.html
    boost::asio::io_service::work _keepAliveWork;
    std::thread _workerThread;

public:
    Scheduler(const Scheduler &) = delete;
    Scheduler & operator = (const Scheduler &) = delete;
    Scheduler(std::function<void (boost::asio::io_service&)> callRun) ;
    ~Scheduler();
};

}

