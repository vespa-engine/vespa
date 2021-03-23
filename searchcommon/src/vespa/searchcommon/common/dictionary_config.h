// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>

namespace search {

/**
 * Contains the config required for setting up a suitable dictionary.
 */
class DictionaryConfig {
public:
    enum class Type { BTREE, HASH, BTREE_AND_HASH };
    DictionaryConfig() noexcept : _type(Type::BTREE) {}
    DictionaryConfig(Type ordering) noexcept : _type(ordering) {}
    Type getType() const { return _type; }
    bool operator == (const DictionaryConfig & b) const { return _type == b._type; }
private:
    Type _type;
};

std::ostream& operator<<(std::ostream& os, const DictionaryConfig & cfg);

} // namespace search
