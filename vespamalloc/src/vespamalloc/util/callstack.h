// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdio.h>
#include <dlfcn.h>
#include <limits>
#include </usr/include/execinfo.h>
#include <vespamalloc/util/stream.h>

namespace vespamalloc {

const void * dlNextSym(const void * f);
const char * dlAddr(const void * addr);

class StackReturnEntry {
public:
    StackReturnEntry(const void * returnAddress = NULL,
                     const void * stack=NULL)
        : _return(returnAddress)
    {
        (void) stack;
    }
    int cmp(const StackReturnEntry & b) const {
        return (size_t(_return) - size_t(b._return));
    }
    void info(FILE * os) const;
    bool valid() const { return _return != NULL; }
    bool valid(const void * stopAddr) const { return valid() && (_return != stopAddr); }
    bool valid(const void * stopAddrMin, const void * stopAddrMax) const { return valid() && ! ((stopAddrMin <= _return) && (_return < stopAddrMax)); }
private:
    friend asciistream & operator << (asciistream & os, const StackReturnEntry & v);
    const void * _return;
};

class StackFrameReturnEntry {
public:
    StackFrameReturnEntry(const void * returnAddress = NULL,
                          const void * stack = NULL)
        : _return(returnAddress),
          _stack(stack)
    { }
    int cmp(const StackFrameReturnEntry & b) const {
        int diff (size_t(_return) - size_t(b._return));
        if (diff == 0) {
            diff = size_t(_stack) - size_t(b._stack);
        }
        return diff;
    }
    friend asciistream & operator << (asciistream & os, const StackFrameReturnEntry & v);
    void info(FILE * os) const;
private:
    const void * _return;
    const void * _stack;
};

template <typename StackRep>
class StackEntry {
public:
    StackEntry(const void * returnAddress = NULL,
               const void * stack = NULL)
        : _stackRep(returnAddress, stack)
    { }
    bool operator == (const StackEntry & b) const { return cmp(b) == 0; }
    bool operator < (const StackEntry & b)  const { return cmp(b) < 0; }
    bool operator > (const StackEntry & b)  const { return cmp(b) > 0; }
    void info(FILE * os)                    const { _stackRep.info(os); }
    bool valid()                            const { return _stackRep.valid(_stopAddr); }
    static size_t fillStack2(StackEntry *stack, size_t nelems);
    static size_t fillStack(StackEntry *stack, size_t nelems);
    static void setStopAddress(const void * stopAddr) { _stopAddr = stopAddr; }
private:
    int cmp(const StackEntry & b) const { return _stackRep.cmp(b._stackRep); }
    friend asciistream & operator << (asciistream & os, const StackEntry<StackRep> & v) {
        return os << v._stackRep;
    }
    StackRep _stackRep;
    static const void * _stopAddr;
};

template <typename S, int N>
inline bool generateStackEntry(S & stack)
{
    void * s = __builtin_frame_address(N);
    void * r(NULL);
    if (s && (s > (void*)0x1000000) && (s < (void *)(std::numeric_limits<long>::max()))) {
      r = __builtin_return_address(N);
      stack = S(r, s);
    } else {
      stack = S(0, 0);
    }
    return (r == NULL) || (s == NULL);
}

#define CASESTACK(n) \
  case n: {                                   \
    done = generateStackEntry< StackEntry<StackRep>, n >(stack[n]);   \
    break;                                    \
  }

template <typename StackRep>
const void * StackEntry<StackRep>::_stopAddr = NULL;

template <typename StackRep>
size_t StackEntry<StackRep>::fillStack(StackEntry<StackRep> *stack, size_t nelems)
{
    void * retAddr[nelems];
    int sz = backtrace(retAddr, nelems);
    if ((sz > 0) && (size_t(sz) <= nelems)) {
        for(int i(1); i < sz; i++) {
            StackEntry<StackRep> entry(retAddr[i], NULL);
            if (entry.valid()) {
                stack[i-1] = entry;
            } else {
                sz = i;
            }
        }
        sz -= 1;  // Do not count self
    } else {
        sz = 0;
    }
    return sz;
}

template <typename StackRep>
size_t StackEntry<StackRep>::fillStack2(StackEntry<StackRep> *stack, size_t nelems)
{
    bool done(false);
    size_t i(0);
    for ( i=0; !done && (i < nelems); i++) {
        switch (i) {
            CASESTACK(31);
            CASESTACK(30);
            CASESTACK(29);
            CASESTACK(28);
            CASESTACK(27);
            CASESTACK(26);
            CASESTACK(25);
            CASESTACK(24);
            CASESTACK(23);
            CASESTACK(22);
            CASESTACK(21);
            CASESTACK(20);
            CASESTACK(19);
            CASESTACK(18);
            CASESTACK(17);
            CASESTACK(16);
            CASESTACK(15);
            CASESTACK(14);
            CASESTACK(13);
            CASESTACK(12);
            CASESTACK(11);
            CASESTACK(10);
            CASESTACK(9);
            CASESTACK(8);
            CASESTACK(7);
            CASESTACK(6);
            CASESTACK(5);
            CASESTACK(4);
            CASESTACK(3);
            CASESTACK(2);
            CASESTACK(1);
            CASESTACK(0);
        default:
            break;
        }
    }
    return i-1;
}

#undef CASESTACK

}

