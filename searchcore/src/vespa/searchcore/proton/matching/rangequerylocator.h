// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vespa/searchlib/query/numeric_range_spec.h>

namespace search::queryeval { class Blueprint; }

namespace proton::matching {

class RangeLimitMetaInfo {
public:
    RangeLimitMetaInfo();
    RangeLimitMetaInfo(const search::NumericRangeSpec& range_spec, size_t estimate);
    ~RangeLimitMetaInfo();
    const search::NumericRangeSpec& range_spec() const { return _range_spec; }
    bool valid() const { return _valid; }
    size_t estimate() const { return _estimate; }
private:
    bool                       _valid;
    size_t                     _estimate;
    search::NumericRangeSpec   _range_spec;
};

class RangeQueryLocator {
public:
    virtual ~RangeQueryLocator() = default;
    virtual RangeLimitMetaInfo locate() const = 0;
};

class LocateRangeItemFromQuery : public RangeQueryLocator {
public:
    LocateRangeItemFromQuery(const search::queryeval::Blueprint & blueprint, uint32_t field_id)
        : _blueprint(blueprint),
          _field_id(field_id)
    {}
    RangeLimitMetaInfo locate() const override;
private:
    const search::queryeval::Blueprint & _blueprint;
    uint32_t _field_id;
};

}
