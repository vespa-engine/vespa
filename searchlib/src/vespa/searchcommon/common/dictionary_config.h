// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>
#include <cstdint>

namespace search {

/**
 * Contains the config required for setting up a suitable dictionary.
 */
class DictionaryConfig {
public:
    enum class Type : uint8_t { BTREE, HASH, BTREE_AND_HASH };
    enum class Match : uint8_t { CASED, UNCASED };
    DictionaryConfig() noexcept : _type(Type::BTREE), _match(Match::UNCASED) {}
    DictionaryConfig(Type type) noexcept : _type(type), _match(Match::UNCASED) {}
    DictionaryConfig(Type type, Match match) noexcept : _type(type), _match(match) {}
    Type getType() const { return _type; }
    Match getMatch() const { return _match; }
    bool operator == (const DictionaryConfig & b) const { return (_type == b._type) && (_match == b._match); }
private:
    Type  _type : 4;
    Match _match : 4;
};

std::ostream& operator<<(std::ostream& os, const DictionaryConfig & cfg);
std::ostream& operator<<(std::ostream& os, DictionaryConfig::Type type);
std::ostream& operator<<(std::ostream& os, DictionaryConfig::Match match);

} // namespace search
