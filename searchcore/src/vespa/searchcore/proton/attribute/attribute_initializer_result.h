// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search {
class AttributeVector;
}

namespace proton {

/**
 * Class representing an initialized attribute.
 */
class AttributeInitializerResult
{
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    AttributeVectorSP _attr;
    bool _hideFromReading; // Delayed removal of attribute aspect
    bool _hideFromWriting; // Delayed addition of attribute aspect
public:
    AttributeInitializerResult(const AttributeVectorSP &attr,
                               bool hideFromReading,
                               bool hideFromWriting);
    ~AttributeInitializerResult();
    bool getHideFromReading() const { return _hideFromReading; }
    bool getHideFromWriting() const { return _hideFromWriting; }
    const AttributeVectorSP &getAttribute() const { return _attr; }
    operator bool() const { return static_cast<bool>(_attr); }
};

} // namespace proton
