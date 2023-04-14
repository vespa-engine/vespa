// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feature_store.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/vespalib/datastore/compacting_buffers.h>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/datastore.hpp>

namespace search::memoryindex {

constexpr size_t MIN_BUFFER_ARRAYS = 1024u;

using index::SchemaUtil;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

uint64_t
FeatureStore::writeFeatures(uint32_t packedIndex, const DocIdAndFeatures &features)
{
    _f._fieldsParams = &_fieldsParams[packedIndex];
    uint64_t oldOffset = _f.getWriteOffset();
    assert((oldOffset & 63) == 0);
    if (oldOffset > 2000) {
        _f.setupWrite(_fctx);
        oldOffset = 0;
        assert(_f.getWriteOffset() == oldOffset);
    }
    assert(!features.has_raw_data());
    _f.writeFeatures(features);
    return oldOffset;
}

EntryRef
FeatureStore::addFeatures(const uint8_t *src, uint64_t byteLen)
{
    uint32_t pad = Aligner::pad(byteLen);
    auto result = _store.rawAllocator<uint8_t>(_typeId).alloc((byteLen + pad) / buffer_array_size, DECODE_SAFETY_ENTRIES);
    uint8_t *dst = result.data;
    memcpy(dst, src, byteLen);
    dst += byteLen;
    if (pad > 0) {
        memset(dst, 0, pad);
        dst += pad;
    }
    memset(dst, 0, DECODE_SAFETY);
    return result.ref;
}

std::pair<EntryRef, uint64_t>
FeatureStore::addFeatures(uint64_t beginOffset, uint64_t endOffset)
{
    uint64_t bitLen = (endOffset - beginOffset);
    assert(static_cast<int64_t>(bitLen) > 0);
    uint64_t wordLen = (bitLen + 63) / 64;
    uint64_t byteLen = (bitLen + 7) / 8;
    assert(wordLen > 0);
    assert(byteLen > 0);
    const uint8_t *src = reinterpret_cast<const uint8_t *>(_f._valI - wordLen);
    EntryRef ref = addFeatures(src, byteLen);
    return std::make_pair(ref, bitLen);
}

EntryRef
FeatureStore::moveFeatures(EntryRef ref, uint64_t bitLen)
{
    const uint8_t *src = getBits(ref);
    uint64_t byteLen = (bitLen + 7) / 8;
    EntryRef newRef = addFeatures(src, byteLen);
    return newRef;
}

FeatureStore::FeatureStore(const Schema &schema)
    : _store(),
      _f(nullptr),
      _fctx(_f),
      _d(nullptr),
      _fieldsParams(),
      _schema(schema),
      _type(buffer_array_size, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _typeId(0)
{
    _f.setWriteContext(&_fctx);
    _fctx.allocComprBuf(64, 1);
    _f.afterWrite(_fctx, 0, 0);

    _fieldsParams.resize(_schema.getNumIndexFields());
    SchemaUtil::IndexIterator it(_schema);
    for (; it.isValid(); ++it) {
        _fieldsParams[it.getIndex()].setSchemaParams(_schema, it.getIndex());
    }
    _store.addType(&_type);
    _store.init_primary_buffers();
}

FeatureStore::~FeatureStore()
{
    _store.dropBuffers();
}

std::pair<EntryRef, uint64_t>
FeatureStore::addFeatures(uint32_t packedIndex, const DocIdAndFeatures &features)
{
    uint64_t oldOffset = writeFeatures(packedIndex, features);
    uint64_t newOffset = _f.getWriteOffset();
    _f.flush();
    return addFeatures(oldOffset, newOffset);
}

void
FeatureStore::add_features_guard_bytes()
{
    uint32_t len = DECODE_SAFETY;
    uint32_t pad = Aligner::pad(len);
    auto result = _store.rawAllocator<uint8_t>(_typeId).alloc((len + pad) / buffer_array_size);
    memset(result.data, 0, len + pad);
}

void
FeatureStore::getFeatures(uint32_t packedIndex, EntryRef ref, DocIdAndFeatures &features)
{
    setupForField(packedIndex, _d);
    setupForReadFeatures(ref, _d);
    _d.readFeatures(features);
}

size_t
FeatureStore::bitSize(uint32_t packedIndex, EntryRef ref)
{
    setupForField(packedIndex, _d);
    setupForUnpackFeatures(ref, _d);
    uint64_t oldOffset = _d.getReadOffset();
    _d.skipFeatures(1);
    uint64_t newOffset = _d.getReadOffset();
    uint64_t bitLen = (newOffset - oldOffset);
    assert(static_cast<int64_t>(bitLen) > 0);
    return bitLen;
}

EntryRef
FeatureStore::moveFeatures(uint32_t packedIndex, EntryRef ref)
{
    uint64_t bitLen = bitSize(packedIndex, ref);
    return moveFeatures(ref, bitLen);
}

std::unique_ptr<vespalib::datastore::CompactingBuffers>
FeatureStore::start_compact()
{
    // Use a compaction strategy that will compact all active buffers
    auto compaction_strategy = CompactionStrategy::make_compact_all_active_buffers_strategy();
    CompactionSpec compaction_spec(true, false);
    return _store.start_compact_worst_buffers(compaction_spec, compaction_strategy);
}

}
