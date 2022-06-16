// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::Runnable
 * @ingroup util
 *
 * @brief Implementation of FastOS_Runnable that implements threadsafe stop.
 *
 * FastOS_Runnable can easily be used unsafe. If you use the thread pointer for
 * anything after your runnable had returned from Run(), it could affect another
 * runnable now using that thread.
 *
 * Using this class should be foolproof to avoid synchronization issues during
 * thread starting and stopping :)
 *
 * @author H�kon Humberset
 * @date 2005-09-19
 */

#pragma once

#include <vespa/fastos/thread.h>
#include <atomic>

namespace document {

class Runnable : private FastOS_Runnable {
public:
    enum State { NOT_RUNNING, STARTING, RUNNING, STOPPING };

private:
    mutable std::mutex _stateLock;
    mutable std::condition_variable _stateCond;
    std::atomic<State> _state;

    void Run(FastOS_ThreadInterface*, void*) override;
    void set_state(State new_state) noexcept; // _stateLock must be held
public:
    /**
     * Create a runnable.
     * @param pool If set, runnable will be started in constructor.
     */
    Runnable();
    ~Runnable() override;

    /**
     * Start this runnable.
     * @param pool The threadpool from which a thread is acquired.
     * @return True if thread was started, false if thread was already running.
     */
    bool start(FastOS_ThreadPool& pool);

    /**
     * Stop this runnable.
     * @return True if thread was stopped, false if thread was not running.
     */
    bool stop();

    /**
     * Called in stop(). Implement, to for instance notify any monitors that
     * can be waiting.
     */
    virtual bool onStop();

    /**
     * Wait for this thread to finish, if it is in the process of stopping.
     * @return True if thread finished (or not running), false if thread is
     *              running normally and no stop is scheduled.
     */
    bool join() const;

    /**
     * Implement this to make the runnable actually do something.
     */
    virtual void run() = 0;

    /**
     * Get the current state of this runnable.
     * Thread safe (but relaxed) read; may be stale if done outside _stateLock.
     */
    [[nodiscard]] State getState() const noexcept {
        return _state.load(std::memory_order_relaxed);
    }

    /** Check if system is in the process of stopping. */
    [[nodiscard]] bool stopping() const noexcept;

    /**
     * Checks if runnable is running or not. (Started is considered running)
     */
    [[nodiscard]] bool running() const noexcept;

    FastOS_ThreadId native_thread_id() const noexcept;
};

}

