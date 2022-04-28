// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compact_words_store.h"
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.compact_words_store");

namespace search::memoryindex {

using Builder = CompactWordsStore::Builder;

namespace {

constexpr size_t MIN_BUFFER_ARRAYS = 1024u;

size_t
getSerializedSize(const Builder &builder)
{
    size_t size = 1 + builder.words().size(); // numWords, [words]
    return size;
}

uint32_t *
serialize(const Builder &builder, uint32_t *begin)
{
    uint32_t *buf = begin;
    const Builder::WordRefVector &words = builder.words();
    *buf++ = words.size();
    for (auto word : words) {
        *buf++ = word.ref();
    }
    return buf;
}

}

CompactWordsStore::Builder::Builder(uint32_t docId_)
    : _docId(docId_),
      _words()
{ }

CompactWordsStore::Builder::~Builder() { }

CompactWordsStore::Builder &
CompactWordsStore::Builder::insert(vespalib::datastore::EntryRef wordRef)
{
    _words.push_back(wordRef);
    return *this;
}

inline void
CompactWordsStore::Iterator::nextWord()
{
    _wordRef = *_buf++;
    _remainingWords--;
}

CompactWordsStore::Iterator::Iterator()
    : _buf(nullptr),
      _remainingWords(0),
      _wordRef(0),
      _valid(false)
{
}

CompactWordsStore::Iterator::Iterator(const uint32_t *buf)
    : _buf(buf),
      _remainingWords(0),
      _wordRef(0),
      _valid(true)
{
    _remainingWords = *_buf++;
    if (_remainingWords > 0) {
        nextWord();
    } else {
        _valid = false;
    }
}

CompactWordsStore::Iterator &
CompactWordsStore::Iterator::operator++()
{
    if (_remainingWords > 0) {
        nextWord();
    } else {
        _valid = false;
    }
    return *this;
}

CompactWordsStore::Store::Store()
    : _store(),
      _type(1,
            MIN_BUFFER_ARRAYS,
            RefType::offsetSize()),
      _typeId(0)
{
    _store.addType(&_type);
    _store.init_primary_buffers();
}

CompactWordsStore::Store::~Store()
{
    _store.dropBuffers();
}

vespalib::datastore::EntryRef
CompactWordsStore::Store::insert(const Builder &builder)
{
    size_t serializedSize = getSerializedSize(builder);
    auto result = _store.rawAllocator<uint32_t>(_typeId).alloc(serializedSize);
    uint32_t *begin = result.data;
    uint32_t *end = serialize(builder, begin);
    assert(size_t(end - begin) == serializedSize);
    (void) end;
    return result.ref;
}

CompactWordsStore::Iterator
CompactWordsStore::Store::get(vespalib::datastore::EntryRef wordRef) const
{
    RefType internalRef(wordRef);
    const uint32_t *buf = _store.getEntry<uint32_t>(internalRef);
    return Iterator(buf);
}

CompactWordsStore::CompactWordsStore()
    : _docs(),
      _docs_used_bytes(0),
      _docs_allocated_bytes(0),
      _wordsStore()
{
    update_docs_memory_usage();
}

CompactWordsStore::~CompactWordsStore() { }

void
CompactWordsStore::insert(const Builder &builder)
{
    vespalib::datastore::EntryRef wordRef = _wordsStore.insert(builder);
    auto insres = _docs.insert(std::make_pair(builder.docId(), wordRef));
    if (!insres.second) {
        LOG(error, "Failed inserting remove info for docid %u",
            builder.docId());
        LOG_ABORT("should not be reached");
    }
}

void 
CompactWordsStore::remove(uint32_t docId)
{
    _docs.erase(docId);
}

CompactWordsStore::Iterator
CompactWordsStore::get(uint32_t docId) const
{
    auto itr = _docs.find(docId);
    if (itr != _docs.end()) {
        return _wordsStore.get(itr->second);
    }
    return Iterator();
}

void
CompactWordsStore::update_docs_memory_usage()
{
    _docs_used_bytes.store(_docs.getMemoryUsed(), std::memory_order_relaxed);
    _docs_allocated_bytes.store(_docs.getMemoryConsumption(), std::memory_order_relaxed);
}

void
CompactWordsStore::commit()
{
    update_docs_memory_usage();
}

vespalib::MemoryUsage
CompactWordsStore::getMemoryUsage() const
{
    vespalib::MemoryUsage usage;
    usage.incAllocatedBytes(_docs_allocated_bytes.load(std::memory_order_relaxed));
    usage.incUsedBytes(_docs_used_bytes.load(std::memory_order_relaxed));
    usage.merge(_wordsStore.getMemoryUsage());
    return usage;

}

}

