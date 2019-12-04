// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_ThreadPool and FastOS_Thread methods.
 *
 * @author  Oivind H. Danielsen
 */

#include "thread.h"
#include <cstdio>
#include <cassert>

// ----------------------------------------------------------------------
// FastOS_ThreadPool
// ----------------------------------------------------------------------

FastOS_ThreadPool::FastOS_ThreadPool(int stackSize, int maxThreads)
    : _startedThreadsCount(0),
      _closeFlagMutex(),
      _stackSize(stackSize),
      _closeCalledFlag(false),
      _freeMutex(),
      _liveMutex(),
      _liveCond(),
      _freeThreads(nullptr),
      _activeThreads(nullptr),
      _numFree(0),
      _numActive(0),
      _numTerminated(0),
      _numLive(0),
      _maxThreads(maxThreads)   // 0 means unbounded
{
}

FastOS_ThreadPool::~FastOS_ThreadPool(void)
{
    Close();
}

void FastOS_ThreadPool::ThreadIsAboutToTerminate(FastOS_ThreadInterface *)
{
    assert(isClosed());

    std::lock_guard<std::mutex> guard(_liveMutex);

    _numTerminated++;
    _numLive--;
    if (_numLive == 0) {
        _liveCond.notify_all();
    }
}


// This is a NOP if the thread isn't active.
void FastOS_ThreadPool::FreeThread (FastOS_ThreadInterface *thread)
{
    std::lock_guard<std::mutex> guard(_freeMutex);

    if(thread->_active) {
        LinkOutThread(thread, &_activeThreads);

        thread->_active = false;
        _numActive--;

        LinkInThread(thread, &_freeThreads);
        _numFree++;
    }
}

void FastOS_ThreadPool::LinkOutThread (FastOS_ThreadInterface *thread, FastOS_ThreadInterface **listHead)
{
    if (thread->_prev != nullptr)
        thread->_prev->_next = thread->_next;
    if (thread->_next != nullptr)
        thread->_next->_prev = thread->_prev;

    if (thread == *listHead)
        *listHead = thread->_next;
}

void FastOS_ThreadPool::LinkInThread (FastOS_ThreadInterface *thread, FastOS_ThreadInterface **listHead)
{
    thread->_prev = nullptr;
    thread->_next = *listHead;

    if (*listHead != nullptr)
        (*listHead)->_prev = thread;

    *listHead  = thread;
}


// _freeMutex is held by caller.
void FastOS_ThreadPool::ActivateThread (FastOS_ThreadInterface *thread)
{
    LinkOutThread(thread, &_freeThreads);
    LinkInThread(thread, &_activeThreads);

    thread->_active = true;
    _numActive++;
    _startedThreadsCount++;
}


// Allocate a thread, either from pool of free or by 'new'.  Finally,
// make this thread call parameter fcn when it becomes active.
FastOS_ThreadInterface *FastOS_ThreadPool::NewThread (FastOS_Runnable *owner, void *arg)
{
    FastOS_ThreadInterface *thread=nullptr;

    std::unique_lock<std::mutex> freeGuard(_freeMutex);

    if (!isClosed()) {
        if ((thread = _freeThreads) != nullptr) {
            // Reusing thread entry
            _freeThreads = thread->_next;
            _numFree--;

            ActivateThread(thread);
        } else {
            // Creating new thread entry

            if (_maxThreads != 0 && ((_numActive + _numFree) >= _maxThreads)) {
                fprintf(stderr, "Error: Maximum number of threads (%d)"
                        " already allocated.\n", _maxThreads);
            } else {
                freeGuard.unlock();
                {
                    std::lock_guard<std::mutex> liveGuard(_liveMutex);
                    _numLive++;
                }
                thread = FastOS_Thread::CreateThread(this);

                if (thread == nullptr) {
                    std::lock_guard<std::mutex> liveGuard(_liveMutex);
                    _numLive--;
                    if (_numLive == 0) {
                        _liveCond.notify_all();
                    }
                }
                freeGuard.lock();

                if(thread != nullptr)
                    ActivateThread(thread);
            }
        }
    }

    freeGuard.unlock();
    if(thread != nullptr) {
        std::lock_guard<std::mutex> liveGuard(_liveMutex);
        thread->Dispatch(owner, arg);
    }

    return thread;
}


void FastOS_ThreadPool::BreakThreads ()
{
    FastOS_ThreadInterface *thread;

    std::lock_guard<std::mutex> freeGuard(_freeMutex);

    // Notice all active threads that they should quit
    for(thread=_activeThreads; thread != nullptr; thread=thread->_next) {
        thread->SetBreakFlag();
    }

    // Notice all free threads that they should quit
    for(thread=_freeThreads; thread != nullptr; thread=thread->_next) {
        thread->SetBreakFlag();
    }
}


void FastOS_ThreadPool::JoinThreads ()
{
    std::unique_lock<std::mutex> liveGuard(_liveMutex);
    while (_numLive > 0) {
        _liveCond.wait(liveGuard);
    }
}

void FastOS_ThreadPool::DeleteThreads ()
{
    FastOS_ThreadInterface *thread;

    std::lock_guard<std::mutex> freeGuard(_freeMutex);

    assert(_numActive == 0);
    assert(_numLive == 0);

    while((thread = _freeThreads) != nullptr) {
        LinkOutThread(thread, &_freeThreads);
        _numFree--;
        //      printf("deleting thread %p\n", thread);
        delete(thread);
    }

    assert(_numFree == 0);
}

void FastOS_ThreadPool::Close ()
{
    std::unique_lock<std::mutex> closeFlagGuard(_closeFlagMutex);
    if (!_closeCalledFlag) {
        _closeCalledFlag = true;
        closeFlagGuard.unlock();

        BreakThreads();
        JoinThreads();
        DeleteThreads();
    }
}

bool FastOS_ThreadPool::isClosed()
{
    std::lock_guard<std::mutex> closeFlagGuard(_closeFlagMutex);
    bool closed(_closeCalledFlag);
    return closed;
}

extern "C"
{
void *FastOS_ThreadHook (void *arg)
{
    FastOS_ThreadInterface *thread = static_cast<FastOS_ThreadInterface *>(arg);
    thread->Hook();

    return nullptr;
}
};


// ----------------------------------------------------------------------
// FastOS_ThreadInterface
// ----------------------------------------------------------------------

void FastOS_ThreadInterface::Hook ()
{
    // Loop forever doing the following:  Wait on the signal _dispatched.
    // When awoken, call _start_fcn  with the parameters.  Then zero set
    // things and return this to the owner, i.e. pool of free threads
    bool finished=false;
    bool deleteOnCompletion = false;

    while(!finished) {

        std::unique_lock<std::mutex> dispatchedGuard(_dispatchedMutex); // BEGIN lock
        while (_owner == nullptr && !(finished = _pool->isClosed())) {
            _dispatchedCond.wait(dispatchedGuard);
        }

        dispatchedGuard.unlock();           // END lock

        if(!finished) {
            PreEntry();
            deleteOnCompletion = _owner->DeleteOnCompletion();
            _owner->Run(this, _startArg);

            dispatchedGuard.lock();         // BEGIN lock

            if (deleteOnCompletion) {
                delete _owner;
            }
            _owner = nullptr;
            _startArg = nullptr;
            _breakFlag.store(false, std::memory_order_relaxed);
            finished = _pool->isClosed();

            dispatchedGuard.unlock();      // END lock

            {
                std::lock_guard<std::mutex> runningGuard(_runningMutex);
                _runningFlag = false;
                _runningCond.notify_all();
            }

            _pool->FreeThread(this);
            // printf("Thread given back to FastOS_ThreadPool: %p\n", this);
        }
    }

    _pool->ThreadIsAboutToTerminate(this);

    // Be sure not to touch any members from here on, as we are about
    // to be deleted.
}


// Make this thread call parameter fcn with parameters argh
// when this becomes active.
// Restriction: _liveCond must be held by the caller.

void FastOS_ThreadInterface::Dispatch(FastOS_Runnable *newOwner, void *arg)
{
    std::lock_guard<std::mutex> dispatchedGuard(_dispatchedMutex);

    {
        std::unique_lock<std::mutex> runningGuard(_runningMutex);
        while (_runningFlag) {
            _runningCond.wait(runningGuard);
        }
        _runningFlag = true;
    }

    _owner = newOwner;
    _startArg = arg;
    // Set _thread variable before NewThread returns
    _owner->_thread = this;

    // It is safe to signal after the unlock since _liveCond is still held
    // so the signalled thread still exists.
    // However as thread creation is infrequent and as helgrind suggest doing
    // it the safe way we just do that, instead of keeping a unneccessary long
    // suppressionslist. It will be long enough anyway.

    _dispatchedCond.notify_one();
}

void FastOS_ThreadInterface::SetBreakFlag()
{
    std::lock_guard<std::mutex> dispatchedGuard(_dispatchedMutex);
    _breakFlag.store(true, std::memory_order_relaxed);
    _dispatchedCond.notify_one();
}


FastOS_ThreadInterface *FastOS_ThreadInterface::CreateThread(FastOS_ThreadPool *pool)
{
    FastOS_ThreadInterface *thread = new FastOS_Thread(pool);

    if(!thread->Initialize(pool->GetStackSize(), pool->GetStackGuardSize())) {
        delete(thread);
        thread = nullptr;
    }

    return thread;
}

void FastOS_ThreadInterface::Join ()
{
    std::unique_lock<std::mutex> runningGuard(_runningMutex);
    while (_runningFlag) {
        _runningCond.wait(runningGuard);
    }
}


// ----------------------------------------------------------------------
// FastOS_Runnable
// ----------------------------------------------------------------------

FastOS_Runnable::FastOS_Runnable()
    : _thread(nullptr)
{
}

FastOS_Runnable::~FastOS_Runnable()
{
    //      assert(_thread == nullptr);
}
