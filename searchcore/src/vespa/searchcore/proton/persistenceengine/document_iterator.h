// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_retriever.h"
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/selection.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/document/fieldset/fieldset.h>

namespace proton {

class IPersistenceHandler;

class DocumentIterator
{
private:
    using ReadConsistency = storage::spi::ReadConsistency;
    using HandlerWithRetriever = std::pair<const IPersistenceHandler*, IDocumentRetriever::SP>;

    const storage::spi::Bucket            _bucket;;
    const storage::spi::Selection         _selection;
    const storage::spi::IncludedVersions  _versions;
    const document::FieldSet::SP          _fields;
    const ssize_t                         _defaultSerializedSize;
    const ReadConsistency                 _readConsistency;
    const bool                            _metaOnly;
    const bool                            _ignoreMaxBytes;
    bool                                  _fetchedData;
    std::vector<HandlerWithRetriever>     _sources;
    size_t                                _nextItem;
    storage::spi::IterateResult::List     _list;


    [[nodiscard]] bool checkMeta(const search::DocumentMetaData &meta) const;
    void fetchCompleteSource(const IPersistenceHandler * handler,
                             const IDocumentRetriever & source,
                             storage::spi::IterateResult::List & list);
    [[nodiscard]] bool isWeakRead() const { return _readConsistency == ReadConsistency::WEAK; }

public:
    DocumentIterator(const storage::spi::Bucket &bucket, document::FieldSet::SP fields,
                     const storage::spi::Selection &selection, storage::spi::IncludedVersions versions,
                     ssize_t defaultSerializedSize, bool ignoreMaxBytes,
                     ReadConsistency readConsistency=ReadConsistency::STRONG);
    ~DocumentIterator();
    void add(const IPersistenceHandler *handler, IDocumentRetriever::SP retriever);
    void add(IDocumentRetriever::SP retriever);
    storage::spi::IterateResult iterate(size_t maxBytes);
};

} // namespace proton

