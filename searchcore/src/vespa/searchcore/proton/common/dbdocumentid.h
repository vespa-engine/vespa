// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/query/base.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace proton {

/**
 * Class used to localize a local document id inside a sub document db.
 */
class DbDocumentId {
private:
    uint32_t            _subDbId; // sub document db id
    search::DocumentIdT _lid;     // local document id
public:
    DbDocumentId() noexcept: DbDocumentId(0) {}
    DbDocumentId(search::DocumentIdT lid) noexcept : DbDocumentId(0, lid) {}
    DbDocumentId(uint32_t subDbId, search::DocumentIdT lid) noexcept
        : _subDbId(subDbId),
          _lid(lid)
    { }
    uint32_t getSubDbId() const { return _subDbId; }
    search::DocumentIdT getLid() const { return _lid; }
    bool valid() const { return _lid != 0; }

    bool
    operator!=(const DbDocumentId &rhs) const
    {
        return _subDbId != rhs._subDbId ||
                   _lid != rhs._lid;
    }

    bool
    operator==(const DbDocumentId &rhs) const
    {
        return _subDbId == rhs._subDbId &&
                   _lid == rhs._lid;
    }

    vespalib::string toString() const {
        return vespalib::make_string("subDbId=%u, lid=%u", _subDbId, _lid);
    }

    friend vespalib::nbostream &
    operator<<(vespalib::nbostream &os, const DbDocumentId &dbdId);

    friend vespalib::nbostream &
    operator>>(vespalib::nbostream &is, DbDocumentId &dbdId);
};


} // namespace proton

