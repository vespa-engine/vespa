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
{ }

StorageDocument::~StorageDocument() { }

namespace {
    FieldPath _emptyFieldPath;
    StorageDocument::SubDocument _empySubDocument(NULL, _emptyFieldPath.getFullRange());
}

const StorageDocument::SubDocument &
StorageDocument::getComplexField(FieldIdT fId) const
{
    if (_cachedFields[fId].getFieldValue() == NULL) {
        const FieldPath & fp = (*_fieldMap)[fId];
        if ( ! fp.empty() ) {
            const document::StructuredFieldValue * sfv = _doc.get();
            NestedIterator nested = fp.getFullRange();
            const document::FieldPathEntry& fvInfo = nested.cur();
            bool ok = sfv->getValue(fvInfo.getFieldRef(), fvInfo.getFieldValueToSet());
            if (ok) {
                SubDocument tmp(&fvInfo.getFieldValueToSet(), nested.next());
                _cachedFields[fId].swap(tmp);
            }
        } else {
          LOG(debug, "Failed getting field fId %d.", fId);
          return _empySubDocument;
        }
    }
    return _cachedFields[fId];
}

void StorageDocument::saveCachedFields() const
{
    size_t m(_cachedFields.size());
    _backedFields.reserve(m);
    for (size_t i(0); i < m; i++) {
        if (_cachedFields[i].getFieldValue() != 0) {
            _backedFields.emplace_back(document::FieldValue::UP(_cachedFields[i].getFieldValue()->clone()));
            _cachedFields[i].setFieldValue(_backedFields.back().get());
        }
    }
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
