// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_gid_key_comparator.h"
#include "i_store.h"
#include <vespa/vespalib/btree/btreeiterator.h>
#include <vespa/searchlib/attribute/attributesaver.h>

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
    using MetaDataView = vespalib::ConstArrayRef<RawDocumentMetaData>;

private:
    GidIterator _gidIterator; // iterator over frozen tree
    MetaDataView _metaDataView;
    bool _writeDocSize;

    bool onSave(search::IAttributeSaveTarget &saveTarget) override;
public:
    DocumentMetaStoreSaver(vespalib::GenerationHandler::Guard &&guard,
                           const search::attribute::AttributeHeader &header,
                           const GidIterator &gidIterator,
                           MetaDataView metaDataView);

    ~DocumentMetaStoreSaver() override;
};

} // namespace proton
