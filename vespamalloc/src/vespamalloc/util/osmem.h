// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cctype>
#include <cstdlib>
#include <unistd.h>
#include <cassert>
#include <cstring>
#include <algorithm>

namespace vespamalloc {

class Memory
{
public:
    Memory(size_t blockSize) : _blockSize(std::max(blockSize, size_t(getpagesize()))), _start(NULL), _end(NULL) { }
    virtual ~Memory() { }
    void * getStart() const  { return _start; }
    void * getEnd()   const  { return _end; }
    size_t getMinBlockSize() const { return _blockSize; }
    static size_t getMinPreferredStartAddress() { return 0x10000000000; } // 1T
    static size_t getBlockAlignment() { return 0x200000; } //2M
protected:
    void setStart(void * v) { _start = v; }
    void setEnd(void * v)   { _end = v; }
    size_t _blockSize;
    void * _start;
    void * _end;
};

class MmapMemory : public Memory
{
public:
    MmapMemory(size_t blockSize);
    virtual ~MmapMemory();
    void *reserve(size_t & len);
    void *get(size_t len);
    bool release(void * mem, size_t len);
    bool reclaim(void * mem, size_t len);
    bool freeTail(void * mem, size_t len);
private:
    void * getHugePages(size_t len);
    void * getNormalPages(size_t len);
    void * getBasePages(size_t len, int mmapOpt, int fd, size_t offset);
    void setupFAdvise();
    void setupHugePages();
    size_t   _useMAdvLimit;
    int      _hugePagesFd;
    size_t   _hugePagesOffset;
    size_t   _hugePageSize;
    char     _hugePagesFileName[1024];
};

}
