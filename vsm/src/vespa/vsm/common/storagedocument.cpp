// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/log/log.h>
LOG_SETUP(".vsm.storagedocument");

#define DEBUGMASK 0x00

namespace vsm
{

StorageDocument::StorageDocument(const SharedFieldPathMap & fim) :
    Document(),
    _doc(new document::Document()),
    _fieldMap(fim),
    _cachedFields(),
    _backedFields()
{
}

StorageDocument::StorageDocument(document::Document::UP doc) :
    Document(),
    _doc(doc.release()),
    _fieldMap(),
    _cachedFields(),
    _backedFields()
{
}

StorageDocument::~StorageDocument()
{
}

void StorageDocument::init()
{
    _cachedFields.clear();
    _cachedFields.resize(getFieldCount());
}

namespace {
    FieldPath _emptyFieldPath;
    StorageDocument::SubDocument _empySubDocument(NULL, _emptyFieldPath.begin(), _emptyFieldPath.end());
}

void StorageDocument::SubDocument::swap(SubDocument & rhs)
{
    std::swap(_fieldValue, rhs._fieldValue);
    std::swap(_it, rhs._it);
    std::swap(_mt, rhs._mt);
}


const StorageDocument::SubDocument & StorageDocument::getComplexField(FieldIdT fId) const
{
    if (_cachedFields[fId].getFieldValue() == NULL) {
        const FieldPath & fp = (*_fieldMap)[fId];
        if ( ! fp.empty() ) {
            const document::StructuredFieldValue * sfv = _doc.get();
            FieldPath::const_iterator it = fp.begin();
            FieldPath::const_iterator mt = fp.end();
            const document::FieldPathEntry& fvInfo = *it;
            bool ok = sfv->getValue(fvInfo.getFieldRef(), fvInfo.getFieldValueToSet());
            if (ok) {
                SubDocument tmp(&fvInfo.getFieldValueToSet(), it + 1, mt);
                _cachedFields[fId].swap(tmp);
            }
        } else {
          LOG(debug, "Failed getting field fId %d.", fId);
          return _empySubDocument;
        }
    }
    return _cachedFields[fId];
}

void StorageDocument::saveCachedFields()
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

const document::FieldValue * StorageDocument::getField(FieldIdT fId) const
{
    return getComplexField(fId).getFieldValue();
}

bool StorageDocument::setField(FieldIdT fId, document::FieldValue::UP fv)
{
    bool ok(fId < _cachedFields.size());
    if (ok) {
        const FieldPath & fp = (*_fieldMap)[fId];
        SubDocument tmp(fv.get(), fp.end(), fp.end());
        _cachedFields[fId].swap(tmp);
        _backedFields.emplace_back(std::move(fv));
    }
    return ok;
}

}
