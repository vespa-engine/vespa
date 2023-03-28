// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_attribute_saver.h"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include "iattributesavetarget.h"


using vespalib::GenerationHandler;
using document::GlobalId;
using vespalib::datastore::AtomicEntryRef;
using vespalib::datastore::EntryRef;

namespace search::attribute {

ReferenceAttributeSaver::ReferenceAttributeSaver(GenerationHandler::Guard &&guard,
                                                 const AttributeHeader &header,
                                                 EntryRefVector&& indices,
                                                 Store &store)
    : AttributeSaver(std::move(guard), header),
      _indices(std::move(indices)),
      _store(store),
      _enumerator(store.getEnumerator(true))
{
}


ReferenceAttributeSaver::~ReferenceAttributeSaver() = default;

namespace {

template <class Store>
class ValueWriter
{
    const Store &_store;
    BufferWriter &_writer;
public:
    ValueWriter(const Store &store, BufferWriter &writer)
        : _store(store),
          _writer(writer)
    {
    }
    void operator()(const AtomicEntryRef& ref) {
        const GlobalId &gid = _store.get(ref.load_acquire()).gid();
        _writer.write(&gid, sizeof(GlobalId));;
    }
};

template <class Store, class Enumerator>
void
writeUdat(IAttributeSaveTarget &saveTarget, const Store &store, const Enumerator &enumerator)
{
    std::unique_ptr<BufferWriter>
        udatWriter(saveTarget.udatWriter().allocBufferWriter());
    enumerator.foreach_key(ValueWriter<Store>(store, *udatWriter));
    udatWriter->flush();
}

}

bool
ReferenceAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    writeUdat(saveTarget, _store, _enumerator);
    std::unique_ptr<search::BufferWriter> datWriter(saveTarget.datWriter().
                                                    allocBufferWriter());

    _enumerator.enumerateValues();
    for (const auto &ref : _indices) {
        uint32_t enumValue = _enumerator.mapEntryRefToEnumValue(ref);
        datWriter->write(&enumValue, sizeof(uint32_t));
    }
    datWriter->flush();
    return true;
}

}
