// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docidmapper.h"
#include <vespa/searchlib/common/documentsummary.h>

#define NO_DOC static_cast<uint32_t>(-1)

namespace search::diskindex {

DocIdMapping::DocIdMapping()
    : _selector(nullptr),
      _docIdLimit(0u),
      _selectorId(0)
{
}


void
DocIdMapping::clear()
{
    _docIdLimit = 0;
    _selector = nullptr;
    _selectorId = 0;
}


void
DocIdMapping::setup(uint32_t docIdLimit)
{
    _docIdLimit = docIdLimit;
    _selector = nullptr;
    _selectorId = 0;
}


void
DocIdMapping::setup(uint32_t docIdLimit, const SelectorArray *selector, uint8_t selectorId)
{
    _docIdLimit = docIdLimit;
    _selector = selector;
    _selectorId = selectorId;
}


bool
DocIdMapping::readDocIdLimit(const vespalib::string &mergedDir)
{
    uint32_t docIdLimit = 0;
    if (!search::docsummary::DocumentSummary::readDocIdLimit(mergedDir, docIdLimit)) {
        return false;
    }
    _docIdLimit = docIdLimit;
    return true;
}



}
