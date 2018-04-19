// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

class IAttributeVector;

/**
 * Short-lived guard for getting read access to an attribute vector.
 *
 * Sub-classes must ensure that the appropriate guard(s)
 * are held during the lifetime of this object.
 */
class AttributeReadGuard {
private:
    const IAttributeVector *_attr;

protected:
    AttributeReadGuard(const IAttributeVector *attr);

public:
    AttributeReadGuard(const AttributeReadGuard &) = delete;
    AttributeReadGuard &operator=(const AttributeReadGuard &) = delete;
    AttributeReadGuard(AttributeReadGuard &&) = delete;
    AttributeReadGuard &operator=(AttributeReadGuard &&) = delete;
    virtual ~AttributeReadGuard();
    const IAttributeVector *operator->() const { return _attr; }
    const IAttributeVector &operator*()  const { return *_attr; }
    const IAttributeVector *attribute() const { return _attr; }
};

}
