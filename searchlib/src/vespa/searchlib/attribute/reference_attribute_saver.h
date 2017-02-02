// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/datastore/unique_store.h>
#include <vespa/searchlib/datastore/unique_store_saver.h>
#include <vespa/searchlib/common/rcuvector.h>
#include "iattributesavetarget.h"

namespace search {
namespace attribute {

/*
 * Class for saving a reference attribute.
 */
class ReferenceAttributeSaver : public AttributeSaver
{
private:
    using EntryRef = search::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    using IndicesCopyVector = vespalib::Array<EntryRef>;
    using Store = datastore::UniqueStore<GlobalId, datastore::EntryRefT<22>>;
    using Saver = datastore::UniqueStoreSaver<GlobalId, datastore::EntryRefT<22>>;
    IndicesCopyVector _indices;
    const Store &_store;
    Saver _saver;

    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    ReferenceAttributeSaver(vespalib::GenerationHandler::Guard &&guard,
                            const IAttributeSaveTarget::Config &cfg,
                            IndicesCopyVector &&indices,
                            const Store &store);

    virtual ~ReferenceAttributeSaver();
};

} // namespace search::attribute
} // namespace search
