// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dbdocumentid.h"

namespace proton {

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
