// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2002-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#if defined(MEMTUB_CLASS) || defined(MEMTUB_REFCLASS) || defined(MEMTUB_CHUNK) || defined(MEMTUB_LIMIT)
#error "Memory tub 'template' parameters collide with other defines..."
#endif

#include <new>


namespace search {
namespace util {

class IMemTub
{
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~IMemTub(void) { }

    virtual void *TubAlloc(size_t size) = 0;
    virtual void  AddRef() = 0;
    virtual void  SubRef() = 0;
    static uint32_t Align(uint32_t size)
    {
        return ((size + (sizeof(char *) - 1))
                & ~(sizeof(char *) - 1));
    }
};

}
}

inline void *
operator new(size_t size, search::util::IMemTub *tub)
{
    return tub->TubAlloc(size);
}

inline void *
operator new[](size_t size, search::util::IMemTub *tub)
{
    return tub->TubAlloc(size);
}

#define MEMTUB_CLASS MicroMemoryTub
#define MEMTUB_REFCLASS MicroMemoryTubRefCnt
#define MEMTUB_CHUNK (8192 - 256)
#define MEMTUB_LIMIT 2048
#include <vespa/searchlib/util/memorytub_impl.h>

#define MEMTUB_CLASS TinyMemoryTub
#define MEMTUB_REFCLASS TinyMemoryTubRefCnt
#define MEMTUB_CHUNK (16384 - 256)
#define MEMTUB_LIMIT 4096
#include <vespa/searchlib/util/memorytub_impl.h>

#define MEMTUB_CLASS SmallMemoryTub
#define MEMTUB_REFCLASS SmallMemoryTubRefCnt
#define MEMTUB_CHUNK (32768 - 256)
#define MEMTUB_LIMIT 8192
#include <vespa/searchlib/util/memorytub_impl.h>

#define MEMTUB_CLASS MediumMemoryTub
#define MEMTUB_REFCLASS MediumMemoryTubRefCnt
#define MEMTUB_CHUNK (65536 - 256)
#define MEMTUB_LIMIT 16384
#include <vespa/searchlib/util/memorytub_impl.h>

#define MEMTUB_CLASS LargeMemoryTub
#define MEMTUB_REFCLASS LargeMemoryTubRefCnt
#define MEMTUB_CHUNK (131072 - 256)
#define MEMTUB_LIMIT 32768
#include <vespa/searchlib/util/memorytub_impl.h>

#define MEMTUB_CLASS HugeMemoryTub
#define MEMTUB_REFCLASS HugeMemoryTubRefCnt
#define MEMTUB_CHUNK (262144 - 256)
#define MEMTUB_LIMIT 65536
#include <vespa/searchlib/util/memorytub_impl.h>

namespace search {
namespace util {

class DocSumMemoryPool : public SmallMemoryTub {};

}
}

