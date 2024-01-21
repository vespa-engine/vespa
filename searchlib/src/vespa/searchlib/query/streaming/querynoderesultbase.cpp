// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "querynoderesultbase.h"
#include <ostream>

namespace search::streaming {

namespace {

const char* to_str(Normalizing norm) noexcept {
    switch (norm) {
    case Normalizing::NONE:               return "NONE";
    case Normalizing::LOWERCASE:          return "LOWERCASE";
    case Normalizing::LOWERCASE_AND_FOLD: return "LOWERCASE_AND_FOLD";
    }
    abort();
}

}

std::ostream& operator<<(std::ostream& os, Normalizing n) {
    os << to_str(n);
    return os;
}

}
