// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include <vespa/vespalib/datastore/unique_store_enumerator.h>

namespace search {

class IAttributeSaveTarget;

/**
 * Helper class for saving an enumerated multivalue attribute.
 *
 * It handles writing to the udat file.
 */
class EnumAttributeSaver
{
public:
    using Enumerator = IEnumStore::Enumerator;

private:
    const IEnumStore  &_enumStore;
    std::unique_ptr<Enumerator> _enumerator;

public:
    EnumAttributeSaver(IEnumStore &enumStore);
    ~EnumAttributeSaver();

    void writeUdat(IAttributeSaveTarget &saveTarget);
    const IEnumStore &getEnumStore() const { return _enumStore; }
    Enumerator &get_enumerator() { return *_enumerator; }
    void clear() { _enumerator->clear(); }
};

} // namespace search
