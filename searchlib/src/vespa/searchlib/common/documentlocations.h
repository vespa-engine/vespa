// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributeguard.h>

namespace search {
namespace common {


/**
 * This class contains meta-information about document locations (positions)
 * for all documents in the index, and references to the attributes
 * containing the actual document locations.
 */
class DocumentLocations
{

private:
    search::AttributeGuard::UP _vec_guard;
    const search::attribute::IAttributeVector *_vec;

public:
    DocumentLocations(void);

    void setVecGuard(search::AttributeGuard::UP guard) {
        _vec_guard = std::move(guard);
        setVec(_vec_guard.get()->get());
    }

    void setVec(const search::attribute::IAttributeVector &vec) {
        _vec = &vec;
    }

    const search::attribute::IAttributeVector *getVec() const {
        return _vec;
    }
};


}
}

