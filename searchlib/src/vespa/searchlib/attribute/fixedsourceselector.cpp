// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixedsourceselector.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.fixed_source_selector");

namespace search {

namespace {
    attribute::Config getConfig() { return attribute::Config(attribute::BasicType::INT8); }

uint32_t
capSelector(queryeval::sourceselector::Iterator::SourceStore &store, queryeval::Source defaultSource)
{
    uint32_t committedDocIdLimit = store.getCommittedDocIdLimit();
    uint32_t cappedSources = 0;
    for (uint32_t docId = 0; docId < committedDocIdLimit; ++docId) {
        queryeval::Source source = store.getFast(docId);
        if (source > defaultSource) {
            ++cappedSources;
            store.set(docId, defaultSource);
        }
    }
    return cappedSources;
}

}

FixedSourceSelector::Iterator::Iterator(const FixedSourceSelector & sourceSelector) :
    IIterator(sourceSelector._source),
    _attributeGuard(sourceSelector._realSource)
{ }

FixedSourceSelector::FixedSourceSelector(queryeval::Source defaultSource,
                                         const vespalib::string & attrBaseFileName,
                                         uint32_t initialNumDocs) :
    SourceSelector(defaultSource, AttributeVector::SP(new SourceStore(attrBaseFileName, getConfig()))),
    _source(static_cast<SourceStore &>(*_realSource))
{
    if (initialNumDocs != std::numeric_limits<uint32_t>::max()) {
        reserve(initialNumDocs);
        _source.commit();
    }
}

FixedSourceSelector::~FixedSourceSelector()
{
}

FixedSourceSelector::UP
FixedSourceSelector::cloneAndSubtract(const vespalib::string & attrBaseFileName,
                                      uint32_t diff)
{
    queryeval::Source newDefault = getNewSource(getDefaultSource(), diff);
    FixedSourceSelector::UP selector(new FixedSourceSelector(newDefault, attrBaseFileName, _source.getNumDocs()-1));
    for (uint32_t docId = 0; docId < _source.getNumDocs(); ++docId) {
        queryeval::Source src = _source.get(docId);
        src = getNewSource(src, diff);
        assert(src < SOURCE_LIMIT);
        selector->_source.set(docId, src);
    }
    selector->_source.commit();
    selector->setBaseId(getBaseId() + diff);
    selector->_source.setCommittedDocIdLimit(_source.getCommittedDocIdLimit());
    return selector;
}

FixedSourceSelector::UP
FixedSourceSelector::load(const vespalib::string & baseFileName, uint32_t currentId)
{
    LoadInfo::UP info = extractLoadInfo(baseFileName);
    info->load();
    uint32_t defaultSource = currentId - info->header()._baseId;
    assert(defaultSource < SOURCE_LIMIT);
    if (defaultSource != info->header()._defaultSource) {
        LOG(info, "Default source mismatch: header says %u, should be %u selector %s",
            (uint32_t) info->header()._defaultSource, defaultSource,
            baseFileName.c_str());
    }
    FixedSourceSelector::UP selector(new FixedSourceSelector(
                                             defaultSource,
                                             info->header()._baseFileName,
                                             std::numeric_limits<uint32_t>::max()));
    selector->setBaseId(info->header()._baseId);
    selector->_source.load();
    uint32_t cappedSources = capSelector(selector->_source, selector->getDefaultSource());
    if (cappedSources > 0) {
        LOG(warning, "%u sources capped in source selector %s", cappedSources, baseFileName.c_str());
    }
    return selector;
}

void FixedSourceSelector::reserve(uint32_t numDocs)
{
    const uint32_t maxDoc(_source.getNumDocs());
    const uint32_t newMaxDocIdPlussOne(numDocs + 1);
    if (newMaxDocIdPlussOne > maxDoc) {
        uint32_t newDocId(0);
        for (_source.addDoc(newDocId); newDocId < numDocs; _source.addDoc(newDocId));
    }
    for (uint32_t i = _source.getCommittedDocIdLimit(); i < newMaxDocIdPlussOne; ++i) {
        _source.set(i, getDefaultSource());
    }
}

void
FixedSourceSelector::setSource(uint32_t docId, queryeval::Source source)
{
    assert(source < SOURCE_LIMIT);
    /**
     * Due to matchingloop advancing 1 past end, we need to initialize data that
     * far too.
     **/
    reserve(docId+1);
    _source.update(docId, source);
    _source.updateUncommittedDocIdLimit(docId + 1);
    _source.commit();
}

void
FixedSourceSelector::compactLidSpace(uint32_t lidLimit)
{
    if (lidLimit < _source.getCommittedDocIdLimit()) {
        _source.compactLidSpace(lidLimit + 1);
    }
}

} // namespace search
