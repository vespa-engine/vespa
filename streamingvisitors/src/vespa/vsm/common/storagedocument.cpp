// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagedocument.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.storagedocument");

using NestedIterator = document::FieldValue::PathRange;

namespace vsm {

StorageDocument::StorageDocument(document::Document::UP doc, const SharedFieldPathMap & fim, size_t fieldNoLimit) :
    Document(fieldNoLimit),
    _doc(std::move(doc)),
    _fieldMap(fim),
    _cachedFields(getFieldCount()),
    _backedFields()
{
    _backedFields.reserve(getFieldCount());
}

StorageDocument::~StorageDocument() = default;

namespace {
    FieldPath _emptyFieldPath;
    StorageDocument::SubDocument _empySubDocument(nullptr, _emptyFieldPath.getFullRange());
}

const StorageDocument::SubDocument &
StorageDocument::getComplexField(FieldIdT fId) const
{
    if (_cachedFields[fId].getFieldValue() == nullptr) {
        const FieldPath & fp = (*_fieldMap)[fId];
        if ( ! fp.empty() ) {
            const document::StructuredFieldValue * sfv = _doc.get();
            NestedIterator nested = fp.getFullRange();
            const document::FieldPathEntry& fvInfo = nested.cur();
            document::FieldValue::UP fv = sfv->getValue(fvInfo.getFieldRef());
            if (fv) {
                SubDocument tmp(fv.get(), nested.next());
                _cachedFields[fId].swap(tmp);
                _backedFields.push_back(std::move(fv));
            }
        } else {
          LOG(debug, "Failed getting field fId %d.", fId);
          return _empySubDocument;
        }
    }
    return _cachedFields[fId];
}

const document::FieldValue *
StorageDocument::getField(FieldIdT fId) const
{
    return getComplexField(fId).getFieldValue();
}

bool StorageDocument::setField(FieldIdT fId, document::FieldValue::UP fv)
{
    bool ok(fId < _cachedFields.size());
    if (ok) {
        const FieldPath & fp = (*_fieldMap)[fId];
        SubDocument tmp(fv.get(), NestedIterator(fp.end(), fp.end()));
        _cachedFields[fId].swap(tmp);
        _backedFields.emplace_back(std::move(fv));
    }
    return ok;
}

}
