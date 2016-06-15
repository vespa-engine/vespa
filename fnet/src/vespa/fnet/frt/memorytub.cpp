// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/fnet/frt/frt.h>

void *
FRT_MemoryTub::SlowAlloc(size_t size)
{
    void *chunkMem = malloc(sizeof(Chunk) + CHUNK_SIZE);
    assert(chunkMem != NULL);
    _chunkHead = new (chunkMem) Chunk(CHUNK_SIZE, 0, _chunkHead);
    return _chunkHead->Alloc(size);
}


void *
FRT_MemoryTub::BigAlloc(size_t size)
{
    void *ret = malloc(size);
    assert(ret != NULL);
    _allocHead = new (this) AllocInfo(_allocHead, size, ret);
    return ret;
}


bool
FRT_MemoryTub::InTub(const void *pt) const
{
    const char *p = (const char *) pt;

    for (Chunk *chunk = _chunkHead; chunk != NULL; chunk = chunk->_next)
        if (p >= chunk->_data &&
            p < chunk->_data + chunk->_used)
            return true;

    for (AllocInfo *info = _allocHead; info != NULL; info = info->_next)
        if (p >= (char *) info->_data &&
            p < (char *) info->_data + info->_size)
            return true;

    return false;
}
