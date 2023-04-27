// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "featureset.h"

namespace vespalib {

FeatureSet::FeatureSet()
    : _names(),
      _docIds(),
      _values()
{
}

FeatureSet::~FeatureSet() {}

FeatureSet::FeatureSet(const StringVector &names, uint32_t expectDocs)
    : _names(names),
      _docIds(),
      _values()
{
    _docIds.reserve(expectDocs);
    _values.reserve(expectDocs * names.size());
}

bool
FeatureSet::equals(const FeatureSet &rhs) const
{
    return ((_docIds == rhs._docIds) &&
            (_values == rhs._values) &&
            (_names == rhs._names)); // do names last, as they are most likely to match
}

uint32_t
FeatureSet::addDocId(uint32_t docId)
{
    _docIds.push_back(docId);
    _values.resize(_names.size() * _docIds.size());
    return (_docIds.size() - 1);
}

bool
FeatureSet::contains(const std::vector<uint32_t> &docIds) const
{
    using ITR = std::vector<uint32_t>::const_iterator;
    ITR myPos = _docIds.begin();
    ITR myEnd = _docIds.end();
    ITR pos = docIds.begin();
    ITR end = docIds.end();

    for (; pos != end; ++pos) {
        while (myPos != myEnd && *myPos < *pos) {
            ++myPos;
        }
        if (myPos == myEnd || *myPos != *pos) {
            return false;
        }
        ++myPos;
    }
    return true;
}

FeatureSet::Value *
FeatureSet::getFeaturesByIndex(uint32_t idx)
{
    if (idx >= _docIds.size()) {
        return 0;
    }
    return &(_values[idx * _names.size()]);
}

const FeatureSet::Value *
FeatureSet::getFeaturesByDocId(uint32_t docId) const
{
    uint32_t low = 0;
    uint32_t hi = _docIds.size();
    while (low < hi) {
        uint32_t pos = (low + hi) >> 1;
        uint32_t val = _docIds[pos];
        if (val < docId) {
            low = pos + 1;
        } else if (val > docId) {
            hi = pos;
        } else {
            return &(_values[pos * _names.size()]);
        }
    }
    return 0;
}

FeatureValues::FeatureValues() noexcept = default;
FeatureValues::FeatureValues(const FeatureValues& rhs) = default;
FeatureValues::FeatureValues(FeatureValues&& rhs) noexcept = default;
FeatureValues::~FeatureValues() noexcept = default;
FeatureValues& FeatureValues::operator=(const FeatureValues& rhs) = default;;
FeatureValues& FeatureValues::operator=(FeatureValues&& rhs) noexcept = default;

}
