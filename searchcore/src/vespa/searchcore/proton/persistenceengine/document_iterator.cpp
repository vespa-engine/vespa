// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_iterator.h"
#include "ipersistencehandler.h"
#include <vespa/persistence/spi/docentry.h>
#include <vespa/searchcore/proton/common/cachedselect.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/selectcontext.h>
#include <vespa/document/select/gid_filter.h>
#include <vespa/document/select/node.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/hash_map.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.persistenceengine.document_iterator");

using storage::spi::IterateResult;
using storage::spi::DocEntry;
using storage::spi::Timestamp;
using document::Document;
using document::DocumentId;
using document::GlobalId;
using storage::spi::DocumentMetaEnum;

namespace proton {

namespace {

std::unique_ptr<DocEntry>
createDocEntry(Timestamp timestamp, bool removed) {
    return DocEntry::create(timestamp, removed ? DocumentMetaEnum::REMOVE_ENTRY : DocumentMetaEnum::NONE);
}

std::unique_ptr<DocEntry>
createDocEntry(Timestamp timestamp, bool removed, std::string_view doc_type, const GlobalId &gid) {
    return DocEntry::create(timestamp, (removed ? DocumentMetaEnum::REMOVE_ENTRY : DocumentMetaEnum::NONE), doc_type, gid);
}

std::unique_ptr<DocEntry>
createDocEntry(Timestamp timestamp, bool removed, Document::UP doc, ssize_t defaultSerializedSize) {
    if (doc) {
        if (removed) {
            return DocEntry::create(timestamp, DocumentMetaEnum::REMOVE_ENTRY, doc->getId());
        } else {
            ssize_t serializedSize = defaultSerializedSize >= 0 ? defaultSerializedSize : doc->serialize().size();
            return DocEntry::create(timestamp, std::move(doc), serializedSize);
        }
    } else {
        return createDocEntry(timestamp, removed);
    }
}

} // namespace proton::<unnamed>

bool
DocumentIterator::checkMeta(const search::DocumentMetadata &meta) const
{
    if (!meta.valid()) {
        return false;
    }
    if (!_selection.getTimestampSubset().empty()) {
        return (std::binary_search(_selection.getTimestampSubset().begin(),
                                   _selection.getTimestampSubset().end(),
                                   Timestamp(meta.timestamp)));
    }
    if ((meta.timestamp < _selection.getFromTimestamp()) ||
        (meta.timestamp > _selection.getToTimestamp()))
    {
        return false;
    }
    if ((_versions == storage::spi::NEWEST_DOCUMENT_ONLY) && meta.removed) {
        return false;
    }
    return true;
}

DocumentIterator::DocumentIterator(const storage::spi::Bucket &bucket,
                                   document::FieldSet::SP fields,
                                   const storage::spi::Selection &selection,
                                   storage::spi::IncludedVersions versions,
                                   ssize_t defaultSerializedSize,
                                   bool ignoreMaxBytes,
                                   ReadConsistency readConsistency)
    : _bucket(bucket),
      _selection(selection),
      _versions(versions),
      _fields(std::move(fields)),
      _defaultSerializedSize((readConsistency == ReadConsistency::WEAK) ? defaultSerializedSize : -1),
      _readConsistency(readConsistency),
      _metaOnly(_fields->getType() == document::FieldSet::Type::NONE),
      _ignoreMaxBytes((readConsistency == ReadConsistency::WEAK) && ignoreMaxBytes),
      _fetchedData(false),
      _sources(),
      _nextItem(0),
      _list()
{
}

DocumentIterator::~DocumentIterator() = default;

void
DocumentIterator::add(const DocTypeName &doc_type_name, IDocumentRetriever::SP retriever)
{
    assert(doc_type_name.getName() == retriever->get_doc_type_name().getName());
    _sources.emplace_back(doc_type_name, std::move(retriever));
}

void
DocumentIterator::add(IDocumentRetriever::SP retriever)
{
    auto& name = retriever->get_doc_type_name();
    add(name, std::move(retriever));
}

IterateResult
DocumentIterator::iterate(size_t maxBytes)
{
    if ( ! _fetchedData ) {
        for (const auto & source : _sources) {
            fetchCompleteSource(source.first, *source.second, _list);
        }
        _fetchedData = true;
    }
    if ( _ignoreMaxBytes ) {
        return IterateResult(std::move(_list), true);
    } else {
        IterateResult::List results;
        for (size_t sz(0); (_nextItem < _list.size()) && ((sz < maxBytes) || results.empty()); _nextItem++) {
            DocEntry::UP item = std::move(_list[_nextItem]);
            sz += item->getSize();
            results.push_back(std::move(item));
        }
        return IterateResult(std::move(results), _nextItem >= _list.size());
    }
}

namespace {

class Matcher {
public:
    Matcher(const IDocumentRetriever &source, bool metaOnly, const std::string &selection) :
        _dscTrue(true),
        _metaOnly(metaOnly),
        _willAlwaysFail(false),
        _docidLimit(source.getDocIdLimit())
    {
        if (!(_metaOnly || selection.empty())) {
            LOG(spam, "ParseSelect: %s", selection.c_str());
            _cachedSelect = source.parseSelect(selection);
            _dscTrue = _cachedSelect->is_always_true();
            if (_cachedSelect->is_always_false() || _cachedSelect->is_always_invalid()) {
                assert(!_dscTrue);
                LOG(debug, "Nothing will ever match cs.allFalse = '%d', cs.allInvalid = '%d'",
                    _cachedSelect->is_always_false(), _cachedSelect->is_always_invalid());
                _willAlwaysFail = true;
            } else {
                _selectSession = _cachedSelect->createSession();
                using document::select::GidFilter;
                _gidFilter = GidFilter::for_selection_root_node(_selectSession->selectNode());
                _selectCxt = std::make_unique<SelectContext>(*_cachedSelect);
                _selectCxt->getAttributeGuards();
            }
        } else {
            _dscTrue = true;
        }
    }
    
    ~Matcher() {
        if (_selectCxt) {
            _selectCxt->dropAttributeGuards();
        }
    }

    [[nodiscard]] bool willAlwaysFail() const noexcept { return _willAlwaysFail; }

    [[nodiscard]] bool match(const search::DocumentMetadata & meta) const {
        if (meta.lid >= _docidLimit) {
            return false;
        }
        if (_dscTrue || _metaOnly) {
            return true;
        }
        if (!_gidFilter.gid_might_match_selection(meta.gid)) {
            return false;
        }
        assert(_selectCxt);
        _selectCxt->_docId = meta.lid;
        _selectCxt->_doc = nullptr;
        return _selectSession->contains_pre_doc(*_selectCxt);
    }
    [[nodiscard]] bool match(const search::DocumentMetadata & meta, const Document * doc) const {
        if (_dscTrue || _metaOnly) {
            return true;
        }
        assert(_selectCxt);
        _selectCxt->_docId = meta.lid;
        _selectCxt->_doc = doc;
        return (doc && (doc->getId().getGlobalId() == meta.gid) && _selectSession->contains_doc(*_selectCxt));
    }
private:
    bool                           _dscTrue;
    bool                           _metaOnly;
    bool                           _willAlwaysFail;
    uint32_t                       _docidLimit;
    CachedSelect::SP               _cachedSelect;
    std::unique_ptr<CachedSelect::Session> _selectSession;
    document::select::GidFilter    _gidFilter;
    std::unique_ptr<SelectContext> _selectCxt;
};

using LidIndexMap = vespalib::hash_map<uint32_t, uint32_t>;

class MatchVisitor : public search::IDocumentVisitor
{
public:
    MatchVisitor(const Matcher &matcher, const search::DocumentMetadata::Vector &metadata,
                 const LidIndexMap &lidIndexMap, const document::FieldSet *fields, IterateResult::List &list,
                 ssize_t defaultSerializedSize) :
        _matcher(matcher),
        _metadata(metadata),
        _lidIndexMap(lidIndexMap),
        _fields(fields),
        _list(list),
        _defaultSerializedSize(defaultSerializedSize),
        _allowVisitCaching(false)
    { }
    MatchVisitor & allowVisitCaching(bool allow) { _allowVisitCaching = allow; return *this; }
    void visit(uint32_t lid, document::Document::UP doc) override {
        const search::DocumentMetadata & meta = _metadata[_lidIndexMap[lid]];
        assert(lid == meta.lid);
        if (_matcher.match(meta, doc.get())) {
            if (doc && _fields) {
                document::FieldSet::stripFields(*doc, *_fields);
            }
            _list.push_back(createDocEntry(storage::spi::Timestamp(meta.timestamp), meta.removed, std::move(doc), _defaultSerializedSize));
        }
    }

    bool allowVisitCaching() const override {
        return _allowVisitCaching;
    }

private:
    const Matcher                          & _matcher;
    const search::DocumentMetadata::Vector & _metadata;
    const LidIndexMap                      & _lidIndexMap;
    const document::FieldSet               * _fields;
    IterateResult::List                    & _list;
    size_t                                   _defaultSerializedSize;
    bool                                     _allowVisitCaching;
};

}

void
DocumentIterator::fetchCompleteSource(const DocTypeName & doc_type_name,
                                      const IDocumentRetriever & source,
                                      IterateResult::List & list)
{
    IDocumentRetriever::ReadGuard sourceReadGuard(source.getReadGuard());
    search::DocumentMetadata::Vector metadata;
    source.getBucketMetadata(_bucket, metadata, false);
    if (metadata.empty()) {
        return;
    }
    LOG(debug, "metadata count before filtering: %zu", metadata.size());

    Matcher matcher(source, _metaOnly, _selection.getDocumentSelection().getDocumentSelection());
    if (matcher.willAlwaysFail()) {
        return;
    }

    LidIndexMap lidIndexMap(3*metadata.size());
    IDocumentRetriever::LidVector lidsToFetch;
    lidsToFetch.reserve(metadata.size());
    for (size_t i(0); i < metadata.size(); i++) {
        const search::DocumentMetadata & meta = metadata[i];
        if (checkMeta(meta)) {
            if (matcher.match(meta)) {
                lidsToFetch.emplace_back(meta.lid);
                lidIndexMap[meta.lid] = i;
            }
        }
    }
    LOG(debug, "metadata count after filtering: %zu", lidsToFetch.size());

    list.reserve(lidsToFetch.size());
    if ( _metaOnly ) {
        for (uint32_t lid : lidsToFetch) {
            const search::DocumentMetadata & meta = metadata[lidIndexMap[lid]];
            assert(lid == meta.lid);
            list.push_back(createDocEntry(storage::spi::Timestamp(meta.timestamp), meta.removed, doc_type_name.getName(), meta.gid));
        }
    } else {
        MatchVisitor visitor(matcher, metadata, lidIndexMap, _fields.get(), list, _defaultSerializedSize);
        visitor.allowVisitCaching(isWeakRead());
        source.visitDocuments(lidsToFetch, visitor, _readConsistency);
    }

}

}
