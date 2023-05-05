// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchresult.h"
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <algorithm>

namespace vdslib {

namespace {

// Magic value for hit count to enable extension flags
constexpr uint32_t extension_flags_present = 0xffffffffu;

// Extension flag values
constexpr uint32_t match_features_present_mask = 1;

// Selector values for feature value
constexpr uint8_t feature_value_is_double = 0;
constexpr uint8_t feature_value_is_data = 1;

inline bool has_match_features(uint32_t extension_flags) {
    return ((extension_flags & match_features_present_mask) != 0);
}

inline bool must_serialize_extension_flags(uint32_t extension_flags, uint32_t hit_count) {
    return ((extension_flags != 0) || (hit_count == extension_flags_present));
}

}

void AggregatorList::add(size_t id, const vespalib::MallocPtr & aggrBlob)
{
    insert(value_type(id, aggrBlob));
}

void AggregatorList::deserialize(document::ByteBuffer & buf)
{
    int32_t tmp(0);
    buf.getIntNetwork(tmp);
    for (size_t i(tmp); i; i--) {
        buf.getIntNetwork(tmp);
        size_t id(tmp);
        buf.getIntNetwork(tmp);
        size_t sz(tmp);
        vespalib::MallocPtr aggr(sz);
        buf.getBytes(aggr, sz);
        add(id, aggr);
    }
}

void AggregatorList::serialize(vespalib::GrowableByteBuffer & buf) const
{
    buf.putInt(size());
    for (const auto & entry : *this) {
        buf.putInt(entry.first);
        buf.putInt(entry.second.size());
        buf.putBytes(entry.second, entry.second.size());
    }
}

uint32_t AggregatorList::getSerializedSize() const
{
    size_t sz(sizeof(uint32_t) * (1 + 2*size()));
    for (const auto & entry : *this) {
        sz += entry.second.size();
    }
    return sz;
}

BlobContainer::BlobContainer(size_t reserve) :
    _blob(reserve),
    _offsets()
{
    _offsets.push_back(0);
}

BlobContainer::~BlobContainer() = default;

size_t BlobContainer::append(const void * v, size_t sz)
{
    const size_t index(getCount());
    _offsets.push_back(_offsets.back() + sz);
    if (getSize() > _blob.size()) {
        _blob.realloc(getSize()*2);
    }
    if (sz > 0) {
        memcpy(_blob.str() + _offsets[index], v, sz);
    }
    return index;
}

void BlobContainer::getBlob(size_t index, const void * & blob, size_t & sz) const
{
    blob = _blob.c_str() + _offsets[index];
    sz = getSize(index);
}

void BlobContainer::deserialize(document::ByteBuffer & buf)
{
    int tmp(0);
    buf.getIntNetwork(tmp);
    _offsets.resize(tmp + 1);
    _offsets[0] = 0;
    for (size_t i(0), m(getCount()); i < m; i++) {
        buf.getIntNetwork(tmp);
        _offsets[i+1] = _offsets[i] + tmp;
    }
    _blob.realloc(getSize());
    buf.getBytes(_blob, getSize());
}

void BlobContainer::serialize(vespalib::GrowableByteBuffer & buf) const
{
    buf.putInt(getCount());
    for(size_t i(0), m(getCount()); i < m; i++) {
        buf.putInt(getSize(i));
    }
    buf.putBytes(_blob, getSize());
}

SearchResult::SearchResult() :
    _totalHits(0),
    _wantedHits(10),
    _hits(),
    _docIdBuffer(),
    _numDocIdBytes(0)
{
    _docIdBuffer.reset(new vespalib::MallocPtr(4_Ki));
}

SearchResult::SearchResult(document::ByteBuffer & buf) :
    _totalHits(0),
    _wantedHits(10),
    _hits(),
    _docIdBuffer(),
    _numDocIdBytes(0),
    _aggregatorList(),
    _groupingList(),
    _sortBlob(),
    _match_features()
{
    deserialize(buf);
}

SearchResult::SearchResult(SearchResult &&) noexcept = default;
SearchResult::~SearchResult() = default;

void
SearchResult::deserialize(document::ByteBuffer & buf)
{
    int32_t tmp;
    buf.getIntNetwork(tmp); _totalHits = tmp;
    uint32_t numResults(0), bufSize(0);
    buf.getIntNetwork(tmp); numResults = tmp;
    uint32_t extension_flags = 0u;
    if (numResults == extension_flags_present) {
        buf.getIntNetwork(tmp);
        extension_flags = tmp;
        buf.getIntNetwork(tmp);
        numResults = tmp;
    }
    if (numResults > 0) {
        buf.getIntNetwork(tmp); bufSize = tmp;
        _docIdBuffer.reset(new vespalib::MallocPtr(bufSize));
        buf.getBytes(_docIdBuffer->str(), _docIdBuffer->size());
        _hits.resize(numResults);
        _numDocIdBytes = _docIdBuffer->size();
        double rank(0);
        const char * docIdbp(_docIdBuffer->c_str());
        for (size_t n(0), m(_hits.size()), p(0); n < m; n++) {
            buf.getDoubleNetwork(rank);
            _hits[n] = Hit(0, rank, p, n);
            while(docIdbp[p++]) { }
        }
    }
    _sortBlob.deserialize(buf);
    _aggregatorList.deserialize(buf);
    _groupingList.deserialize(buf);
    if (has_match_features(extension_flags)) {
        deserialize_match_features(buf);
    }
}

void SearchResult::serialize(vespalib::GrowableByteBuffer & buf) const
{
    buf.putInt(_totalHits);
    uint32_t hitCount = std::min(_hits.size(), _wantedHits);
    uint32_t extension_flags = calc_extension_flags(hitCount);
    if (must_serialize_extension_flags(extension_flags, hitCount)) {
        buf.putInt(extension_flags_present);
        buf.putInt(extension_flags);
    }
    buf.putInt(hitCount);
    if (hitCount > 0) {
        uint32_t sz = getBufCount();
        buf.putInt(sz);
        for (size_t i(0), m(hitCount); i < m; i++) {
            const char * s(_hits[i].getDocId(_docIdBuffer->c_str()));
            buf.putBytes(s, strlen(s)+1);
        }
        for (size_t i(0), m(hitCount); i < m; i++) {
            buf.putDouble(_hits[i].getRank());
        }
    }
    uint32_t sortCount = std::min(_sortBlob.getCount(), _wantedHits);
    buf.putInt(sortCount);
    for (size_t i(0); i < sortCount; i++) {
        buf.putInt(_sortBlob.getSize(_hits[i].getIndex()));
    }
    for (size_t i(0); i < sortCount; i++) {
        size_t sz;
        const void * b;
        _sortBlob.getBlob(_hits[i].getIndex(), b, sz);
        buf.putBytes(b, sz);
    }
    _aggregatorList.serialize(buf);
    _groupingList.serialize(buf);
    if (has_match_features(extension_flags)) {
        serialize_match_features(buf, hitCount);
    }
}

uint32_t SearchResult::getSerializedSize() const
{
    uint32_t hitCount = std::min(_hits.size(), _wantedHits);
    uint32_t extension_flags = calc_extension_flags(hitCount);
    uint32_t extension_flags_overhead = must_serialize_extension_flags(extension_flags, hitCount) ? (2 * sizeof(uint32_t)) : 0;
    uint32_t match_features_size = has_match_features(extension_flags) ? get_match_features_serialized_size(hitCount) : 0;
    return _aggregatorList.getSerializedSize() +
           _groupingList.getSerializedSize() +
           _sortBlob.getSerializedSize() +
           extension_flags_overhead +
           match_features_size +
           ((hitCount > 0) ? ((4 * 3) + getBufCount() + sizeof(RankType)*hitCount) : 8);
}

uint32_t
SearchResult::calc_extension_flags(uint32_t hit_count) const noexcept
{
    uint32_t extension_flags = 0u;
    if (!_match_features.names.empty() && hit_count != 0) {
        extension_flags |= match_features_present_mask;
    }
    return extension_flags;
}

uint32_t
SearchResult::get_match_features_serialized_size(uint32_t hit_count) const noexcept
{
    uint32_t size = sizeof(uint32_t);
    for (auto& name : _match_features.names) {
        size += sizeof(uint32_t) + name.size() + 1;
    }
    for (uint32_t i = 0; i < hit_count; ++i) {
        auto mfv = get_match_feature_values(i);
        for (auto& value : mfv) {
            if (value.is_data()) {
                size += sizeof(uint8_t) + sizeof(uint32_t) + value.as_data().size;
            } else {
                size += sizeof(uint8_t) + sizeof(double);
            }
        }
    }
    return size;
}

void
SearchResult::serialize_match_features(vespalib::GrowableByteBuffer& buf, uint32_t hit_count) const
{
    buf.putInt(_match_features.names.size());
    for (auto& name : _match_features.names) {
        buf.put_c_string(name);
    }
    for (uint32_t i = 0; i < hit_count; ++i) {
        auto mfv = get_match_feature_values(i);
        for (auto& value : mfv) {
            if (value.is_data()) {
                buf.putByte(feature_value_is_data);
                auto mem = value.as_data();
                buf.putInt(mem.size);
                buf.putBytes(mem.data, mem.size);
            } else {
                buf.putByte(feature_value_is_double);
                buf.putDouble(value.as_double());
            }
        }
    }
}

void
SearchResult::deserialize_match_features(document::ByteBuffer& buf)
{
    int32_t tmp(0);
    double dtmp(0.0);
    uint8_t selector(0);
    std::vector<char> scratch;
    buf.getIntNetwork(tmp);
    uint32_t num_features = tmp;
    _match_features.names.resize(num_features);
    for (auto& name : _match_features.names) {
        buf.getIntNetwork(tmp);
        if (tmp > 1) {
            name.resize(tmp - 1);
            buf.getBytes(&name[0], tmp - 1);
        }
        buf.getByte(selector); // Read and ignore the nul-termination.
    }
    uint32_t hit_count = _hits.size();
    uint32_t num_values = num_features * hit_count;
    _match_features.values.resize(num_values);
    for (auto& value : _match_features.values) {
        buf.getByte(selector);
        if (selector == feature_value_is_data) {
            buf.getIntNetwork(tmp);
            scratch.resize(tmp);
            if (!scratch.empty()) {
                buf.getBytes(scratch.data(), scratch.size());
            }
            value.set_data({ scratch.data(), scratch.size() });
        } else if (selector == feature_value_is_double) {
            buf.getDoubleNetwork(dtmp);
            value.set_double(dtmp);
        } else {
            abort();
        }
    }
}

void SearchResult::addHit(uint32_t lid, const char * docId, RankType rank, const void * sortData, size_t sz)
{
    addHit(lid, docId, rank);
    _sortBlob.append(sortData, sz);
}

void SearchResult::addHit(uint32_t lid, const char * docId, RankType rank)
{
    const size_t sz(strlen(docId));
    size_t start = 0;
    if ( ! _hits.empty() ) {
        start = getBufCount();
    }
    Hit h(lid, rank, start, _hits.size());
    _hits.push_back(h);
    _totalHits++;
    _numDocIdBytes += sz + 1;
    if (_numDocIdBytes > _docIdBuffer->size()) {
        _docIdBuffer->realloc((_numDocIdBytes)*2);
    }
    memcpy(_docIdBuffer->str() + start, docId, sz+1);
}

void SearchResult::sort()
{
    if (_sortBlob.getCount() == 0) {
        std::sort(_hits.begin(), _hits.end(), std::greater<Hit>());
    } else {
        std::sort(_hits.begin(), _hits.end(), SortDataCompare(_sortBlob));
    }
}

void
SearchResult::set_match_features(FeatureValues&& match_features)
{
    _match_features = std::move(match_features);
}

}
