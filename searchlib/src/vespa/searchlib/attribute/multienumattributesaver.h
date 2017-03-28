// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multivalueattributesaver.h"
#include "enumattributesaver.h"

namespace search {

/*
 * Class for saving an enumerated multivalue attribute.
 *
 * Template argument MultiValueT is either  multivalue::Value<ValueType> or
 * multivalue::WeightedValue<ValueType>
 */
template <typename MultiValueT>
class MultiValueEnumAttributeSaver : public MultiValueAttributeSaver
{
    using Parent = MultiValueAttributeSaver;
    using MultiValueType = MultiValueT;
    using ValueType = typename MultiValueType::ValueType;
    using GenerationHandler = vespalib::GenerationHandler;
    using Parent::_frozenIndices;
    using MultiValueMapping = attribute::MultiValueMapping<MultiValueType>;

    const MultiValueMapping &_mvMapping;
    EnumAttributeSaver      _enumSaver;
public:
    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
    MultiValueEnumAttributeSaver(GenerationHandler::Guard &&guard,
                                 const attribute::AttributeHeader &header,
                                 const MultiValueMapping &mvMapping,
                                 const EnumStoreBase &enumStore);
    virtual ~MultiValueEnumAttributeSaver();
};


} // namespace search
