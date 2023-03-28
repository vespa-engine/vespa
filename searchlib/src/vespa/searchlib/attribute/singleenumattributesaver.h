// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "enumattributesaver.h"
#include "save_utils.h"

namespace search {

/*
 * Class for saving a single value enumerated attribute.
 */
class SingleValueEnumAttributeSaver : public AttributeSaver
{
private:
    attribute::EntryRefVector _indices;
    EnumAttributeSaver        _enumSaver;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    SingleValueEnumAttributeSaver(vespalib::GenerationHandler::Guard &&guard,
                                  const attribute::AttributeHeader &header,
                                  attribute::EntryRefVector &&indices,
                                  IEnumStore &enumStore);

    ~SingleValueEnumAttributeSaver() override;
};

} // namespace search
