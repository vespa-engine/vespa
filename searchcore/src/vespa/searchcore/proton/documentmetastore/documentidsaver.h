// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_gid_key_comparator.h"
#include "i_store.h"
#include <vespa/vespalib/btree/btreeiterator.h>
#include "raw_document_metadata.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.h>

namespace proton {

/**
 * Implements saving document ids in binary format.
 **/
class DocumentIdSaver {
public:
    using KeyComp = documentmetastore::LidGidKeyComparator;
    using GidIterator = vespalib::btree::BTreeConstIterator<
        documentmetastore::GidToLidMapKey,
        vespalib::btree::BTreeNoLeafData,
        vespalib::btree::NoAggregated,
        const KeyComp &>;
    using MetaDataView = std::span<const RawDocumentMetadata>;
    using TypeMapper = vespalib::datastore::ArrayStoreDynamicTypeMapper<char>;
    using DocumentIdEntryRef = vespalib::datastore::EntryRefT<19>;
    using DocumentIdStore = vespalib::datastore::ArrayStore<char, DocumentIdEntryRef, TypeMapper>;
    DocumentIdSaver(const GidIterator &gidIterator, MetaDataView metaDataView, const DocumentIdStore& docid_store);
    ~DocumentIdSaver();
    void save(search::BufferWriter& writer) const;

private:
    GidIterator            _gid_iterator;
    MetaDataView           _meta_data_view;
    const DocumentIdStore& _docid_store;
};

} // namespace proton
