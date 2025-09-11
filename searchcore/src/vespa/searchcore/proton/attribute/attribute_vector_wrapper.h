// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <shared_mutex>

namespace proton {

/**
 * Class that stores the name of an attribute and a pointer to the corresponding AttributeVector.
 * Intended to be used in tracking of the initialization status of an attribute, where an AttributeVectorWrapper
 * with the name of the attribute is created in the AttributeInitializer
 * and the AttributeVector is added later once it is created.
 *
 * Thread-safe.
 */
class AttributeVectorWrapper {
public:
    using SP = std::shared_ptr<AttributeVectorWrapper>;
    explicit AttributeVectorWrapper(const std::string &name);

    const std::string& getName() const { return _name; }

    void setAttributeVector(const search::AttributeVector::SP &attr);
    search::AttributeVector::SP getAttributeVector() const;

private:
    mutable std::shared_mutex _mutex;

    const std::string _name;
    search::AttributeVector::SP _attr;
};

} // namespace proton
