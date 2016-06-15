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
 * Template argument IndexT is either multivalue::Index32 or multivalue::Index64
 */
template <typename MultiValueT, typename IndexT>
class MultiValueEnumAttributeSaver : public MultiValueAttributeSaver<IndexT>
{
    using Parent = MultiValueAttributeSaver<IndexT>;
    using Index = IndexT;
    using MultiValueType = MultiValueT;
    using ValueType = typename MultiValueType::ValueType;
    using GenerationHandler = vespalib::GenerationHandler;
    using Parent::_frozenIndices;
    using MultiValueMapping = MultiValueMappingT<MultiValueType, Index>;

    const MultiValueMapping &_mvMapping;
    EnumAttributeSaver      _enumSaver;
public:
    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
    MultiValueEnumAttributeSaver(GenerationHandler::Guard &&guard,
                                 const IAttributeSaveTarget::Config &cfg,
                                 const MultiValueMapping &mvMapping,
                                 const EnumStoreBase &enumStore);
    virtual ~MultiValueEnumAttributeSaver();
};


} // namespace search
