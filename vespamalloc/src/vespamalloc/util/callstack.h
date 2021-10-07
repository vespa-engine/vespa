// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdio.h>
#include <dlfcn.h>
#include <limits>
#include </usr/include/execinfo.h>
#include <vespamalloc/util/stream.h>

namespace vespamalloc {

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
    if (size_t(sz) < nelems) {
        stack[sz] = StackEntry<StackRep>();
    }
    return sz;
}

}

