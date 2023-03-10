// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multivalueattributesaver.h"
#include "enumattributesaver.h"
#include "multi_value_mapping.h"
#include <vespa/searchcommon/attribute/multi_value_traits.h>

namespace search {

/**
 * Class for saving an enumerated multivalue attribute.
 *
 * Template argument MultiValueT is either ValueType or
 * multivalue::WeightedValue<ValueType>
 */
template <typename MultiValueT>
class MultiValueEnumAttributeSaver : public MultiValueAttributeSaver
{
    using Parent = MultiValueAttributeSaver;
    using MultiValueType = MultiValueT;
    using ValueType = multivalue::ValueType_t<MultiValueType>;
    using GenerationHandler = vespalib::GenerationHandler;
    using Parent::_frozenIndices;
    using MultiValueMapping = attribute::MultiValueMapping<MultiValueType>;

    const MultiValueMapping &_mvMapping;
    EnumAttributeSaver       _enumSaver;
    const IEnumStore        &_enum_store;
    uint64_t                 _compaction_count;
    bool compaction_interferred() const;
public:
    bool onSave(IAttributeSaveTarget &saveTarget) override;
    MultiValueEnumAttributeSaver(GenerationHandler::Guard &&guard,
                                 const attribute::AttributeHeader &header,
                                 const MultiValueMapping &mvMapping,
                                 IEnumStore &enumStore);
    ~MultiValueEnumAttributeSaver() override;
};


} // namespace search
