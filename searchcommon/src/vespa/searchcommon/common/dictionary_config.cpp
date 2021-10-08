// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionary_config.h"
#include <ostream>
#include <cassert>

namespace search {

std::ostream&
operator<<(std::ostream& os, const DictionaryConfig & cfg) {
    return os << cfg.getType() << "," << cfg.getMatch();
}

std::ostream&
operator<<(std::ostream& os, DictionaryConfig::Type type) {

    switch (type) {
        case DictionaryConfig::Type::BTREE:
            return os << "BTREE";
        case DictionaryConfig::Type::HASH:
            return os << "HASH";
        case DictionaryConfig::Type::BTREE_AND_HASH:
            return os << "BTREE_AND_HASH";
    }
    assert(false);
}

std::ostream&
operator<<(std::ostream& os, DictionaryConfig::Match match) {
    switch(match) {
        case DictionaryConfig::Match::CASED:
            return os << "CASE_SENSTITIVE";
        case DictionaryConfig::Match::UNCASED:
            return os << "CASE_INSENSTITIVE";
    }
    assert(false);
}

} // namespace search
