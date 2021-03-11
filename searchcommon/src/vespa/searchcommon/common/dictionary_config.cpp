// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionary_config.h"
#include <ostream>
#include <cassert>

namespace search {

std::ostream&
operator<<(std::ostream& os, const DictionaryConfig & cfg) {
    switch(cfg.getOrdering()) {
        case DictionaryConfig::Ordering::ORDERED:
        return os << "ORDERED";
        case DictionaryConfig::Ordering::UNORDERED:
        return os << "UNORDERED";
    }
    assert(false);
}

} // namespace search
