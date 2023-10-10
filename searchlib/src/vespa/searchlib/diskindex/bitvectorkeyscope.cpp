// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectorkeyscope.h"
#include <cassert>

using search::diskindex::BitVectorKeyScope;

namespace search::diskindex {

const char *getBitVectorKeyScopeSuffix(BitVectorKeyScope scope)
{
    switch (scope) {
    case BitVectorKeyScope::SHARED_WORDS:
        return ".bidx";
    default:
        return ".idx";
    }
}

}
