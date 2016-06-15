// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#if !defined(MEMTUB_CLASS) || !defined(MEMTUB_REFCLASS) || !defined(MEMTUB_CHUNK) || !defined(MEMTUB_LIMIT)
#error "Missing 'template' parameter(s)..."
#endif


namespace search {
namespace util {

/**
 * These classes are used to speed up allocation and deallocation of
 * memory. The poor mans template HACK is in honor of AIX. The denial
 * of array alloc operations is in honor of Microsoft (VC++).
 **/
class MEMTUB_CLASS : public IMemTub
{
private:
    MEMTUB_CLASS(const MEMTUB_CLASS &);
    MEMTUB_CLASS& operator=(const MEMTUB_CLASS &);

public:

    struct AllocInfo {
    private:
        AllocInfo(const AllocInfo &);
        AllocInfo &operator=(const AllocInfo &);

    public:
        AllocInfo *_next;
        void      *_data;
        uint32_t   _size;

        AllocInfo(AllocInfo *next, void *data, uint32_t size)
            : _next(next), _data(data), _size(size) {}
    };

    struct Chunk {
    private:
        Chunk(const Chunk &);
        Chunk &operator=(const Chunk &);
    public:
        uint32_t  _used;
        Chunk    *_next;
        char      _data[MEMTUB_CHUNK];

        void *Alloc(size_t size)
        {
            size_t alignedsize = Align(size);
            if (_used + alignedsize <= sizeof(_data)) {
                void *ret = &_data[_used];
                _used += alignedsize;
                return ret;
            }
            return NULL;
        }
        Chunk(uint32_t used,
              Chunk *next)
            : _used(used),
              _next(next)
        {
        }
    };

private:

    Chunk      _fixedChunk;
    Chunk     *_chunkHead;
    AllocInfo *_allocHead;

    void *SlowAlloc(size_t size) {
        Chunk *chunk = static_cast<Chunk *>(malloc(sizeof(Chunk)));
        assert(chunk != NULL);
        chunk->_used = 0;
        chunk->_next = _chunkHead;
        _chunkHead = chunk;
        return _chunkHead->Alloc(size);
    }
    void *SmallAlloc(size_t size) {
        void *tmp = _chunkHead->Alloc(size);
        return (tmp != NULL) ? tmp : SlowAlloc(size);
    }
    void *BigAlloc(size_t size) {
        void *ret = malloc(size);
        assert(ret != NULL);
        _allocHead = new (SmallAlloc(sizeof(AllocInfo))) AllocInfo(_allocHead, ret, size);
        return ret;
    }

public:
    MEMTUB_CLASS()
        : _fixedChunk(0, NULL),
          _chunkHead(&_fixedChunk),
          _allocHead(NULL)
    {
        assert(MEMTUB_CHUNK >= MEMTUB_LIMIT * 2);
        assert(MEMTUB_LIMIT >= sizeof(AllocInfo));
    }

    uint32_t GetChunkSize()  const { return MEMTUB_CHUNK; }
    uint32_t GetAllocLimit() const { return MEMTUB_LIMIT; }

    inline bool InTub(const void *pt) const {
        const char *p = static_cast<const char *>(pt);

        for (Chunk *chunk = _chunkHead; chunk != NULL; chunk = chunk->_next)
            if (p >= chunk->_data &&
                p < chunk->_data + chunk->_used)
                return true;

        for (AllocInfo *info = _allocHead; info != NULL; info = info->_next)
            if (p >= static_cast<char *>(info->_data) &&
                p < static_cast<char *>(info->_data) + info->_size)
                return true;

        return false;
    }

    void *Alloc(size_t size) {
        return (size > MEMTUB_LIMIT) ? BigAlloc(size) : SmallAlloc(size);
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

    virtual ~MEMTUB_CLASS()
    {
        Reset();
    }

    // IMemTub implementation
    virtual void *TubAlloc(size_t size) {
        return Alloc(size);
    }
    virtual void AddRef() {}
    virtual void SubRef() {}
};


class MEMTUB_REFCLASS : public MEMTUB_CLASS
{
private:
    FastOS_Mutex _lock;
    int         _refcnt;

public:
    MEMTUB_REFCLASS() : _lock(), _refcnt(1) {}
    virtual ~MEMTUB_REFCLASS() { assert(_refcnt == 0); }
    virtual void AddRef()
    {
        _lock.Lock();
        _refcnt++;
        _lock.Unlock();
    }
    virtual void SubRef()
    {
        _lock.Lock();
        assert(_refcnt > 0);
        if (--_refcnt > 0) {
            _lock.Unlock();
            return;
        }
        _lock.Unlock();
        delete this;
    }
};

}
}

inline void *
operator new(size_t size, search::util::MEMTUB_CLASS *tub)
{
    return tub->Alloc(size);
}


inline void *
operator new[](size_t size, search::util::MEMTUB_CLASS *tub)
{
    return tub->Alloc(size);
}


#undef MEMTUB_CLASS
#undef MEMTUB_REFCLASS
#undef MEMTUB_CHUNK
#undef MEMTUB_LIMIT
