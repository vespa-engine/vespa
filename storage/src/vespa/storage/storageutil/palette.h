// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Palette
 *
 * \brief Contains a set of distinct colors.
 *
 * When writing graphics like charts one wants to use distinct colors.
 * This class defines some distinct colors.
 */

#pragma once

#include <vector>
#include <cstdint>
#include <iosfwd>

namespace storage {

class Palette {
    std::vector<uint32_t> _colors;

public:
    Palette(uint32_t colorCount);

    uint32_t operator[](uint32_t colorIndex) const
        { return _colors[colorIndex]; }

    void printHtmlTablePalette(std::ostream& out) const;
};

} // storage

