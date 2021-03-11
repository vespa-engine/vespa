// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>

namespace search {

/**
 * Contains the config required for setting up a suitable dictionary.
 */
class DictionaryConfig {
public:
    enum class Ordering { ORDERED, UNORDERED };
    DictionaryConfig() noexcept : _ordering(Ordering::ORDERED) {}
    DictionaryConfig(Ordering ordering) noexcept : _ordering(ordering) {}
    Ordering getOrdering() const { return _ordering; }
    bool operator == (const DictionaryConfig & b) const { return _ordering == b._ordering; }
private:
    Ordering _ordering;
};

std::ostream& operator<<(std::ostream& os, const DictionaryConfig & cfg);

} // namespace search
