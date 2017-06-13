// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstorebase.h"

namespace search {

class IAttributeSaveTarget;

/*
 * Helper class for saving an enumerated multivalue attribute.
 *
 * It handles writing to the udat file.
 */
class EnumAttributeSaver
{
    const EnumStoreBase  &_enumStore;
    bool                  _disableReEnumerate;
    btree::BTreeNode::Ref _rootRef;

public:
    EnumAttributeSaver(const EnumStoreBase &enumStore, bool disableReEnumerate);

    ~EnumAttributeSaver();

    void enableReEnumerate();

    void writeUdat(IAttributeSaveTarget &saveTarget);

    const EnumStoreBase &getEnumStore() const { return _enumStore; }
};

} // namespace search
