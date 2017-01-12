// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <cstring>


class FRT_MemoryTub
{

public:

    enum {
        CHUNK_SIZE  = 32500,
        FIXED_SIZE  =  3880,
        ALLOC_LIMIT =  3200
    };

    struct AllocInfo {
        AllocInfo *_next;
        uint32_t   _size;
        void      *_data;

        AllocInfo(AllocInfo *next, uint32_t size, void *data)
            : _next(next), _size(size), _data(data) {}

    private:
        AllocInfo(const AllocInfo &);
        AllocInfo &operator=(const AllocInfo &);
    };

    struct Chunk {
        uint32_t  _size;
        uint32_t  _used;
        Chunk    *_next;
        char     *_data;

        Chunk(uint32_t size, uint32_t used, Chunk *next)
            : _size(size), _used(used), _next(next), _data(NULL)
        {
            _data = reinterpret_cast<char*>(this + 1);
        }

        void *Alloc(size_t size)
        {
            size_t alignedsize = ((size + (sizeof(char *) - 1))
                                  & ~(sizeof(char *) - 1));
            if (_used + alignedsize <= _size) {
                void *ret = _data + _used;
                _used += alignedsize;
                return ret;
            }
            return NULL;
        }

    private:
        Chunk(const Chunk &);
        Chunk &operator=(const Chunk &);
    };

private:

    Chunk      _fixedChunk;
    char       _fixedData[FIXED_SIZE];
    Chunk     *_chunkHead;
    AllocInfo *_allocHead;

    FRT_MemoryTub(const FRT_MemoryTub &);
    FRT_MemoryTub &operator=(const FRT_MemoryTub &);

    void *SlowAlloc(size_t size);
    void *BigAlloc(size_t size);

public:

    FRT_MemoryTub()
        : _fixedChunk(FIXED_SIZE, 0, NULL),
          _chunkHead(&_fixedChunk),
          _allocHead(NULL)
    {
        // Just to be sure
        _fixedChunk._data = _fixedData;
    }

    bool InTub(const void *pt) const;

    void *Alloc(size_t size)
    {
        if (size > ALLOC_LIMIT)
            return BigAlloc(size);
        void *tmp = _chunkHead->Alloc(size);
        return (tmp != NULL) ? tmp : SlowAlloc(size);
    };

    char *CopyString(const char *str, uint32_t len)
    {
        char *pt = (char *) Alloc(len + 1);
        memcpy(pt, str, len);
        pt[len] = '\0';
        return pt;
    }

    char *CopyData(const char *buf, uint32_t len)
    {
        char *pt = (char *) Alloc(len);
        memcpy(pt, buf, len);
        return pt;
    }

    void Reset()
    {
        for (AllocInfo *info = _allocHead;
             info != NULL; info = info->_next) {
            free(info->_data);
        }
        _allocHead = NULL;
        while (_chunkHead != &_fixedChunk) {
            Chunk *tmp = _chunkHead;
            _chunkHead = tmp->_next;
            free(tmp);
        }
        _fixedChunk._used = 0;
    }

    ~FRT_MemoryTub()
    {
        Reset();
    }
};


inline void *
operator new(size_t size, FRT_MemoryTub *tub)
{
    return tub->Alloc(size);
}


inline void *
operator new[](size_t size, FRT_MemoryTub *tub)
{
    (void) size;
    (void) tub;
    fprintf(stderr, "Microsoft does not permit this operation!");
    abort();
    return malloc(size);
}

