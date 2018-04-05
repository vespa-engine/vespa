// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixedtyperepo.h"
#include "documenttyperepo.h"
#include <cassert>

namespace document {

FixedTypeRepo::FixedTypeRepo(const DocumentTypeRepo &repo)
    : _repo(&repo), _doc_type(repo.getDocumentType(DataType::T_DOCUMENT))
{
}

FixedTypeRepo::FixedTypeRepo(const DocumentTypeRepo &repo,
                             const vespalib::string &type)
    : _repo(&repo), _doc_type(repo.getDocumentType(type)) {
    assert(_doc_type);
}

const DataType *
FixedTypeRepo::getDataType(int32_t id) const
{
    return _repo->getDataType(*_doc_type, id);
}

const DataType *
FixedTypeRepo::getDataType(const vespalib::string &name) const
{
    return _repo->getDataType(*_doc_type, name);
}

const AnnotationType *
FixedTypeRepo::getAnnotationType(int32_t id) const
{
    return _repo->getAnnotationType(*_doc_type, id);
}

}  // namespace document
