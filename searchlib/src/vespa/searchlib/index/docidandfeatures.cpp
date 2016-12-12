// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.docidandfeatures");
#include "docidandfeatures.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace search
{

namespace index
{

using vespalib::nbostream;

#if 0
void
DocIdAndFeatures::append(const DocIdAndFeatures &rhs, uint32_t localFieldId)
{
    assert(!rhs.getRaw());
    assert(rhs._fields.size() == 1);
    const WordDocFieldFeatures &field = rhs._fields.front();
    assert(field.getFieldId() == 0);
    uint32_t numElements = field.getNumElements();
    std::vector<WordDocFieldElementFeatures>::const_iterator element =
        rhs._elements.begin();
    std::vector<WordDocFieldElementWordPosFeatures>::const_iterator position =
        rhs._wordPositions.begin();
    assert(_fields.empty() || localFieldId > _fields.back().getFieldId());
    _fields.push_back(field);
    _fields.back().setFieldId(localFieldId);
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++element) {
        _elements.push_back(*element);
        for (uint32_t posResidue = element->getNumOccs(); posResidue > 0;
             --posResidue, ++position) {
            _wordPositions.push_back(*position);
        }
    }
}
#endif


nbostream &
operator<<(nbostream &out, const WordDocElementFeatures &features)
{
    out << features._elementId << features._numOccs <<
        features._weight << features._elementLen;
    return out;
}


nbostream &
operator>>(nbostream &in, WordDocElementFeatures &features)
{
    in >> features._elementId >> features._numOccs >>
        features._weight >> features._elementLen;
    return in;
}


nbostream &
operator<<(nbostream &out, const WordDocElementWordPosFeatures &features)
{
    out << features._wordPos;
    return out;
}


nbostream &
operator>>(nbostream &in, WordDocElementWordPosFeatures &features)
{
    in >> features._wordPos;
    return in;
}


nbostream &
operator<<(nbostream &out, const DocIdAndFeatures &features)
{
    out << features._docId;
    out.saveVector(features._elements).
        saveVector(features._wordPositions);
    out.saveVector(features._blob);
    out << features._bitOffset << features._bitLength << features._raw;
    return out;
}


nbostream &
operator>>(nbostream &in, DocIdAndFeatures &features)
{
    in >> features._docId;
    in.restoreVector(features._elements).
        restoreVector(features._wordPositions);
    in.restoreVector(features._blob);
    in >> features._bitOffset >> features._bitLength >> features._raw;
    return in;
}


} // namespace index

} // namespace search

#include <vespa/vespalib/objects/nbostream.hpp>

namespace vespalib {
    using search::index::WordDocElementFeatures;
    using search::index::WordDocElementWordPosFeatures;
    template nbostream& nbostream::saveVector<WordDocElementFeatures>(const std::vector<WordDocElementFeatures> &);
    template nbostream& nbostream::restoreVector<WordDocElementFeatures>(std::vector<WordDocElementFeatures> &);
    template nbostream& nbostream::saveVector<WordDocElementWordPosFeatures>(const std::vector<WordDocElementWordPosFeatures> &);
    template nbostream& nbostream::restoreVector<WordDocElementWordPosFeatures>(std::vector<WordDocElementWordPosFeatures> &);
}