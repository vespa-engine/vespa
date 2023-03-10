// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumattributesaver.h"
#include "i_enum_store_dictionary.h"
#include "iattributesavetarget.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/datastore/unique_store_enumerator.hpp>

namespace search {

EnumAttributeSaver::EnumAttributeSaver(IEnumStore &enumStore)
    : _enumStore(enumStore),
      _enumerator(enumStore.make_enumerator())
{
}

EnumAttributeSaver::~EnumAttributeSaver() = default;

void
EnumAttributeSaver::writeUdat(IAttributeSaveTarget &saveTarget)
{
    if (saveTarget.getEnumerated()) {
        auto udatWriter = saveTarget.udatWriter().allocBufferWriter();
        _enumerator->foreach_key([&](const vespalib::datastore::AtomicEntryRef& idx){
            _enumStore.write_value(*udatWriter, idx.load_acquire());
        });
        udatWriter->flush();
    }
}

}  // namespace search

namespace vespalib::datastore {

template class UniqueStoreEnumerator<search::IEnumStore::InternalIndex>;

}
