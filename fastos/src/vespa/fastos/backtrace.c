// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "backtrace.h"

#if defined(__i386__) || defined(__clang__) || defined(__aarch64__)
// use GLIBC version, hope it works
extern int backtrace(void **buffer, int size);
#define HAVE_BACKTRACE
#endif

#if defined(__x86_64__) && !defined(__clang__)

/**
   Written by Arne H. J. based on docs:

   http://www.kernel.org/pub/linux/devel/gcc/unwind/
   http://www.codesourcery.com/public/cxx-abi/abi-eh.html
   http://refspecs.freestandards.org/LSB_3.1.0/LSB-Core-generic/LSB-Core-generic/libgcc-s-ddefs.html
**/

#include <unwind.h>

struct trace_context {
    void **array;
    int size;
    int index;
};

static _Unwind_Reason_Code
trace_fn(struct _Unwind_Context *ctxt, void *arg)
{
    struct trace_context *tp = (struct trace_context *)arg;
    void *ip = (void *)_Unwind_GetIP(ctxt);

    if (ip == 0) {
        return _URC_END_OF_STACK;
    }
    if (tp->index <= tp->size) {
        // there's no point filling in the address of the backtrace()
        // function itself, that doesn't provide any extra information,
        // so skip one level
        if (tp->index > 0) {
            tp->array[tp->index - 1] = ip;
        }
        tp->index++;
    } else {
        return _URC_NORMAL_STOP;
    }
    return _URC_NO_REASON; // "This is not the destination frame" -> try next frame
}

#define HAVE_BACKTRACE
int
backtrace (void **array, int size)
{
    struct trace_context t;
    t.array = array;
    t.size = size;
    t.index = 0;
    _Unwind_Backtrace(trace_fn, &t);
    return t.index - 1;
}
#endif // x86_64


#ifdef HAVE_BACKTRACE

int
FastOS_backtrace (void **array, int size)
{
    return backtrace(array, size);
}

#else

# warning "backtrace not supported on this CPU"
int
FastOS_backtrace (void **array, int size) 
{
    (void) array;
    (void) size;
    return 0;
}

#endif
