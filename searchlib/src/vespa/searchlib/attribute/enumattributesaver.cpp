// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumattributesaver.h"
#include "iattributesavetarget.h"
#include <vespa/searchlib/util/bufferwriter.h>

namespace search {

EnumAttributeSaver::
EnumAttributeSaver(const EnumStoreBase &enumStore, bool disableReEnumerate)
    : _enumStore(enumStore),
      _disableReEnumerate(disableReEnumerate),
      _rootRef()
{
    if (_disableReEnumerate) {
        // Prevent enum store from re-enumerating enum values during compaction
        _enumStore.disableReEnumerate();
    }
    const EnumStoreDictBase &enumDict = enumStore.getEnumStoreDict();
    _rootRef = enumDict.getFrozenRootRef();
}

EnumAttributeSaver::~EnumAttributeSaver()
{
    enableReEnumerate();
}

void
EnumAttributeSaver::enableReEnumerate()
{
    if (_disableReEnumerate) {
        // compaction of enumstore can now re-enumerate enum values
        _enumStore.enableReEnumerate();
        _disableReEnumerate = false;
    }
}

void
EnumAttributeSaver::writeUdat(IAttributeSaveTarget &saveTarget)
{
    if (saveTarget.getEnumerated()) {
        std::unique_ptr<BufferWriter>
            udatWriter(saveTarget.udatWriter().allocBufferWriter());
        const EnumStoreDictBase &enumDict = _enumStore.getEnumStoreDict();
        enumDict.writeAllValues(*udatWriter, _rootRef);
        udatWriter->flush();
    }
}

}  // namespace search
