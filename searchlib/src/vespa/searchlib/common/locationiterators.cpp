// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "locationiterators.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/attribute/attributevector.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.locationiterators");

namespace search::common {

class LocationIterator : public search::queryeval::SearchIterator
{
private:
    static constexpr double pi = 3.14159265358979323846;
    // microdegrees -> degrees -> radians -> km (using Earth mean radius)
    static constexpr double udeg_to_km = 1.0e-6 * (pi / 180.0) * 6371.0088;
    search::fef::TermFieldMatchData & _tfmd;
    const unsigned int _numDocs;
    const bool         _strict;
    const Location &   _location;
    uint32_t           _num_values;
    std::vector<search::AttributeVector::largeint_t> _pos;

    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
public:
    LocationIterator(search::fef::TermFieldMatchData &tfmd,
                     unsigned int numDocs,
                     bool strict,
                     const Location & location);
    ~LocationIterator() override;
};

LocationIterator::LocationIterator(search::fef::TermFieldMatchData &tfmd,
                                   unsigned int numDocs,
                                   bool strict,
                                   const Location & location)
  : SearchIterator(),
    _tfmd(tfmd),
    _numDocs(numDocs),
    _strict(strict),
    _location(location),
    _num_values(0),
    _pos()
{
    _pos.resize(1);  //Need at least 1 entry as the singlevalue attributes does not honour given size.
    LOG(debug, "created LocationIterator(numDocs=%u)\n", numDocs);
};


LocationIterator::~LocationIterator() = default;

void
LocationIterator::doSeek(uint32_t docId)
{
    while (__builtin_expect(docId < getEndId(), true)) {
        if (__builtin_expect(docId >= _numDocs, false)) {
            break;
        }
        _num_values = _location.getVec()->get(docId, &_pos[0], _pos.size());
        while (_num_values > _pos.size()) {
            _pos.resize(_num_values);
            _num_values = _location.getVec()->get(docId, &_pos[0], _pos.size());
        }
        for (uint32_t i = 0; i < _num_values; i++) {
            int64_t docxy(_pos[i]);
            if (_location.inside_limit(docxy)) {
                setDocId(docId);
                return;
            }
        }
        if (!_strict) {
            return;
        }
        ++docId;
    }
    setAtEnd();
}

void
LocationIterator::doUnpack(uint32_t docId)
{
    uint64_t sqabsdist = std::numeric_limits<uint64_t>::max();
    int32_t docx = 0;
    int32_t docy = 0;
    // use _num_values from _pos fetched in doSeek()
    for (uint32_t i = 0; i < _num_values; i++) {
        int64_t docxy(_pos[i]);
        vespalib::geo::ZCurve::decode(docxy, &docx, &docy);
        uint64_t sqdist = _location.sq_distance_to({docx, docy});
        if (sqdist < sqabsdist) {
            sqabsdist = sqdist;
        }
    }
    double dist = std::sqrt(double(sqabsdist));
    double score = 1.0 / (1.0 + (udeg_to_km * dist));
    LOG(debug, "unpack LI(%u) score %f\n", docId, score);
    LOG(debug, "distance: %f micro-degrees ~= %f km", dist, udeg_to_km * dist);
    _tfmd.setRawScore(docId, score);
}

std::unique_ptr<search::queryeval::SearchIterator>
create_location_iterator(search::fef::TermFieldMatchData &tfmd, unsigned int numDocs,
                         bool strict, const Location & location)
{
    return std::make_unique<LocationIterator>(tfmd, numDocs, strict, location);
}

} // namespace

using namespace search::common;

class FastS_2DZLocationIterator : public search::queryeval::SearchIterator
{
private:
    const unsigned int _numDocs;
    const bool         _strict;
    const Location &   _location;
    std::vector<search::AttributeVector::largeint_t> _pos;

    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
public:
    FastS_2DZLocationIterator(unsigned int numDocs, bool strict, const Location & location);

    ~FastS_2DZLocationIterator() override;
};


FastS_2DZLocationIterator::
FastS_2DZLocationIterator(unsigned int numDocs,
                          bool strict,
                          const Location & location)
    : SearchIterator(),
      _numDocs(numDocs),
      _strict(strict),
      _location(location),
      _pos()
{
    _pos.resize(1);  //Need at least 1 entry as the singlevalue attributes does not honour given size.
};


FastS_2DZLocationIterator::~FastS_2DZLocationIterator() = default;


void
FastS_2DZLocationIterator::doSeek(uint32_t docId)
{
    LOG(debug, "FastS_2DZLocationIterator: seek(%u) with numDocs=%u endId=%u",
        docId, _numDocs, getEndId());
    if (__builtin_expect(docId >= _numDocs, false)) {
        setAtEnd();
        return;
    }

    const Location &location = _location;
    std::vector<search::AttributeVector::largeint_t> &pos = _pos;

    for (;;) {
        uint32_t numValues =
            location.getVec()->get(docId, &pos[0], pos.size());
        if (numValues > pos.size()) {
            pos.resize(numValues);
            numValues = location.getVec()->get(docId, &pos[0], pos.size());
        }
        for (uint32_t i = 0; i < numValues; i++) {
            int64_t docxy(pos[i]);
            if (location.inside_limit(docxy)) {
                setDocId(docId);
                return;
            }
        }

        if (__builtin_expect(docId + 1 >= _numDocs, false)) {
            setAtEnd();
            return;
        }

        if (!_strict) {
            return;
        }
        docId++;
    }
}


void
FastS_2DZLocationIterator::doUnpack(uint32_t docId)
{
    (void) docId;
}


std::unique_ptr<search::queryeval::SearchIterator>
FastS_AllocLocationIterator(unsigned int numDocs, bool strict, const Location & location)
{
    return std::make_unique<FastS_2DZLocationIterator>(numDocs, strict, location);
}
