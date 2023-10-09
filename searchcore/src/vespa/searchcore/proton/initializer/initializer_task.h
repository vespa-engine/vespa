// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vector>

namespace proton::initializer {

/*
 * Class representign an initializer task, used to load a data
 * structure from disk during proton startup.
 */
class InitializerTask {
public:
    using SP = std::shared_ptr<InitializerTask>;
    using List = std::vector<SP>;
    enum class State {
        BLOCKED,
        RUNNING,
        DONE
    };
private:
    State           _state;
    List            _dependencies;
public:
    InitializerTask();
    virtual ~InitializerTask();
    State getState() const { return _state; }
    const List &getDependencies() const { return _dependencies; }
    void setRunning() { _state = State::RUNNING; }
    void setDone() { _state = State::DONE; }
    void addDependency(SP dependency);
    virtual void run() = 0;
    virtual size_t get_transient_memory_usage() const;
};

}
