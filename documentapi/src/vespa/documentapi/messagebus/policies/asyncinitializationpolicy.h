// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/documentapi/common.h>
#include <map>
#include <mutex>

namespace documentapi {

class AsyncInitializationPolicy : public mbus::IRoutingPolicy {
public:
    AsyncInitializationPolicy(const std::map<string, string>& parameters);
    ~AsyncInitializationPolicy() override;

    static std::map<string, string> parse(string parameters);

    /**
     * This function is called asynchronously at some point after the
     * first select() is called.
     *
     * @return
     */
    virtual string init() = 0;

    void select(mbus::RoutingContext &context) override;

    virtual void doSelect(mbus::RoutingContext& context) = 0;

    const string& getError() {
        return _error;
    }

    void initSynchronous();

    /**
     * Signal that subclass should be async initialized. Must be called prior
     * to the first invocation of select().
     */
    void needAsynchronousInit() { _syncInit = false; }

private:
    class Task : public vespalib::Executor::Task
    {
    public:
        Task(AsyncInitializationPolicy& owner)
            : _owner(owner)
        {
        }

        Task(const Task&) = delete;
        Task& operator=(const Task&) = delete;

        void run() override;

    private:
        AsyncInitializationPolicy& _owner;
    };

    friend class Task;

    std::unique_ptr<vespalib::Executor> _executor;
    std::mutex _lock;

    enum class State {
        NOT_STARTED,
        RUNNING,
        FAILED,
        DONE
    };

    State _state;

protected:
    string _error;
    bool _syncInit;
};

}


