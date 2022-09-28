// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "collectiontype.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search::attribute {

const CollectionType::TypeInfo CollectionType::_typeTable[CollectionType::MAX_TYPE] = {
    { CollectionType::SINGLE, "single" },
    { CollectionType::ARRAY,  "array" },
    { CollectionType::WSET,   "weightedset" }
};

CollectionType::Type
CollectionType::asType(const vespalib::string &t)
{
    for (size_t i(0); i < sizeof(_typeTable)/sizeof(_typeTable[0]); i++) {
        if (t == _typeTable[i]._name) {
            return _typeTable[i]._type;
        }
    }
    throw vespalib::IllegalStateException(t + " not recognized as valid attribute collection type");
    return SINGLE;
}

}
