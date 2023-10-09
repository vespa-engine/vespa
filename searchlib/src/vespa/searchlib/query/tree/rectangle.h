// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::query {

struct Rectangle {
    int32_t left;
    int32_t top;
    int32_t right;
    int32_t bottom;

    Rectangle() : left(0), top(0), right(0), bottom(0) {}
    Rectangle(int32_t l, int32_t t, int32_t r, int32_t b)
        : left(l), top(t), right(r), bottom(b) {}
};

}
