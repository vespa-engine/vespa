// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::attribute {

class AttributeReadGuard;

/**
 * Interface for an attribute vector used to create a short-lived read guard over that attribute.
 */
class ReadableAttributeVector {
public:
    virtual ~ReadableAttributeVector() {}
    virtual std::unique_ptr<AttributeReadGuard> makeReadGuard(bool stableEnumGuard) const = 0;
};

}
