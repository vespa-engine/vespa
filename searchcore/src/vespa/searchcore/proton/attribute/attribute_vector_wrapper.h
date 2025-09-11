// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>

#include <memory>
#include <shared_mutex>
#include <string>

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
    explicit AttributeVectorWrapper(const std::string &name);

    const std::string& getName() const { return _name; }

    void setAttributeVector(const std::shared_ptr<search::AttributeVector> &attr);
    std::shared_ptr<search::AttributeVector> getAttributeVector() const;

private:
    mutable std::shared_mutex _mutex;

    const std::string _name;
    std::shared_ptr<search::AttributeVector> _attr;
};

} // namespace proton
