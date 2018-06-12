// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixedsourceselector.h"
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.fixed_source_selector");
#include "singlenumericattribute.hpp"

namespace search {

namespace {
    attribute::Config getConfig() { return attribute::Config(attribute::BasicType::INT8); }
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
    reserve(initialNumDocs);
    _source.commit();
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
    return selector;
}

FixedSourceSelector::UP
FixedSourceSelector::load(const vespalib::string & baseFileName)
{
    LoadInfo::UP info = extractLoadInfo(baseFileName);
    info->load();
    FixedSourceSelector::UP selector(new FixedSourceSelector(
                                             info->header()._defaultSource,
                                             info->header()._baseFileName,
                                             0));
    selector->setBaseId(info->header()._baseId);
    selector->_source.load();
    return selector;
}

void FixedSourceSelector::reserve(uint32_t numDocs)
{
    const uint32_t maxDoc(_source.getNumDocs());
    const uint32_t newMaxDocIdPlussOne(numDocs + 1);
    if (newMaxDocIdPlussOne > maxDoc) {
        uint32_t newDocId(0);
        for (_source.addDoc(newDocId); newDocId < numDocs; _source.addDoc(newDocId));
        for (uint32_t i = maxDoc; i < newMaxDocIdPlussOne; ++i) {
            _source.set(i, getDefaultSource());
        }
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
    _source.commit();
}

} // namespace search
