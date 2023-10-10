// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <memory>

namespace search::queryeval {

/**
 * Utility used to iterate low-level sorted results (typically owned
 * by a HitCollector). The actual results are stored in a backing
 * array and the order is defined by a separate index array.
 **/
class SortedHitSequence
{
public:
    using Hit = std::pair<uint32_t, feature_t>;
    using Ref = uint32_t;

private:
    const Hit *_data;
    const Ref *_pos;
    const Ref *_end;

public:
    SortedHitSequence(const Hit *hits, const Ref *refs, size_t num_refs)
        : _data(hits), _pos(refs), _end(refs + num_refs) {}
    bool valid() const { return (_pos != _end); }
    const Hit &get() const { return _data[*_pos]; }
    void next() { ++_pos; }
};

}
