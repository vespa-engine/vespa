// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cassert>
#include <vector>

namespace vespalib::geo {

/**
 * @brief Utility methods for a Z-curve (Morton-order) encoder and decoder.
 */
class ZCurve
{
public:

    /**
     * @brief Represents a box in the xy-space, storing the max/min x
     * and y values as interleaved z-code.
     */
    class BoundingBox
    {
    private:
        int64_t _zMinx;		/* Min X coordinate, interleaved (Z curve) */
        int64_t _zMaxx;		/* Max X coordinate, interleaved (Z curve) */
        int64_t _zMiny;		/* Min Y coordinate, interleaved (Z curve) */
        int64_t _zMaxy;		/* Max Y coordinate, interleaved (Z curve) */

    public:
        BoundingBox(int32_t minx, int32_t maxx, int32_t miny, int32_t maxy);

        ~BoundingBox() = default;

        int64_t getzMinx() const { return _zMinx; }
        int64_t getzMaxx() const { return _zMaxx; }
        int64_t getzMiny() const { return _zMiny; }
        int64_t getzMaxy() const { return _zMaxy; }

        /**
         * Returns true if the given z-encoded xy coordinate is
         * outside this BoundingBox, false otherwise.
         *
         * @param docxy  Z-encoded xy-value.
         * @return true if the given coordinate is outside this box.
         */
        bool
        getzFailBoundingBoxTest(int64_t docxy) const
        {
            int64_t doczy = docxy & UINT64_C(0xaaaaaaaaaaaaaaaa);
            int64_t doczx = docxy & UINT64_C(0x5555555555555555);

            return (doczy < getzMiny() ||
                    doczy > getzMaxy() ||
                    static_cast<int64_t>(doczx << 1) <
                    static_cast<int64_t>(getzMinx() << 1) ||
                    static_cast<int64_t>(doczx << 1) >
                    static_cast<int64_t>(getzMaxx() << 1));
        }
    };

    /**
     * Encode two 32 bit integers by bit-interleaving them into one 64 bit
     * integer value. The x-direction owns the least significant bit (bit
     * 0). Both x and y can have negative values.<p>
     *
     * This is a time-efficient implementation. In the first step, the input
     * value is split in two blocks, one containing the most significant bits,
     * and the other containing the least significant bits. The most
     * significant block is then shifted left for as many bits it contains.
     * For each following step every block from the previous step is split in
     * the same manner, with a least and most significant block, and the most
     * significant blocks are shifted left for as many bits they contain (half
     * the number from the previous step). This continues until each block has
     * only one bit.<p>
     *
     * This algorithm works by placing the LSB of all blocks in the correct
     * position after the bit-shifting is done in each step. This algorithm
     * is quite similar to computing the Hamming Weight (or population count)
     * of a bit string, see http://en.wikipedia.org/wiki/Hamming_weight.<p>
     *
     * The encoding operations in this method should require 42 cpu operations,
     * of which many can be executed in parallell.<p>
     *
     * @param x  x value
     * @param y  y value
     * @return  The bit-interleaved long containing x and y.
     */
    static int64_t
    encode(int32_t x, int32_t y)
    {

        uint64_t rx = (static_cast<uint64_t>(static_cast<uint32_t>(x) &
                               0xffff0000u) << 16) |
                      (static_cast<uint32_t>(x) & 0x0000ffffu);
        uint64_t ry = (static_cast<uint64_t>(static_cast<uint32_t>(y) &
                               0xffff0000u) << 16) |
                      (static_cast<uint32_t>(y) & 0x0000ffffu);
        rx = ((rx & UINT64_C(0xff00ff00ff00ff00)) << 8) |
             (rx & UINT64_C(0x00ff00ff00ff00ff));
        ry = ((ry & UINT64_C(0xff00ff00ff00ff00)) << 8) |
             (ry & UINT64_C(0x00ff00ff00ff00ff));
        rx = ((rx & UINT64_C(0xf0f0f0f0f0f0f0f0)) << 4) |
             (rx & UINT64_C(0x0f0f0f0f0f0f0f0f));
        ry = ((ry & UINT64_C(0xf0f0f0f0f0f0f0f0)) << 4) |
             (ry & UINT64_C(0x0f0f0f0f0f0f0f0f));
        rx = ((rx & UINT64_C(0xcccccccccccccccc)) << 2) |
             (rx & UINT64_C(0x3333333333333333));
        ry = ((ry & UINT64_C(0xcccccccccccccccc)) << 2) |
             (ry & UINT64_C(0x3333333333333333));
        rx = ((rx & UINT64_C(0xaaaaaaaaaaaaaaaa)) << 1) |
             (rx & UINT64_C(0x5555555555555555));
        ry = ((ry & UINT64_C(0xaaaaaaaaaaaaaaaa)) << 1) |
             (ry & UINT64_C(0x5555555555555555));
        return static_cast<int64_t>(rx | (ry << 1));
    }

    /**
     * Decode a 64-bit z-value to 32-bit x and y values.
     *
     * @param enc  The bit-interleaved z-value containing x and y.
     * @param xp   Return value, pointer to the decoded x value.
     * @param yp   Return value, pointer to the decoded y value.
     */
    static void
    decode(int64_t enc, int32_t *xp, int32_t *yp)
    {
        uint64_t x = static_cast<uint64_t>(enc) & UINT64_C(0x5555555555555555);
        uint64_t y = static_cast<uint64_t>(enc) & UINT64_C(0xaaaaaaaaaaaaaaaa);

        x = ((x & UINT64_C(0xcccccccccccccccc)) >> 1) |
            (x & UINT64_C(0x3333333333333333));
        y = ((y & UINT64_C(0xcccccccccccccccc)) >> 1) |
            (y & UINT64_C(0x3333333333333333));
        x = ((x & UINT64_C(0xf0f0f0f0f0f0f0f0)) >> 2) |
            (x & UINT64_C(0x0f0f0f0f0f0f0f0f));
        y = ((y & UINT64_C(0xf0f0f0f0f0f0f0f0)) >> 2) |
            (y & UINT64_C(0x0f0f0f0f0f0f0f0f));
        x = ((x & UINT64_C(0xff00ff00ff00ff00)) >> 4) |
            (x & UINT64_C(0x00ff00ff00ff00ff));
        y = ((y & UINT64_C(0xff00ff00ff00ff00)) >> 4) |
            (y & UINT64_C(0x00ff00ff00ff00ff));
        x = ((x & UINT64_C(0xffff0000ffff0000)) >> 8) |
            (x & UINT64_C(0x0000ffff0000ffff));
        y = ((y & UINT64_C(0xffff0000ffff0000)) >> 8) |
            (y & UINT64_C(0x0000ffff0000ffff));
        x = ((x & UINT64_C(0xffffffff00000000)) >> 16) |
            (x & UINT64_C(0x00000000ffffffff));
        y = ((y & UINT64_C(0xffffffff00000000)) >> 16) |
            (y & UINT64_C(0x00000000ffffffff));
        *xp = static_cast<int32_t>(x);
        *yp = static_cast<int32_t>(y >> 1);
    }

    /**
     * A point in space, holding both x,y and z coordinates, where z
     * is not z, but Z.
     **/
    struct Point {
        const int32_t x;
        const int32_t y;
        const int64_t z;
        Point(int32_t x_, int32_t y_) : x(x_), y(y_), z(encode(x_, y_)) {}
    };

    /**
     * An area defined by its upper left and lower right corners. The
     * z-coordinates between these corners act as a spacial
     * over-estimation of the actual area. These areas may never cross
     * signed borders, since that would break the whole concept of
     * hierarchical spatial partitioning.
     **/
    struct Area {
        const Point min;
        const Point max;
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

    class Range
    {
    private:
        int64_t _min;
        int64_t _max;

    public:
        Range(int64_t min_, int64_t max_) : _min(std::min(min_, max_)), _max(std::max(min_, max_)) {}
        int64_t min() const { return _min; }
        int64_t max() const { return _max; }
        void min(int64_t value) { _min = value; }
        void max(int64_t value) { _max = value; }
        bool operator<(const Range &rhs) const { return (_min < rhs._min); }
    };
    using RangeVector = std::vector<Range>;

    /**
     * Given an inclusive bounding box, return a set of ranges in
     * z-curve values that contain all points inside the bounding
     * box. Note that the returned ranges may contain points that are
     * outside the bounding box. NB: not yet even remotely optimal.
     **/
    static RangeVector find_ranges(int min_x, int min_y,
                                   int max_x, int max_y);

    static int64_t
    encodeSlow(int32_t x, int32_t y);

    static void
    decodeSlow(int64_t enc, int32_t *xp, int32_t *yp);
};

}
