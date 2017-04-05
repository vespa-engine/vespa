// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {
class AttributeVector;
class ISequencedTaskExecutor;
}
namespace vespalib { class Gate; }

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
    search::ISequencedTaskExecutor &_attributeFieldWriter;

public:
    using UP = std::unique_ptr<ExclusiveAttributeReadAccessor>;

    ExclusiveAttributeReadAccessor(const AttributeVectorSP &attribute,
                                   search::ISequencedTaskExecutor &attributeFieldWriter);
    Guard::UP takeGuard();
};

} // namespace proton
