// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/vespalib/util/fiddle.h>
#include <algorithm>
#include <limits>

namespace vespalib::geo {

namespace {

    /**
 * An area defined by its upper left and lower right corners. The
 * z-coordinates between these corners act as a spacial
 * over-estimation of the actual area. These areas may never cross
 * signed borders, since that would break the whole concept of
 * hierarchical spatial partitioning.
 **/
struct Area {
    const ZCurve::Point min;
    const ZCurve::Point max;
    Area(const Area &rhs) = default;
    Area(int32_t min_x, int32_t min_y,
         int32_t max_x, int32_t max_y)
            : min(min_x, min_y), max(max_x, max_y)
    {
        assert((min_x <= max_x) && ((min_x < 0) == (max_x < 0)));
        assert((min_y <= max_y) && ((min_y < 0) == (max_y < 0)));
    }
    Area &operator=(Area &&rhs) { new ((void*)this) Area(rhs); return *this; }
    int64_t size() const { return (static_cast<int64_t>(max.x) - min.x + 1) * (static_cast<int64_t>(max.y) - min.y + 1); }
    int64_t estimate() const { return (max.z - min.z + 1); }
    int64_t error() const { return estimate() - size(); }
};

class ZAreaQueue
{
private:
    struct MaxAreaErrorCmp {
        bool operator()(const Area &a, const Area &b) const {
            return (a.error() > b.error());
        }
    };
    using Range = ZCurve::Range;
    using RangeVector = ZCurve::RangeVector;
    using Queue = PriorityQueue<Area, MaxAreaErrorCmp, LeftArrayHeap>;

    Queue   _queue;
    int64_t _total_estimate;

public:
    ZAreaQueue() : _queue(), _total_estimate(0) {}

    int64_t total_estimate() const { return _total_estimate; }

    void put(Area area) {
        _total_estimate += std::min(area.estimate(), std::numeric_limits<int64_t>::max() - _total_estimate);
        _queue.push(std::move(area));
    }

    Area get() {
        assert(!_queue.empty());
        Area area(_queue.front());
        _queue.pop_front();
        _total_estimate -= area.estimate();
        return area;
    }

    size_t size() const { return _queue.size(); }

    RangeVector extract_ranges() {
        RangeVector ranges;
        ranges.reserve(_queue.size());
        while (!_queue.empty()) {
            const Area &area = _queue.any();
            ranges.push_back(Range(area.min.z, area.max.z));
            _queue.pop_any();
        }
        return ranges;
    }
};

class ZAreaSplitter
{
private:
    using RangeVector = ZCurve::RangeVector;

    ZAreaQueue _queue;

public:
    ZAreaSplitter(int min_x, int min_y, int max_x, int max_y) : _queue() {
        assert(min_x <= max_x);
        assert(min_y <= max_y);
        bool cross_x = (min_x < 0) != (max_x < 0);
        bool cross_y = (min_y < 0) != (max_y < 0);
        if (cross_x) {
            if (cross_y) {
                _queue.put(Area(min_x, min_y,    -1,    -1));
                _queue.put(Area(    0, min_y, max_x,    -1));
                _queue.put(Area(min_x,     0,    -1, max_y));
                _queue.put(Area(    0,     0, max_x, max_y));
            } else {
                _queue.put(Area(min_x, min_y,    -1, max_y));
                _queue.put(Area(    0, min_y, max_x, max_y));
            }
        } else {
            if (cross_y) {
                _queue.put(Area(min_x, min_y, max_x,    -1));
                _queue.put(Area(min_x,     0, max_x, max_y));
            } else {
                _queue.put(Area(min_x, min_y, max_x, max_y));
            }
        }
    }

    size_t num_ranges() const { return _queue.size(); }

    int64_t total_estimate() const { return _queue.total_estimate(); }

    void split_worst() {
        Area area = _queue.get();
        uint32_t x_first_max, x_last_min;
        uint32_t y_first_max, y_last_min;
        uint32_t x_bits = bits::split_range(area.min.x, area.max.x, x_first_max, x_last_min);
        uint32_t y_bits = bits::split_range(area.min.y, area.max.y, y_first_max, y_last_min);
        if (x_bits > y_bits) {
            _queue.put(Area(area.min.x, area.min.y, x_first_max, area.max.y));
            _queue.put(Area(x_last_min, area.min.y,  area.max.x, area.max.y));
        } else {
            assert(y_bits > 0);
            _queue.put(Area(area.min.x, area.min.y, area.max.x, y_first_max));
            _queue.put(Area(area.min.x, y_last_min, area.max.x,  area.max.y));
        }
    }

    RangeVector extract_ranges() { return _queue.extract_ranges(); }
};

} // namespace vespalib::geo::<unnamed>

ZCurve::BoundingBox::BoundingBox(int32_t minx,
                                 int32_t maxx,
                                 int32_t miny,
                                 int32_t maxy)
    : _zMinx(ZCurve::encode(minx, 0)),
      _zMaxx(ZCurve::encode(maxx, 0)),
      _zMiny(ZCurve::encode(0, miny)),
      _zMaxy(ZCurve::encode(0, maxy))
{
}

ZCurve::RangeVector
ZCurve::find_ranges(int min_x, int min_y,
                    int max_x, int max_y)
{
    uint64_t x_size = (static_cast<int64_t>(max_x) - min_x + 1);
    uint64_t y_size = (static_cast<int64_t>(max_y) - min_y + 1);
    uint64_t total_size = (x_size > std::numeric_limits<uint32_t>::max() &&
                           y_size > std::numeric_limits<uint32_t>::max()) ?
                          std::numeric_limits<uint64_t>::max() :
                          (x_size * y_size);
    int64_t estimate_target = (total_size > std::numeric_limits<int64_t>::max() / 4) ?
                              std::numeric_limits<int64_t>::max() :
                              (total_size * 4);
    ZAreaSplitter splitter(min_x, min_y, max_x, max_y);
    while (splitter.total_estimate() > estimate_target && splitter.num_ranges() < 42) {
        splitter.split_worst();
    }
    RangeVector ranges = splitter.extract_ranges();
    std::sort(ranges.begin(), ranges.end());
    return ranges;
}

int64_t
ZCurve::encodeSlow(int32_t x, int32_t y)
{
    uint64_t res = 0;
    uint32_t ibit = 1;
    uint64_t obit = 1;
    for (;
         ibit != 0;
         ibit <<= 1, obit <<= 2)
    {
        if (static_cast<uint32_t>(x) & ibit)
            res |= obit;
        if (static_cast<uint32_t>(y) & ibit)
            res |= (obit << 1);
    }
    return static_cast<int64_t>(res);
}


void
ZCurve::decodeSlow(int64_t enc, int32_t *xp, int32_t *yp)
{
    uint32_t x = 0;
    uint32_t y = 0;
    uint64_t ibit = 1;
    uint32_t obit = 1;
    for (;
         ibit != 0;
         ibit <<= 2, obit <<= 1)
    {
        if ((static_cast<uint64_t>(enc) & ibit) != 0)
            x |= obit;
        if ((static_cast<uint64_t>(enc) & (ibit << 1)) != 0)
            y |= obit;
    }
    *xp = static_cast<int32_t>(x);
    *yp = static_cast<int32_t>(y);
}

}
