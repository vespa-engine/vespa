// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::query {

struct Point {
    int64_t x;
    int64_t y;
    Point() : x(0), y(0) {}
    Point(int64_t x_in, int64_t y_in) : x(x_in), y(y_in) {}
};

inline bool operator==(const Point &p1, const Point &p2) {
    return p1.x == p2.x && p1.y == p2.y;
}

}
