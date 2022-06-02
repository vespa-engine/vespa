// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definitions for FastOS_ThreadPool, FastOS_ThreadInterface and
 * FastOS_Runnable.
 *
 * @author  Oivind H. Danielsen
 */

#pragma once


#include "types.h"
#include <atomic>
#include <mutex>
#include <condition_variable>

typedef pthread_t FastOS_ThreadId;

class FastOS_Runnable;
class FastOS_ThreadInterface;


/**
 * This class implements an initially empty pool of threads.
 *
 * As threads are allocated with @ref NewThread() the number of
 * threads in the pool increases. A maximum number of threads
 * contained in the pool can be set using the constructor
 * FastOS_ThreadPool(int maxThreads).
 *
 * Threads are automatically returned to the pool when they
 * terminate.
 */
class FastOS_ThreadPool
{
    friend class FastOS_ThreadInterface;

private:
    FastOS_ThreadPool(const FastOS_ThreadPool&);
    FastOS_ThreadPool& operator=(const FastOS_ThreadPool&);

    int _startedThreadsCount;
    std::mutex _closeFlagMutex;
    /**
     * The stack size for threads in this pool.
     */
    const int _stackSize;
    bool _closeCalledFlag;

    // Always lock in this order
    mutable std::mutex _freeMutex;
    std::mutex _liveMutex;
    std::condition_variable _liveCond;
    /**
     * List of free (available) threads.
     */
    FastOS_ThreadInterface *_freeThreads;

    /**
     * List of active (allocated) threads.
     */
    FastOS_ThreadInterface *_activeThreads;

    /**
     * Number of available threads in the threadpool.
     * Total number of threads = free + active
     */
    int _numFree;

    /**
     * Number of active threads in the threadpool.
     * Total number of threads = free + active
     */
    int _numActive;

    /**
     * Number of threads that have terminated
     */
    int _numTerminated;

    /**
     * Number of threads that have not been terminated
     */
    int _numLive;

    /**
     * Maximum number of threads in the threadpool. A value of
     * zero means that there is no limit.
     */
    int _maxThreads;

    /**
     * Put this thread on the @ref _activeThreads list.
     */
    void ActivateThread (FastOS_ThreadInterface *thread);

    /**
     * Return previously active thread to the list of free thread.
     */
    void FreeThread (FastOS_ThreadInterface *thread);

    /**
     * A thread is informing the thread pool that it is about to
     * terminate.
     */
    void ThreadIsAboutToTerminate(FastOS_ThreadInterface *thread);

    /**
     * Set the break flag on all threads.
     */
    void BreakThreads ();

    /**
     * Wait for all threads to finish.
     */
    void JoinThreads ();

    /**
     * Delete all threads in threadpool.
     */
    void DeleteThreads ();

    /**
     * Remove a thread from a list.
     */
    void LinkOutThread (FastOS_ThreadInterface *thread,
                        FastOS_ThreadInterface **listHead);

    /**
     * Add a thread to a list. Notice that a thread can be on only one
     * list at a time.
     */
    void LinkInThread (FastOS_ThreadInterface *thread,
                       FastOS_ThreadInterface **listHead);

public:
    /**
     * Create a threadpool that can hold a maximum of [maxThreads] threads.
     * @param  stackSize    The stack size for threads in this pool should
     *                      be this many bytes.
     * @param  maxThreads   Maximum number of threads in threadpool.
     *                      (0 == no limit).
     */
    FastOS_ThreadPool(int stackSize, int maxThreads=0);

    /**
     * Destructor. Closes pool if necessary.
     */
    virtual ~FastOS_ThreadPool();


    /**
     * Allocate a new thread, and make this thread invoke the Run() method
     * of the @ref FastOS_Runnable object [owner] with parameters [arg].
     * The thread is automatically freed (returned to the treadpool)
     * when Run() returns.
     *
     * @param owner     Instance to be invoked by new thread.
     * @param arg       Arguments to be passed to new thread.
     *
     * @return          Pointer to newly created thread or nullptr on failure.
     */
    FastOS_ThreadInterface *NewThread (FastOS_Runnable *owner, void *arg=nullptr);

    /**
     * Get the stack size used for threads in this pool.
     * @return          Stack size in bytes.
     */
    int GetStackSize() const { return _stackSize; }

    int GetStackGuardSize() const { return 0; }

    /**
     * Close the threadpool. This involves setting the break flag on
     * all active threads, and waiting for them to finish. Once Close
     * is called, no more threads can be allocated from the thread
     * pool. There exists no way to reopen a closed threadpool.
     */
    void Close ();

    /**
     *  This will tell if the pool has been closed.
     */
    bool isClosed();

    /**
     * Get the number of currently active threads.
     * The total number of actual allocated threads is the sum of
     * @ref GetNumActiveThreads() and @ref GetNumInactiveThreads().
     * @return   Number of currently active threads
     */
    int GetNumActiveThreads () const {
        std::lock_guard<std::mutex> guard(_freeMutex);
        return _numActive;
    }

    /**
     * Get the number of currently inactive threads.
     * The total number of actual allocated threads is the sum of
     * @ref GetNumActiveThreads() and @ref GetNumInactiveThreads().
     * @return   Number of currently inactive threads
     */
    int GetNumInactiveThreads () const {
        std::lock_guard<std::mutex> guard(_freeMutex);
        return _numFree;
    }

    /**
     * Get the number of started threads since instantiation of the thread pool.
     * @return   Number of threads started
     */
    int GetNumStartedThreads () const { return _startedThreadsCount; }
};


// Operating system thread entry point
extern "C" {
    void *FastOS_ThreadHook (void *arg);
}

/**
 * This class controls each operating system thread.
 *
 * In most cases you would not want to create objects of this class
 * directly. Use @ref FastOS_ThreadPool::NewThread() instead.
 */
class FastOS_ThreadInterface
{
    friend class FastOS_ThreadPool;
    friend void *FastOS_ThreadHook (void *arg);

private:
    FastOS_ThreadInterface(const FastOS_ThreadInterface&);
    FastOS_ThreadInterface& operator=(const FastOS_ThreadInterface&);

protected:
    /**
     * The thread does not start (call @ref FastOS_Runnable::Run())
     * until this event has been triggered.
     */
    std::mutex              _dispatchedMutex;
    std::condition_variable _dispatchedCond;

    FastOS_ThreadInterface *_next;
    FastOS_ThreadInterface *_prev;

    /**
     * A pointer to the instance which implements the interface
     * @ref FastOS_Runnable.
     */
    FastOS_Runnable *_owner;

    /**
     * A pointer to the originating @ref FastOS_ThreadPool
     */
    FastOS_ThreadPool *_pool;

    /**
     * Entry point for the OS thread. The thread will sleep here
     * until dispatched.
     */
    void Hook ();

    /**
     * Signals that thread should be dispatched.
     * @param owner   Instance of @ref FastOS_Runnable.
     * @param arg     Thread invocation arguments.
     */
    void Dispatch (FastOS_Runnable *owner, void *arg);

    /**
     * This method is called prior to invoking @ref FastOS_Runnable::Run().
     * Usually this involves setting operating system thread attributes,
     * and is handled by each operating specific subclass.
     */
    virtual void PreEntry ()=0;

    /**
     * Initializes a thread. This includes creating the operating system
     * socket handle and setting it up and making it ready to be dispatched.
     * @return   Boolean success/failure
     */
    virtual bool Initialize (int stackSize, int stackGuardSize)=0;

    /**
     * Used to store thread invocation arguments. These are passed along
     * to @ref FastOS_Runnable::Run() when the thread is dispatched.
     */
    void *_startArg;

    /**
     * Create an operating system thread. In most cases you would want
     * to create threads using @ref FastOS_ThreadPool::NewThread() instead.
     * @param pool  The threadpool which is about to contain the new thread.
     * @return      A new @ref FastOS_Thread or nullptr on failure.
     */
    static FastOS_ThreadInterface *CreateThread(FastOS_ThreadPool *pool);

    /**
     * Break flag. If true, the thread should exit.
     */
    std::atomic<bool> _breakFlag;

    /**
     * Is this thread active or free in the threadpool?
     */
    bool _active;

    /**
     * Is the thread running? This is used by @ref Join(), to wait for threads
     * to finish.
     */
    std::mutex              _runningMutex;
    std::condition_variable _runningCond;
    bool                    _runningFlag;

public:
    /**
     * Constructor. Resets internal attributes.
     */
    FastOS_ThreadInterface (FastOS_ThreadPool *pool)
        : _dispatchedMutex(),
          _dispatchedCond(),
          _next(nullptr),
          _prev(nullptr),
          _owner(nullptr),
          _pool(pool),
          _startArg(nullptr),
          _breakFlag(false),
          _active(false),
          _runningMutex(),
          _runningCond(),
          _runningFlag(false)
    {
    }

    /**
     * Destructor.
     */
    virtual ~FastOS_ThreadInterface () {}

    /**
     * Instruct a thread to exit. This could be used in conjunction with
     * @ref GetBreakFlag() in a worker thread, to have cooperative thread
     * termination. When a threadpool closes, all threads in the pool will
     * have their break flag set.
     */
    void SetBreakFlag ();

    /**
     * Return the status of this thread's break flag. If the break flag
     * is set, someone wants the thread to terminate. It is up to the
     * implementor of the thread to decide whether the break flag
     * should be used.
     *
     * In scenarios where a worker thread loops "forever" waiting for
     * new jobs, the break flag should be polled in order to eventually
     * exit from the loop and terminate the thread.
     *
     * In scenarios where a worker thread performs a task which
     * always should run to completion, the break flag could be ignored
     * as the thread sooner or later will terminate.
     *
     * When a threadpool is closed, the break flag is set on all
     * threads in the pool. If a thread loops forever and chooses to
     * ignore the break flag, a @ref FastOS_ThreadPool::Close() will
     * never finish. (see @ref SetBreakFlag)
     */
    bool GetBreakFlag () const
    {
        return _breakFlag.load(std::memory_order_relaxed);
    }

    /**
     * Wait for a thread to finish.
     */
    void Join ();

    /**
     * Returns the id of this thread.
     */
    virtual FastOS_ThreadId GetThreadId () const noexcept = 0;
};


/**
 * This class gives a generic interface for invoking new threads with an object.
 *
 * The thread object should inherit this interface (class), and implement
 * the @ref Run() method. When @ref FastOS_ThreadPool::NewThread() is
 * called, the @ref Run() method of the passed instance will be invoked.
 *
 * Arguments could be supplied via @ref FastOS_ThreadPool::NewThread(), but
 * it is also possible to supply arguments to the new thread through the
 * worker thread object constructor or some other attribute-setting method
 * prior to creating the thread. Choose whichever method works best for you.
 *
 * Example:
 * @code
 *   // Arguments passed to the new thread.
 *   struct MyThreadArgs
 *   {
 *      int _something;
 *      char _tenChars[10];
 *   };
 *
 *   class MyWorkerThread : public FastOS_Runnable
 *   {
 *   public:
 *
 *      // Delete this instance upon completion
 *      virtual bool DeleteOnCompletion() const { return true; }
 *
 *      virtual void Run (FastOS_ThreadInterface *thread, void *arguments)
 *      {
 *         MyThreadArgs *args = static_cast<MyThreadArgs *>(arguments);
 *
 *         // Do some computation...
 *         Foo(args->_something);
 *
 *         for(int i=0; i<30000; i++)
 *         {
 *            ...
 *            ...
 *
 *            if(thread->GetBreakFlag())
 *               break;
 *            ...
 *            ...
 *
 *         }
 *
 *         // Thread terminates...
 *      }
 *   };
 *
 *
 *   // Example on how to create a thread using the above classes.
 *   void SomeClass::SomeMethod (FastOS_ThreadPool *pool)
 *   {
 *      MyWorkerThread *workerThread = new MyWorkerThread();
 *      static MyThreadArgs arguments;
 *
 *      arguments._something = 123456;
 *
 *      // the workerThread instance will be deleted when Run completes
 *      // see the DeleteOnCompletion doc
 *      pool->NewThread(workerThread, &arguments);
 *   }
 * @endcode
 */
class FastOS_Runnable
{
private:
    friend class FastOS_ThreadInterface;
    FastOS_ThreadInterface *_thread;

public:
    FastOS_Runnable(const FastOS_Runnable&) = delete;
    FastOS_Runnable& operator=(const FastOS_Runnable&) = delete;
    FastOS_Runnable();
    virtual ~FastOS_Runnable();

    /**
     * The DeleteOnCompletion method should be overridden to return true
     * if the runnable instance should be deleted when run completes
     *
     * @author Nils Sandoy
     * @return true iff this runnable instance should be deleted on completion
     */
    virtual bool DeleteOnCompletion() const { return false; }

    /**
     * When an object implementing interface @ref FastOS_Runnable is used to
     * create a thread, starting the thread causes the object's @ref Run()
     * method to be called in that separately executing thread. The thread
     * terminates when @ref Run() returns.
     * @param  thisThread  A thread object.
     * @param  arguments   Supplied to @ref FastOS_ThreadPool::NewThread
     */
    virtual void Run(FastOS_ThreadInterface *thisThread, void *arguments)=0;

    FastOS_ThreadInterface *GetThread()             { return _thread; }
    const FastOS_ThreadInterface *GetThread() const { return _thread; }
    bool HasThread()                          const { return _thread != nullptr; }
};

#include <vespa/fastos/unix_thread.h>
typedef FastOS_UNIX_Thread FASTOS_PREFIX(Thread);

