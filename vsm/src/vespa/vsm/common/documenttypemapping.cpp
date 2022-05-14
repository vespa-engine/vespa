// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documenttypemapping.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.common.documenttypemapping");

namespace vsm {

DocumentTypeMapping::DocumentTypeMapping() :
    _fieldMap(),
    _defaultDocumentTypeName(),
    _defaultDocumentType(),
    _documentTypeFreq()
{ }

DocumentTypeMapping::~DocumentTypeMapping() { }

namespace {

vespalib::string getDocTypeId(const document::DocumentType & docType)
{
    vespalib::string typeId(docType.getName());
    typeId += "0";  // Hardcoded version (version not supported)
    return typeId;
}

}

void DocumentTypeMapping::init(const vespalib::string & defaultDocumentType,
                               const StringFieldIdTMapT & fieldList,
                               const document::DocumentTypeRepo &repo)
{
    _defaultDocumentType = repo.getDocumentType(defaultDocumentType);
    _defaultDocumentTypeName = getDocTypeId(*_defaultDocumentType);
    LOG(debug, "Setting default document type to '%s'",
        _defaultDocumentTypeName.c_str());
    buildFieldMap(_defaultDocumentType, fieldList, _defaultDocumentTypeName);
}

bool DocumentTypeMapping::prepareBaseDoc(SharedFieldPathMap & map) const
{
    FieldPathMapMapT::const_iterator found = _fieldMap.find(_defaultDocumentTypeName);
    if (found != _fieldMap.end()) {
        map = std::make_shared<FieldPathMapT>(found->second);
        LOG(debug, "Found FieldPathMap for default document type '%s' with %zd elements",
            _defaultDocumentTypeName.c_str(), map->size());
    } else {
        LOG(warning, "No FieldPathMap found for default document type '%s'. Using empty one",
            _defaultDocumentTypeName.c_str());
        map = std::make_shared<FieldPathMapT>();
    }
    return true;
}

void DocumentTypeMapping::buildFieldMap(
        const document::DocumentType *docTypePtr,
        const StringFieldIdTMapT & fieldList, const vespalib::string & typeId)
{
    LOG(debug, "buildFieldMap: docType = '%s', fieldList.size = '%zd', typeId = '%s'",
        docTypePtr->getName().c_str(), fieldList.size(), typeId.c_str());
    const document::DocumentType & docType = *docTypePtr;
    size_t highestFNo(0);
    for (StringFieldIdTMapT::const_iterator it = fieldList.begin(), mt = fieldList.end(); it != mt; it++) {
        highestFNo = std::max(highestFNo, size_t(it->second));
    }
    highestFNo++;
    FieldPathMapT & fieldMap = _fieldMap[typeId];

    fieldMap.resize(highestFNo);

    size_t validCount(0);
    for (StringFieldIdTMapT::const_iterator it = fieldList.begin(), mt = fieldList.end(); it != mt; it++) {
        vespalib::string fname = it->first;
        LOG(debug, "Handling %s -> %d", fname.c_str(), it->second);
        try {
            if ((it->first[0] != '[') && (it->first != "summaryfeatures") && (it->first != "rankfeatures") && (it->first != "ranklog") && (it->first != "sddocname") && (it->first != "documentid")) {
                FieldPath fieldPath;
                docType.buildFieldPath(fieldPath, fname);
                fieldMap[it->second] = std::move(fieldPath);
                validCount++;
                LOG(spam, "Found %s -> %d in document", fname.c_str(), it->second);
            }
        } catch (const std::exception & e) {
            LOG(debug, "Could not get field info for '%s' in documenttype '%s' (id = '%s') : %s",
                    it->first.c_str(), docType.getName().c_str(), typeId.c_str(), e.what());
        }
    }
    _documentTypeFreq.insert(std::make_pair(validCount, docTypePtr));
}

const document::DocumentType & DocumentTypeMapping::getCurrentDocumentType() const
{
    if (_documentTypeFreq.empty()) {
        throw std::runtime_error("No document type registered yet.");
    }
    return *_documentTypeFreq.rbegin()->second;
}


}
