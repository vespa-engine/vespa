// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
public:
    AttributeInitializerResult(const AttributeVectorSP &attr);
    ~AttributeInitializerResult();
    const AttributeVectorSP &getAttribute() const { return _attr; }
    operator bool() const { return static_cast<bool>(_attr); }
};

} // namespace proton
