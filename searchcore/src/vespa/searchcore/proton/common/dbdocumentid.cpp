// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dbdocumentid.h"

namespace proton {

DbDocumentId::DbDocumentId()
    : _subDbId(0),
      _lid(0)
{
}


DbDocumentId::DbDocumentId(search::DocumentIdT lid)
    : _subDbId(0),
      _lid(lid)
{
}


DbDocumentId::DbDocumentId(uint32_t subDbId, search::DocumentIdT lid)
    : _subDbId(subDbId),
      _lid(lid)
{
}


vespalib::nbostream &
operator<<(vespalib::nbostream &os, const DbDocumentId &dbdId)
{
    os << dbdId._subDbId;
    os << dbdId._lid;
    return os;
}


vespalib::nbostream &
operator>>(vespalib::nbostream &is, DbDocumentId &dbdId)
{
    is >> dbdId._subDbId;
    is >> dbdId._lid;
    return is;
}

} // namespace proton
