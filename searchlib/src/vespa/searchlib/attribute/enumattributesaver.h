// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstorebase.h"
#include <vespa/vespalib/datastore/unique_store_enumerator.h>

namespace search {

class IAttributeSaveTarget;

/*
 * Helper class for saving an enumerated multivalue attribute.
 *
 * It handles writing to the udat file.
 */
class EnumAttributeSaver
{
public:
    using Enumerator = datastore::UniqueStoreEnumerator<EnumStoreIndex>;

private:
    const EnumStoreBase  &_enumStore;
    Enumerator _enumerator;

public:
    EnumAttributeSaver(const EnumStoreBase &enumStore);
    ~EnumAttributeSaver();

    void writeUdat(IAttributeSaveTarget &saveTarget);
    const EnumStoreBase &getEnumStore() const { return _enumStore; }
    Enumerator &get_enumerator() { return _enumerator; }
    void clear() { _enumerator.clear(); }
};

} // namespace search
