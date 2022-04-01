// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadproxy.h"
#include "common.h"
#include <dlfcn.h>
#include <pthread.h>
#include <cstdio>

namespace vespamalloc {

IAllocator * _G_myMemP = nullptr;

void setAllocatorForThreads(IAllocator * allocator)
{
    _G_myMemP = allocator;
}

}
extern "C" {

typedef void * (*VoidpFunctionVoidp) (void *);
class ThreadArg
{
public:
    ThreadArg(VoidpFunctionVoidp func, void * arg) : _func(func), _arg(arg) { }
    VoidpFunctionVoidp  _func;
    void               * _arg;
};

typedef int (*pthread_create_function) (pthread_t *thread,
                                        const pthread_attr_t *attr,
                                        VoidpFunctionVoidp start_routine,
                                        void *arg);

int linuxthreads_pthread_getattr_np(pthread_t pid, pthread_attr_t *dst);

static void * _G_mallocThreadProxyReturnAddress = nullptr;
static std::atomic<size_t> _G_threadCount(1);  // You always have the main thread.

static void cleanupThread(void * arg)
{
    ThreadArg * ta = (ThreadArg *) arg;
    delete ta;
    vespamalloc::_G_myMemP->quitThisThread();
    vespamalloc::Mutex::subThread();
    _G_threadCount.fetch_sub(1);
}

void * mallocThreadProxy (void * arg)
{
    ThreadArg * ta = (ThreadArg *) arg;

    void * tempReturnAddress = __builtin_return_address(0);
    ASSERT_STACKTRACE((_G_mallocThreadProxyReturnAddress == nullptr) || (_G_mallocThreadProxyReturnAddress == tempReturnAddress));
    _G_mallocThreadProxyReturnAddress = tempReturnAddress;
    vespamalloc::_G_myMemP->setReturnAddressStop(tempReturnAddress);

    vespamalloc::Mutex::addThread();
    vespamalloc::_G_myMemP->initThisThread();
    void * result = nullptr;
    DEBUG(fprintf(stderr, "arg(%p=%p), local(%p=%p)\n", &arg, arg, &ta, ta));

    pthread_cleanup_push(cleanupThread, ta);
        result = (*ta->_func)(ta->_arg);
    pthread_cleanup_pop(1);

    return result;
}


extern "C" VESPA_DLL_EXPORT int
local_pthread_create (pthread_t *thread,
                      const pthread_attr_t *attrOrg,
                      void * (*start_routine) (void *),
                      void * arg) __asm__("pthread_create");

VESPA_DLL_EXPORT int
local_pthread_create (pthread_t *thread,
                      const pthread_attr_t *attrOrg,
                      void * (*start_routine) (void *),
                      void * arg)
{
    size_t numThreads = _G_threadCount;
    while ((numThreads < vespamalloc::_G_myMemP->getMaxNumThreads())
           && ! _G_threadCount.compare_exchange_strong(numThreads, numThreads+1))
    { }

    if (numThreads >= vespamalloc::_G_myMemP->getMaxNumThreads()) {
#if 1
        // Just abort when max threads are reached.
        // Future option is to make the behaviour optional.
        fprintf (stderr, "All %ld threads are active! Aborting so you can start again.\n", numThreads);
        abort();
#else
        return EAGAIN;
#endif
    }
    // A pointer to the library version of pthread_create.
    static pthread_create_function real_pthread_create = nullptr;

    const char * pthread_createName = "pthread_create";

    if (real_pthread_create == nullptr) {
        real_pthread_create = (pthread_create_function) dlsym (RTLD_NEXT, pthread_createName);
        if (real_pthread_create == nullptr) {
            fprintf (stderr, "Could not find the pthread_create function!\n");
            abort();
        }
    }

    ThreadArg * args = new ThreadArg(start_routine, arg);
    pthread_attr_t locAttr;
    pthread_attr_t *attr(const_cast<pthread_attr_t *>(attrOrg));
    if (attr == nullptr) {
        pthread_attr_init(&locAttr);
        attr = &locAttr;
    }

    vespamalloc::_G_myMemP->enableThreadSupport();
    int retval = (*real_pthread_create)(thread, attr, mallocThreadProxy, args);

    return retval;
}

}
