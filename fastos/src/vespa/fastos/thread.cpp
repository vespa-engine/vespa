// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_ThreadPool and FastOS_Thread methods.
 *
 * @author  Oivind H. Danielsen
 */

#include <vespa/fastos/thread.h>

// ----------------------------------------------------------------------
// FastOS_ThreadPool
// ----------------------------------------------------------------------

FastOS_ThreadPool::FastOS_ThreadPool(int stackSize, int maxThreads)
    : _startedThreadsCount(0),
      _closeFlagMutex(),
      _stackSize(stackSize),
      _closeCalledFlag(false),
      _freeMutex(),
      _liveCond(),
      _freeThreads(NULL),
      _activeThreads(NULL),
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

    _liveCond.Lock();

    _numTerminated++;
    _numLive--;
    if (_numLive == 0)
        _liveCond.Broadcast();

    _liveCond.Unlock();
}


// This is a NOP if the thread isn't active.
void FastOS_ThreadPool::FreeThread (FastOS_ThreadInterface *thread)
{
    _freeMutex.Lock();

    if(thread->_active) {
        LinkOutThread(thread, &_activeThreads);

        thread->_active = false;
        _numActive--;

        LinkInThread(thread, &_freeThreads);
        _numFree++;
    }

    _freeMutex.Unlock();
}

void FastOS_ThreadPool::LinkOutThread (FastOS_ThreadInterface *thread, FastOS_ThreadInterface **listHead)
{
    if (thread->_prev != NULL)
        thread->_prev->_next = thread->_next;
    if (thread->_next != NULL)
        thread->_next->_prev = thread->_prev;

    if (thread == *listHead)
        *listHead = thread->_next;
}

void FastOS_ThreadPool::LinkInThread (FastOS_ThreadInterface *thread, FastOS_ThreadInterface **listHead)
{
    thread->_prev = NULL;
    thread->_next = *listHead;

    if (*listHead != NULL)
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
    FastOS_ThreadInterface *thread=NULL;

    _freeMutex.Lock();

    if (!isClosed()) {
        if ((thread = _freeThreads) != NULL) {
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
                _freeMutex.Unlock();

                _liveCond.Lock();
                _numLive++;
                _liveCond.Unlock();

                thread = FastOS_Thread::CreateThread(this);

                if (thread == NULL) {
                    _liveCond.Lock();
                    _numLive--;
                    if (_numLive == 0) {
                        _liveCond.Broadcast();
                    }
                    _liveCond.Unlock();
                }

                _freeMutex.Lock();

                if(thread != NULL)
                    ActivateThread(thread);
            }
        }
    }

    _freeMutex.Unlock();
    if(thread != NULL) {
        _liveCond.Lock();
        thread->Dispatch(owner, arg);
        _liveCond.Unlock();
    }

    return thread;
}


void FastOS_ThreadPool::BreakThreads ()
{
    FastOS_ThreadInterface *thread;

    _freeMutex.Lock();

    // Notice all active threads that they should quit
    for(thread=_activeThreads; thread != NULL; thread=thread->_next) {
        thread->SetBreakFlag();
    }

    // Notice all free threads that they should quit
    for(thread=_freeThreads; thread != NULL; thread=thread->_next) {
        thread->SetBreakFlag();
    }

    _freeMutex.Unlock();
}


void FastOS_ThreadPool::JoinThreads ()
{
    _liveCond.Lock();

    while (_numLive > 0)
        _liveCond.Wait();

    _liveCond.Unlock();
}

void FastOS_ThreadPool::DeleteThreads ()
{
    FastOS_ThreadInterface *thread;

    _freeMutex.Lock();

    assert(_numActive == 0);
    assert(_numLive == 0);

    while((thread = _freeThreads) != NULL) {
        LinkOutThread(thread, &_freeThreads);
        _numFree--;
        //      printf("deleting thread %p\n", thread);
        delete(thread);
    }

    assert(_numFree == 0);

    _freeMutex.Unlock();
}

void FastOS_ThreadPool::Close ()
{
    _closeFlagMutex.Lock();
    if (!_closeCalledFlag) {
        _closeCalledFlag = true;
        _closeFlagMutex.Unlock();

        BreakThreads();
        JoinThreads();
        DeleteThreads();
    } else {
        _closeFlagMutex.Unlock();
    }
}

bool FastOS_ThreadPool::isClosed()
{
    _closeFlagMutex.Lock();
    bool closed(_closeCalledFlag);
    _closeFlagMutex.Unlock();
    return closed;
}

extern "C"
{
void *FastOS_ThreadHook (void *arg)
{
    FastOS_ThreadInterface *thread = static_cast<FastOS_ThreadInterface *>(arg);
    thread->Hook();

    return NULL;
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

        _dispatched.Lock();             // BEGIN lock

        while (_owner == NULL && !(finished = _pool->isClosed())) {
            _dispatched.Wait();
        }

        _dispatched.Unlock();           // END lock

        if(!finished) {
            PreEntry();
            deleteOnCompletion = _owner->DeleteOnCompletion();
            _owner->Run(this, _startArg);

            _dispatched.Lock();         // BEGIN lock

            if (deleteOnCompletion) {
                delete _owner;
            }
            _owner = NULL;
            _startArg = NULL;
            _breakFlag = false;
            finished = _pool->isClosed();

            _dispatched.Unlock();               // END lock

            _runningCond.ClearBusyBroadcast();

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
    _dispatched.Lock();

    _runningCond.SetBusy();

    _owner = newOwner;
    _startArg = arg;
    // Set _thread variable before NewThread returns
    _owner->_thread = this;

    // It is safe to signal after the unlock since _liveCond is still held
    // so the signalled thread still exists.
    // However as thread creation is infrequent and as helgrind suggest doing
    // it the safe way we just do that, instead of keeping a unneccessary long
    // suppressionslist. It will be long enough anyway.

    _dispatched.Signal();

    _dispatched.Unlock();
}

void FastOS_ThreadInterface::SetBreakFlag()
{
    _dispatched.Lock();
    _breakFlag = true;

    _dispatched.Signal();
    _dispatched.Unlock();
}


FastOS_ThreadInterface *FastOS_ThreadInterface::CreateThread(FastOS_ThreadPool *pool)
{
    FastOS_ThreadInterface *thread = new FastOS_Thread(pool);

    if(!thread->Initialize(pool->GetStackSize(), pool->GetStackGuardSize())) {
        delete(thread);
        thread = NULL;
    }

    return thread;
}

void FastOS_ThreadInterface::Join ()
{
    _runningCond.WaitBusy();
}


// ----------------------------------------------------------------------
// FastOS_Runnable
// ----------------------------------------------------------------------

FastOS_Runnable::FastOS_Runnable(void)
    : _thread(NULL)
{
}

FastOS_Runnable::~FastOS_Runnable(void)
{
    //      assert(_thread == NULL);
}

void FastOS_Runnable::Detach(void)
{
    _thread = NULL;
}
