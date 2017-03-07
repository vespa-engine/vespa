// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    using GidIterator = search::btree::BTreeConstIterator<
        DocId,
        search::btree::BTreeNoLeafData,
        search::btree::NoAggregated,
        const KeyComp &>;
    using MetaDataStore = search::attribute::RcuVectorBase<RawDocumentMetaData>;

private:
    GidIterator _gidIterator; // iterator over frozen tree
    const MetaDataStore &_metaDataStore;
    bool _writeSize;

    virtual bool onSave(search::IAttributeSaveTarget &saveTarget) override;
public:
    DocumentMetaStoreSaver(vespalib::GenerationHandler::Guard &&guard,
                                 const search::IAttributeSaveTarget::Config &cfg,
                                 const GidIterator &gidIterator,
                                 const MetaDataStore &metaDataStore);

    virtual ~DocumentMetaStoreSaver();
};

} // namespace proton
