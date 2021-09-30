// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * File:   AsyncInitializationPolicy.cpp
 * Author: thomasg
 *
 * Created on July 17, 2012, 1:43 PM
 */

#include "asyncinitializationpolicy.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/vespalib/text/stringtokenizer.h>

namespace documentapi {

std::map<string, string>
AsyncInitializationPolicy::parse(string parameters) {
    std::map<string, string> retVal;

    vespalib::StringTokenizer tokenizer(parameters, ";");
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        string keyValue = tokenizer[i];
        vespalib::StringTokenizer keyV(keyValue, "=");

        if (keyV.size() == 1) {
            retVal[keyV[0]] = "true";
        } else {
            retVal[keyV[0]] = keyV[1];
        }
    }

    return retVal;
}

AsyncInitializationPolicy::AsyncInitializationPolicy(
        const std::map<string, string>&)
    : _executor(new vespalib::ThreadStackExecutor(1, 1024)),
      _state(State::NOT_STARTED),
      _syncInit(true)
{
}

AsyncInitializationPolicy::~AsyncInitializationPolicy() = default;

void
AsyncInitializationPolicy::initSynchronous()
{
    init();
    _state = State::DONE;
}

namespace {

mbus::Error
currentPolicyInitError(vespalib::stringref error) {
    // If an init error has been recorded for the last init attempt, report
    // it back until we've managed to successfully complete the init step.
    if (error.empty()) {
        return mbus::Error(DocumentProtocol::ERROR_NODE_NOT_READY,
                           "Waiting to initialize policy");
    } else {
        return mbus::Error(DocumentProtocol::ERROR_POLICY_FAILURE,
                           "Error when creating policy: " + error);
    }
}

}

void
AsyncInitializationPolicy::select(mbus::RoutingContext& context)
{
    if (_syncInit && _state != State::DONE) {
        initSynchronous();
    }

    {
        std::lock_guard lock(_lock);

        if (_state == State::NOT_STARTED || _state == State::FAILED) {
            // Only 1 task may be queued to the executor at any point in time.
            // This is maintained by only scheduling a task when either no task
            // has been created before or the previous task has signalled it is
            // entirely done with accessing the state of this policy (including
            // the mutex). After setting _state == RUNNING, only the task
            // is allowed to mutate _state.
            _executor->execute(vespalib::Executor::Task::UP(new Task(*this)));
            _state = State::RUNNING;
        }

        if (_state != State::DONE) {
            auto reply = std::make_unique<mbus::EmptyReply>();
            reply->addError(currentPolicyInitError(_error));
            context.setReply(std::move(reply));
            return;
        }
        // We have the mutex in state DONE and since no task may be queued
        // up for execution at this point, it's not possible for this to
        // deadlock (executor will stall until all its tasks have finished
        // executing, and any queued tasks would attempt to take the mutex
        // we're currently holding, deadlocking both threads).
        _executor.reset();
    }

    doSelect(context);
}

void
AsyncInitializationPolicy::Task::run()
{
    string error;

    try {
        error = _owner.init();
    } catch (const std::exception& e) {
        error = e.what();
    }

    using State = AsyncInitializationPolicy::State;

    std::lock_guard lock(_owner._lock);
    _owner._error = error;
    _owner._state = error.empty() ? State::DONE : State::FAILED;
}

}
