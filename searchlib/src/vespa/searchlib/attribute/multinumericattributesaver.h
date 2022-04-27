// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multivalueattributesaver.h"
#include <vespa/searchcommon/attribute/multi_value_traits.h>

namespace search {

/*
 * Class for saving a multivalue attribute.
 *
 * Template argument MultiValueT is either ValueType or
 * multivalue::WeightedValue<ValueType>
 */
template <typename MultiValueT>
class MultiValueNumericAttributeSaver : public MultiValueAttributeSaver
{
    using Parent = MultiValueAttributeSaver;
    using MultiValueType = MultiValueT;
    using ValueType = multivalue::ValueType_t<MultiValueType>;
    using GenerationHandler = vespalib::GenerationHandler;
    using Parent::_frozenIndices;
    using MultiValueMapping = attribute::MultiValueMapping<MultiValueType>;

    const MultiValueMapping &_mvMapping;
public:
    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
    MultiValueNumericAttributeSaver(GenerationHandler::Guard &&guard,
                                    const attribute::AttributeHeader &header,
                                    const MultiValueMapping &mvMapping);

    virtual ~MultiValueNumericAttributeSaver();
};


} // namespace search
