// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_iterator.h"
#include <vespa/document/select/gid_filter.h>
#include <vespa/document/select/node.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".proton.persistenceengine.document_iterator");

using storage::spi::IterateResult;
using storage::spi::DocEntry;
using storage::spi::Timestamp;
using document::Document;
using document::DocumentId;


namespace proton {

namespace {

DocEntry *createDocEntry(Timestamp timestamp, bool removed) {
    int flags = removed ? storage::spi::REMOVE_ENTRY : storage::spi::NONE;
    return new DocEntry(timestamp, flags);
}

DocEntry *createDocEntry(Timestamp timestamp, bool removed, Document::UP doc, ssize_t defaultSerializedSize) {
    if (doc) {
        if (removed) {
            return new DocEntry(timestamp, storage::spi::REMOVE_ENTRY, doc->getId());
        } else {
            ssize_t serializedSize = defaultSerializedSize >= 0 ? defaultSerializedSize : doc->getSerializedSize();
            return new DocEntry(timestamp, storage::spi::NONE, std::move(doc), serializedSize);
        }
    } else {
        return createDocEntry(timestamp, removed);
    }
}

} // namespace proton::<unnamed>

bool
DocumentIterator::useDocumentSelection() const
{
    return (!_metaOnly &&
            !_selection.getDocumentSelection().getDocumentSelection().empty());
}

bool
DocumentIterator::checkMeta(const search::DocumentMetaData &meta) const
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
                                   const document::FieldSet& fields,
                                   const storage::spi::Selection &selection,
                                   storage::spi::IncludedVersions versions,
                                   ssize_t defaultSerializedSize,
                                   bool ignoreMaxBytes,
                                   ReadConsistency readConsistency)
    : _bucket(bucket),
      _selection(selection),
      _versions(versions),
      _fields(fields.clone()),
      _defaultSerializedSize((readConsistency == ReadConsistency::WEAK) ? defaultSerializedSize : -1),
      _readConsistency(readConsistency),
      _metaOnly(fields.getType() == document::FieldSet::NONE),
      _ignoreMaxBytes((readConsistency == ReadConsistency::WEAK) && ignoreMaxBytes),
      _fetchedData(false),
      _sources(),
      _nextItem(0),
      _list()
{
}

DocumentIterator::~DocumentIterator() {}

void
DocumentIterator::add(const IDocumentRetriever::SP &retriever)
{
    _sources.push_back(retriever);
}

IterateResult
DocumentIterator::iterate(size_t maxBytes)
{
    if ( ! _fetchedData ) {
        for (const IDocumentRetriever::SP & source : _sources) {
            fetchCompleteSource(*source, _list);
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
    Matcher(const IDocumentRetriever &source, bool metaOnly, const vespalib::string &selection) :
        _dscTrue(true),
        _metaOnly(metaOnly),
        _willAlwaysFail(false),
        _docidLimit(source.getDocIdLimit())
    {
        if (!(_metaOnly || selection.empty())) {
            LOG(spam, "ParseSelect: %s", selection.c_str());
            _cachedSelect = source.parseSelect(selection);
            _dscTrue = _cachedSelect->allTrue();
            if (_cachedSelect->allFalse() || _cachedSelect->allInvalid()) {
                assert(!_dscTrue);
                LOG(debug, "Nothing will ever match cs.allFalse = '%d', cs.allInvalid = '%d'",
                    _cachedSelect->allFalse(), _cachedSelect->allInvalid());
                _willAlwaysFail = true;
            } else {
                _selectSession = _cachedSelect->createSession();
                using document::select::GidFilter;
                _gidFilter = GidFilter::for_selection_root_node(_selectSession->selectNode());
                _selectCxt.reset(new SelectContext(*_cachedSelect));
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

    bool willAlwaysFail() const { return _willAlwaysFail; }

    bool match(const search::DocumentMetaData & meta) const {
        if (meta.lid >= _docidLimit) {
            return false;
        }
        if (_dscTrue || _metaOnly) {
            return true;
        }
        if (_selectCxt) {
            _selectCxt->_docId = meta.lid;
        }
        if (!_gidFilter.gid_might_match_selection(meta.gid)) {
            return false;
        }
        return _selectSession->contains(*_selectCxt);
    }
    bool match(const search::DocumentMetaData & meta, const Document * doc) const {
        if (_dscTrue || _metaOnly) {
            return true;
        }
        return (doc && (doc->getId().getGlobalId() == meta.gid) && _selectSession->contains(*doc));
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

typedef vespalib::hash_map<uint32_t, uint32_t> LidIndexMap;

class MatchVisitor : public search::IDocumentVisitor
{
public:
    MatchVisitor(const Matcher &matcher, const search::DocumentMetaData::Vector &metaData,
                 const LidIndexMap &lidIndexMap, const document::FieldSet *fields, IterateResult::List &list,
                 ssize_t defaultSerializedSize) :
        _matcher(matcher),
        _metaData(metaData),
        _lidIndexMap(lidIndexMap),
        _fields(fields),
        _list(list),
        _defaultSerializedSize(defaultSerializedSize),
        _allowVisitCaching(false)
    { }
    MatchVisitor & allowVisitCaching(bool allow) { _allowVisitCaching = allow; return *this; }
    void visit(uint32_t lid, document::Document::UP doc) override {
        const search::DocumentMetaData & meta = _metaData[_lidIndexMap[lid]];
        assert(lid == meta.lid);
        if (_matcher.match(meta, doc.get())) {
            if (doc && _fields) {
                document::FieldSet::stripFields(*doc, *_fields);
            }
            _list.emplace_back(createDocEntry(meta.timestamp, meta.removed, std::move(doc), _defaultSerializedSize));
        }
    }

    virtual bool allowVisitCaching() const override {
        return _allowVisitCaching;
    }

private:
    const Matcher                          & _matcher;
    const search::DocumentMetaData::Vector & _metaData;
    const LidIndexMap                      & _lidIndexMap;
    const document::FieldSet               * _fields;
    IterateResult::List                    & _list;
    size_t                                   _defaultSerializedSize;
    bool                                     _allowVisitCaching;
};

}

void
DocumentIterator::fetchCompleteSource(const IDocumentRetriever & source, IterateResult::List & list)
{
    IDocumentRetriever::ReadGuard sourceReadGuard(source.getReadGuard());
    search::DocumentMetaData::Vector metaData;
    source.getBucketMetaData(_bucket, metaData);
    if (metaData.empty()) {
        return;
    }
    LOG(debug, "metadata count before filtering: %zu", metaData.size());

    Matcher matcher(source, _metaOnly, _selection.getDocumentSelection().getDocumentSelection());
    if (matcher.willAlwaysFail()) {
        return;
    }

    LidIndexMap lidIndexMap(3*metaData.size());
    IDocumentRetriever::LidVector lidsToFetch;
    lidsToFetch.reserve(metaData.size());
    for (size_t i(0); i < metaData.size(); i++) {
        const search::DocumentMetaData & meta = metaData[i];
        if (checkMeta(meta)) {
            if (matcher.match(meta)) {
                lidsToFetch.emplace_back(meta.lid);
                lidIndexMap[meta.lid] = i;
            }
        }
    }
    LOG(debug, "metadata count after filtering: %zu", lidsToFetch.size());

    if ( _metaOnly ) {
        for (uint32_t lid : lidsToFetch) {
            const search::DocumentMetaData & meta = metaData[lidIndexMap[lid]];
            assert(lid == meta.lid);
            list.emplace_back(createDocEntry(meta.timestamp, meta.removed));
        }
    } else {
        MatchVisitor visitor(matcher, metaData, lidIndexMap, _fields.get(), list, _defaultSerializedSize);
        visitor.allowVisitCaching(isWeakRead());
        source.visitDocuments(lidsToFetch, visitor, _readConsistency);
    }

}

}
