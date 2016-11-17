// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "multinumericattributesaver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include "multivalueattributesaverutils.h"

using vespalib::GenerationHandler;
using search::multivalueattributesaver::CountWriter;
using search::multivalueattributesaver::WeightWriter;

namespace search {

namespace
{

class DatWriter
{
    std::unique_ptr<search::BufferWriter> _datWriter;
public:
    DatWriter(IAttributeSaveTarget &saveTarget)
        : _datWriter(saveTarget.datWriter().allocBufferWriter())
    {
    }

    ~DatWriter()
    {
        _datWriter->flush();
    }

    template <typename MultiValueT>
    void
    writeValues(vespalib::ConstArrayRef<MultiValueT> values) {
        for (const MultiValueT &valueRef : values) {
            typename MultiValueT::ValueType value(valueRef);
            _datWriter->write(&value, sizeof(typename MultiValueT::ValueType));
        }
    }
};

}

template <typename MultiValueT, typename IndexT>
MultiValueNumericAttributeSaver<MultiValueT, IndexT>::
MultiValueNumericAttributeSaver(GenerationHandler::Guard &&guard,
                               const IAttributeSaveTarget::Config &cfg,
                               const MultiValueMapping &mvMapping)
    : Parent(std::move(guard), cfg, mvMapping),
      _mvMapping(mvMapping)
{
}



template <typename MultiValueT, typename IndexT>
MultiValueNumericAttributeSaver<MultiValueT, IndexT>::
~MultiValueNumericAttributeSaver()
{
}

template <typename MultiValueT, typename IndexT>
bool
MultiValueNumericAttributeSaver<MultiValueT, IndexT>::
onSave(IAttributeSaveTarget &saveTarget)
{
    CountWriter countWriter(saveTarget);
    WeightWriter<MultiValueType::_hasWeight> weightWriter(saveTarget);
    DatWriter datWriter(saveTarget);

    for (uint32_t docId = 0; docId < _frozenIndices.size(); ++docId) {
        Index idx = _frozenIndices[docId];
        vespalib::ConstArrayRef<MultiValueType> values(_mvMapping.getDataForIdx(idx));
        countWriter.writeCount(values.size());
        weightWriter.writeWeights(values);
        datWriter.writeValues(values);
    }
    return true;
}

template class MultiValueNumericAttributeSaver<multivalue::Value<int8_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int16_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int32_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int64_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::Value<float>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::Value<double>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int8_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int16_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int32_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int64_t>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<float>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<double>,
                                        multivalue::Index32>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int8_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int16_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int32_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::Value<int64_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::Value<float>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::Value<double>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int8_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int16_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int32_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int64_t>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<float>,
                                        multivalue::Index64>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<double>,
                                        multivalue::Index64>;

}  // namespace search
