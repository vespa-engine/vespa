// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "imessagehandler.h"
#include "ireplyhandler.h"
#include "message.h"
#include "reply.h"
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <condition_variable>
#include <mutex>

namespace mbus {

/**
 * This class implements a single thread that is able to process arbitrary
 * tasks. Tasks are enqueued using the synchronized {@link #enqueue(Task)}
 * method, and are run in the order they were enqueued.
 */
class Messenger {
public:
    /**
     * Defines the required interface for tasks to be posted to this worker.
     */
    class ITask : public vespalib::Executor::Task {
    protected:
        ITask() = default;
    public:
        ITask(const ITask &) = delete;
        ITask & operator = (const ITask &) = delete;
        /**
         * Convenience typedefs.
         */
        using UP = std::unique_ptr<ITask>;

        /**
         * Returns the priority of this task.
         */
        virtual uint8_t priority() const = 0;
    };

private:
    mutable std::mutex           _lock;
    std::condition_variable      _cond;
    std::vector<ITask*>          _children;
    vespalib::ArrayQueue<ITask*> _queue;
    bool                         _closed;
    std::thread                  _thread;
    
protected:
    void run();

public:
    Messenger();

    /**
     * Frees any allocated resources. Also destroys all queued tasks.
     */
    ~Messenger();

    /**
     * Adds a recurrent task to this that is to be run for every iteration of
     * the main loop. This task must be very light-weight as to not block the
     * messenger. This method is thread-safe.
     *
     * @param task The task to add.
     */
    void addRecurrentTask(ITask::UP task);

    /**
     * Discards all the recurrent tasks previously added to using the {@link
     * #addRecurrentTask(ITask)} method.  This method is thread-safe.
     */
    void discardRecurrentTasks();

    /**
     * Starts the internal thread. This must be done AFTER all recurrent tasks
     * have been added.
     *
     * @return True if the thread was started.
     * @see #addRecurrentTask(ITask)
     */
    bool start();

    /**
     * Handshakes with the internal thread. If this method is called using the
     * messenger thread, this will deadlock.
     */
    void sync();

    /**
     * Convenience method to post a {@link MessageTask} to the queue of tasks to
     * be executed.
     *
     * @param msg     The message to send.
     * @param handler The handler to send to.
     */
    void deliverMessage(Message::UP msg, IMessageHandler &handler);

    /**
     * Convenience method to post a {@link ReplyTask} to the queue of tasks to
     * be executed.
     *
     * @param reply   The reply to return.
     * @param handler The handler to return to.
     */
    void deliverReply(Reply::UP reply, IReplyHandler &handler);

    /**
     * Enqueues the given task in the list of tasks that this worker is to
     * process. If this thread has been destroyed previously, this method
     * invokes {@link Messenger.Task#destroy()}.
     *
     * @param task The task to enqueue.
     */
    void enqueue(ITask::UP task);

    /**
     * Returns whether or not there are any tasks queued for execution.
     *
     * @return True if there are no tasks.
     */
    bool isEmpty() const;
};

} // namespace mbus

