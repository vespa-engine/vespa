// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search {
    class AttributeVector;
}
namespace vespalib {
    class Gate;
    class ISequencedTaskExecutor;
}

namespace proton {

/**
 * Class that provides exclusive read access to an attribute vector
 * while the write thread for that attribute is blocked.
 *
 * The attribute write thread is blocked while a guard is held.
 */
class ExclusiveAttributeReadAccessor
{
public:
    class Guard
    {
    private:
        const search::AttributeVector &_attribute;
        std::shared_ptr<vespalib::Gate> _exitGate;

    public:
        using UP = std::unique_ptr<Guard>;
        Guard(const search::AttributeVector &attribute,
              const std::shared_ptr<vespalib::Gate> &exitGate);
        ~Guard();
        const search::AttributeVector &get() const { return _attribute; }
    };

private:
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    AttributeVectorSP _attribute;
    vespalib::ISequencedTaskExecutor &_attributeFieldWriter;

public:
    using UP = std::unique_ptr<ExclusiveAttributeReadAccessor>;

    ExclusiveAttributeReadAccessor(const AttributeVectorSP &attribute,
                                   vespalib::ISequencedTaskExecutor &attributeFieldWriter);
    Guard::UP takeGuard();
};

} // namespace proton
