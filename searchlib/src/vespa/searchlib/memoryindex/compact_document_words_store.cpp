// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.compact_document_words_store");
#include "compact_document_words_store.h"

namespace search {
namespace memoryindex {

typedef CompactDocumentWordsStore::Builder Builder;

namespace {

constexpr size_t MIN_CLUSTERS = 1024u;

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

CompactDocumentWordsStore::Builder &
CompactDocumentWordsStore::Builder::insert(datastore::EntryRef wordRef)
{
    _words.push_back(wordRef);
    return *this;
}

inline void
CompactDocumentWordsStore::Iterator::nextWord()
{
    _wordRef = *_buf++;
    _remainingWords--;
}

CompactDocumentWordsStore::Iterator::Iterator()
    : _buf(NULL),
      _remainingWords(0),
      _wordRef(0),
      _valid(false)
{
}

CompactDocumentWordsStore::Iterator::Iterator(const uint32_t *buf)
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

CompactDocumentWordsStore::Iterator &
CompactDocumentWordsStore::Iterator::operator++()
{
    if (_remainingWords > 0) {
        nextWord();
    } else {
        _valid = false;
    }
    return *this;
}

CompactDocumentWordsStore::Store::Store()
    : _store(),
      _type(1,
            MIN_CLUSTERS,
            RefType::offsetSize()),
      _typeId(0)
{
    _store.addType(&_type);
    _store.initActiveBuffers();
}

CompactDocumentWordsStore::Store::~Store()
{
    _store.dropBuffers();
}

datastore::EntryRef
CompactDocumentWordsStore::Store::insert(const Builder &builder)
{
    size_t serializedSize = getSerializedSize(builder);
    _store.ensureBufferCapacity(_typeId, serializedSize);

    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    datastore::BufferState &state = _store.getBufferState(activeBufferId);
    size_t oldSize = state.size();
    RefType ref(oldSize, activeBufferId);
    assert(oldSize == ref.offset());

    uint32_t *begin = _store.getBufferEntry<uint32_t>(activeBufferId, oldSize);
    uint32_t *end = serialize(builder, begin);
    assert(size_t(end - begin) == serializedSize);
    (void) end;
    state.pushed_back(serializedSize);

    return ref;
}

CompactDocumentWordsStore::Iterator
CompactDocumentWordsStore::Store::get(datastore::EntryRef ref) const
{
    RefType internalRef(ref);
    const uint32_t *buf = _store.getBufferEntry<uint32_t>(internalRef.bufferId(),
                                                          internalRef.offset());
    return Iterator(buf);
}

CompactDocumentWordsStore::CompactDocumentWordsStore()
    : _docs(),
      _wordsStore()
{
}

void
CompactDocumentWordsStore::insert(const Builder &builder)
{
    datastore::EntryRef ref = _wordsStore.insert(builder);
    auto insres = _docs.insert(std::make_pair(builder.docId(), ref));
    if (!insres.second) {
        LOG(error, "Failed inserting remove info for docid %u",
            builder.docId());
        abort();
    }
}

void 
CompactDocumentWordsStore::remove(uint32_t docId)
{
    _docs.erase(docId);
}

CompactDocumentWordsStore::Iterator
CompactDocumentWordsStore::get(uint32_t docId) const
{
    auto itr = _docs.find(docId);
    if (itr != _docs.end()) {
        return _wordsStore.get(itr->second);
    }
    return Iterator();
}

MemoryUsage
CompactDocumentWordsStore::getMemoryUsage() const
{
    MemoryUsage usage;
    usage.incAllocatedBytes(_docs.getMemoryConsumption());
    usage.incUsedBytes(_docs.getMemoryUsed());
    usage.merge(_wordsStore.getMemoryUsage());
    return usage;

}

} // namespace memoryindex
} // namespace search

