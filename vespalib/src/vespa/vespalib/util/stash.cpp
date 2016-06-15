// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stash.h"
#include <algorithm>

namespace vespalib {
namespace stash {

namespace {

void free_chunks(Chunk *chunk) {
    while (chunk != nullptr) {
        void *mem = chunk;
        chunk = chunk->next;
        free(mem);
    }
}

Chunk *keep_one(Chunk *chunk) {
    if (chunk != nullptr) {
        Chunk *next = chunk->next;
        while (next != nullptr) {
            void *mem = chunk;
            chunk = next;
            next = chunk->next;
            free(mem);
        }
        chunk->clear();
        return chunk;
    }
    return nullptr;
}

void run_cleanup(Cleanup *cleanup) {
    while (cleanup != nullptr) {
        Cleanup *tmp = cleanup;
        cleanup = tmp->next;
        tmp->cleanup();
    }
}

} // namespace vespalib::stash::<unnamed>

} // namespace vespalib::stash

char *
Stash::do_alloc(size_t size)
{
    if (is_small(size)) {
        void *chunk_mem = malloc(_chunk_size);
        _chunks = new (chunk_mem) stash::Chunk(_chunks);
        return _chunks->alloc(size, _chunk_size);
    } else {
        char *mem = static_cast<char*>(malloc(sizeof(stash::DeleteMemory) + size));
        _cleanup = new (mem) stash::DeleteMemory(_cleanup);
        return (mem + sizeof(stash::DeleteMemory));
    }
}

Stash::Stash(size_t chunk_size)
    : _chunks(nullptr),
      _cleanup(nullptr),
      _chunk_size(std::max(size_t(4096), chunk_size))
{
}

Stash::Stash(Stash &&rhs)
    : _chunks(rhs._chunks),
      _cleanup(rhs._cleanup),
      _chunk_size(rhs._chunk_size)
{
    rhs._chunks = nullptr;
    rhs._cleanup = nullptr;
}

Stash &
Stash::operator=(Stash &&rhs)
{
    stash::run_cleanup(_cleanup);
    stash::free_chunks(_chunks);
    _chunks = rhs._chunks;
    _cleanup = rhs._cleanup;
    _chunk_size = rhs._chunk_size;
    rhs._chunks = nullptr;
    rhs._cleanup = nullptr;
    return *this;
}

Stash::~Stash()
{
    stash::run_cleanup(_cleanup);
    stash::free_chunks(_chunks);
}

void
Stash::clear()
{
    stash::run_cleanup(_cleanup);
    _cleanup = nullptr;
    _chunks = stash::keep_one(_chunks);
}

size_t
Stash::count_used() const
{
    size_t used = 0;
    for (stash::Chunk *chunk = _chunks; chunk != nullptr; chunk = chunk->next) {
        used += chunk->used;
    }
    return used;
}

} // namespace vespalib
