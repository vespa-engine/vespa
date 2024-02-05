// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_normalization.h"
#include <ostream>

namespace search {

namespace {

const char *
to_str(search::Normalizing norm) noexcept {
    switch (norm) {
        case search::Normalizing::NONE:
            return "NONE";
        case search::Normalizing::LOWERCASE:
            return "LOWERCASE";
        case search::Normalizing::LOWERCASE_AND_FOLD:
            return "LOWERCASE_AND_FOLD";
    }
    abort();
}

}

std::ostream &
operator<<(std::ostream &os, Normalizing n) {
    os << to_str(n);
    return os;
}

}
