// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search { class AttributeGuard; }
namespace search::attribute { class IAttributeVector; }

namespace search::common {


/**
 * This class contains meta-information about document locations (positions)
 * for all documents in the index, and references to the attributes
 * containing the actual document locations.
 */
class DocumentLocations
{
private:
    const search::attribute::IAttributeVector *_vec;

public:
    DocumentLocations(DocumentLocations &&);
    DocumentLocations & operator = (DocumentLocations &&);
    DocumentLocations();
    virtual ~DocumentLocations();

    void setVec(const search::attribute::IAttributeVector &vec) {
        _vec = &vec;
    }

    const search::attribute::IAttributeVector *getVec() const {
        return _vec;
    }
};

}
