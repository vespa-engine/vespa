// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespamalloc {

class NoStat
{
public:
    void incAlloc()         { }
    void incExchangeFree()  { }
    void incReturnFree()    { }
    void incFree()          { }
    void incExchangeAlloc() { }
    void incExactAlloc()    { }

    static bool isDummy()        { return true; }
    size_t alloc()         const { return 0; }
    size_t free()          const { return 0; }
    size_t exchangeAlloc() const { return 0; }
    size_t exchangeFree()  const { return 0; }
    size_t returnFree()    const { return 0; }
    size_t exactAlloc()    const { return 0; }
    bool   isUsed()        const { return false; }
};

class Stat
{
public:
    Stat()
        : _free(0),
          _alloc(0),
          _exchangeAlloc(0),
          _exchangeFree(0),
          _exactAlloc(0),
          _return(0)
    { }
    void incAlloc()         { _alloc++; }
    void incExchangeFree()  { _exchangeFree++; }
    void incReturnFree()    { _return++; }
    void incFree()          { _free++; }
    void incExchangeAlloc() { _exchangeAlloc++; }
    void incExactAlloc()    { _exactAlloc++; }

    bool isUsed()       const {
        return (_alloc || _free || _exchangeAlloc || _exchangeFree || _exactAlloc || _return);
    }
    static bool isDummy()        { return false; }
    size_t alloc()         const { return _alloc; }
    size_t free()          const { return _free; }
    size_t exchangeAlloc() const { return _exchangeAlloc; }
    size_t exchangeFree()  const { return _exchangeFree; }
    size_t exactAlloc()    const { return _exactAlloc; }
    size_t returnFree()    const { return _return; }
private:
    size_t _free;
    size_t _alloc;
    size_t _exchangeAlloc;
    size_t _exchangeFree;
    size_t _exactAlloc;
    size_t _return;
};

}
