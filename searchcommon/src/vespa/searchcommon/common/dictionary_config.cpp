// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionary_config.h"
#include <ostream>
#include <cassert>

namespace search {

std::ostream&
operator<<(std::ostream& os, const DictionaryConfig & cfg) {
    switch(cfg.getType()) {
    case DictionaryConfig::Type::BTREE:
        return os << "BTREE";
    case DictionaryConfig::Type::HASH:
        return os << "HASH";
    case DictionaryConfig::Type::BTREE_AND_HASH:
        return os << "BTREE_AND_HASH";
    }
    assert(false);
}

} // namespace search
