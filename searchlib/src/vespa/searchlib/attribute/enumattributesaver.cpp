// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumattributesaver.h"
#include "iattributesavetarget.h"
#include <vespa/vespalib/util/bufferwriter.h>
#include <vespa/vespalib/datastore/unique_store_enumerator.hpp>

namespace search {

EnumAttributeSaver::
EnumAttributeSaver(const IEnumStore &enumStore)
    : _enumStore(enumStore),
      _enumerator(_enumStore.getEnumStoreDict(), _enumStore.get_data_store_base())
{
}

EnumAttributeSaver::~EnumAttributeSaver()
{
}

void
EnumAttributeSaver::writeUdat(IAttributeSaveTarget &saveTarget)
{
    if (saveTarget.getEnumerated()) {
        std::unique_ptr<BufferWriter>
            udatWriter(saveTarget.udatWriter().allocBufferWriter());
        const auto& enumDict = _enumStore.getEnumStoreDict();
        enumDict.writeAllValues(*udatWriter, _enumerator.get_frozen_root());
        udatWriter->flush();
    }
}

}  // namespace search

namespace search::datastore {

template class UniqueStoreEnumerator<IEnumStore::Index>;

}
