// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "multienumattributesaver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include "multivalueattributesaverutils.h"

using vespalib::GenerationHandler;
using search::multivalueattributesaver::CountWriter;
using search::multivalueattributesaver::WeightWriter;

namespace search {

namespace
{

/*
 * Class to write enum indexes mapped over to either enum values
 * or values, depending on the requirements of the save target.
 */
class DatWriter
{
    std::vector<EnumStoreIndex>           _indexes;
    const EnumStoreBase                  &_enumStore;
    std::unique_ptr<search::BufferWriter> _datWriter;
    bool                                  _enumerated;
public:
    DatWriter(IAttributeSaveTarget &saveTarget,
              const EnumStoreBase &enumStore)
        : _indexes(),
          _enumStore(enumStore),
          _datWriter(saveTarget.datWriter().allocBufferWriter()),
          _enumerated(saveTarget.getEnumerated())
    {
        _indexes.reserve(1000);
    }

    ~DatWriter()
    {
        assert(_indexes.empty());
        _datWriter->flush();
    }

    void flush()
    {
        if (!_indexes.empty()) {
            if (_enumerated) {
                _enumStore.writeEnumValues(*_datWriter,
                                           &_indexes[0], _indexes.size());
            } else {
                _enumStore.writeValues(*_datWriter,
                                       &_indexes[0], _indexes.size());
            }
            _indexes.clear();
        }
    }

    template <typename MultiValueT>
    void
    writeValues(vespalib::ConstArrayRef<MultiValueT> values) {
        for (const MultiValueT &valueRef : values) {
            if (_indexes.size() >= _indexes.capacity()) {
                flush();
            }
            _indexes.push_back(valueRef.value());
        }
    }
};

}

template <typename MultiValueT, typename IndexT>
MultiValueEnumAttributeSaver<MultiValueT, IndexT>::
MultiValueEnumAttributeSaver(GenerationHandler::Guard &&guard,
                             const IAttributeSaveTarget::Config &cfg,
                             const MultiValueMapping &mvMapping,
                             const EnumStoreBase &enumStore)
    : Parent(std::move(guard), cfg, mvMapping),
      _mvMapping(mvMapping),
      _enumSaver(enumStore, true)
{
}



template <typename MultiValueT, typename IndexT>
MultiValueEnumAttributeSaver<MultiValueT, IndexT>::
~MultiValueEnumAttributeSaver()
{
}

template <typename MultiValueT, typename IndexT>
bool
MultiValueEnumAttributeSaver<MultiValueT, IndexT>::
onSave(IAttributeSaveTarget &saveTarget)
{
    CountWriter countWriter(saveTarget);
    WeightWriter<MultiValueType::_hasWeight> weightWriter(saveTarget);
    DatWriter datWriter(saveTarget, _enumSaver.getEnumStore());
    _enumSaver.writeUdat(saveTarget);
    for (uint32_t docId = 0; docId < _frozenIndices.size(); ++docId) {
        Index idx = _frozenIndices[docId];
        vespalib::ConstArrayRef<MultiValueType> handle(_mvMapping.getDataForIdx(idx));
        countWriter.writeCount(handle.size());
        weightWriter.writeWeights(handle);
        datWriter.writeValues(handle);
    }
    datWriter.flush();
    _enumSaver.enableReEnumerate();
    return true;
}

using EnumIdxArray = multivalue::Value<EnumStoreIndex>;
using EnumIdxWset = multivalue::WeightedValue<EnumStoreIndex>;

template class MultiValueEnumAttributeSaver<EnumIdxArray, multivalue::Index32>;
template class MultiValueEnumAttributeSaver<EnumIdxWset, multivalue::Index32>;
template class MultiValueEnumAttributeSaver<EnumIdxArray, multivalue::Index64>;
template class MultiValueEnumAttributeSaver<EnumIdxWset, multivalue::Index64>;

}  // namespace search
