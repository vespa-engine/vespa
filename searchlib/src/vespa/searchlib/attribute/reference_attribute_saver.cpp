// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_attribute_saver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/array.hpp>


using vespalib::GenerationHandler;
using document::GlobalId;
using search::datastore::EntryRef;

namespace search {
namespace attribute {

ReferenceAttributeSaver::
ReferenceAttributeSaver(GenerationHandler::Guard &&guard,
                        const IAttributeSaveTarget::Config &cfg,
                        IndicesCopyVector &&indices,
                        const Store &store)
    : AttributeSaver(std::move(guard), cfg),
      _indices(std::move(indices)),
      _store(store),
      _saver(store.getSaver())
{
}


ReferenceAttributeSaver::~ReferenceAttributeSaver()
{
}

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
    void operator()(EntryRef ref) {
        const GlobalId &gid = _store.get(ref);
        _writer.write(&gid, sizeof(GlobalId));;
    }
};

template <class Store, class Saver>
void
writeUdat(IAttributeSaveTarget &saveTarget, const Store &store, const Saver &saver)
{
    std::unique_ptr<BufferWriter>
        udatWriter(saveTarget.udatWriter().allocBufferWriter());
    saver.foreach_key(ValueWriter<Store>(store, *udatWriter));
    udatWriter->flush();
}

}

bool
ReferenceAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    writeUdat(saveTarget, _store, _saver);
    std::unique_ptr<search::BufferWriter> datWriter(saveTarget.datWriter().
                                                    allocBufferWriter());

    _saver.enumerateValues();
    for (const auto &ref : _indices) {
        uint32_t enumValue = _saver.mapEntryRefToEnumValue(ref);
        datWriter->write(&enumValue, sizeof(uint32_t));
    }
    datWriter->flush();
    return true;
}

}  // namespace search::attribute
}  // namespace search
