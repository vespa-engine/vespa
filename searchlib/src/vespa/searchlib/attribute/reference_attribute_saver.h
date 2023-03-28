// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "reference_attribute.h"
#include "reference.h"
#include "save_utils.h"
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/datastore/unique_store.h>
#include <vespa/vespalib/datastore/unique_store_enumerator.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::attribute {

/*
 * Class for saving a reference attribute to disk or memory buffers.
 *
 * .udat file contains sorted unique values after generic header, in
 * host byte order.
 *
 * .dat file contains enum values after generic header, in host byte order.
 *
 * enum value 0 means value not set.
 * enum value 1 means the first unique value.
 * enum value n means the nth unique value.
 */
class ReferenceAttributeSaver : public AttributeSaver
{
private:
    using EntryRef = vespalib::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    using Store = ReferenceAttribute::ReferenceStore;
    using Enumerator = Store::Enumerator;
    EntryRefVector _indices;
    const Store &_store;
    Enumerator _enumerator;

    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    ReferenceAttributeSaver(vespalib::GenerationHandler::Guard &&guard,
                            const AttributeHeader &header,
                            EntryRefVector&& indices,
                            Store &store);

    ~ReferenceAttributeSaver() override;
};

}
