// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "documentmetastore.h"

namespace proton {

/*
 * Class holding the necessary context for saving a document meta
 * store.  The generation guard in the parent class prevents lids from
 * being reused during the save operation, but timestamp and
 * bucketusedbits can reflect future operations relative to when the
 * document meta store was logically saved.  Thus it is important to
 * replay the same operations at startup.
 */
class DocumentMetaStoreSaver : public search::AttributeSaver
{
public:
    using KeyComp = documentmetastore::LidGidKeyComparator;
    using DocId = documentmetastore::IStore::DocId;
    using GidIterator = vespalib::btree::BTreeConstIterator<
        documentmetastore::GidToLidMapKey,
        vespalib::btree::BTreeNoLeafData,
        vespalib::btree::NoAggregated,
        const KeyComp &>;
    using MetaDataStore = vespalib::RcuVectorBase<RawDocumentMetaData>;

private:
    GidIterator _gidIterator; // iterator over frozen tree
    const MetaDataStore &_metaDataStore;
    bool _writeDocSize;

    bool onSave(search::IAttributeSaveTarget &saveTarget) override;
public:
    DocumentMetaStoreSaver(vespalib::GenerationHandler::Guard &&guard,
                           const search::attribute::AttributeHeader &header,
                           const GidIterator &gidIterator,
                           const MetaDataStore &metaDataStore);

    ~DocumentMetaStoreSaver() override;
};

} // namespace proton
