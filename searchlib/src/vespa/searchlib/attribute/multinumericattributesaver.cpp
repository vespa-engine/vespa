// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multinumericattributesaver.h"
#include "multivalueattributesaverutils.h"
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/searchlib/util/bufferwriter.h>

using vespalib::GenerationHandler;
using search::multivalueattributesaver::CountWriter;
using search::multivalueattributesaver::WeightWriter;

namespace search {

namespace {

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
            using ValueType = multivalue::ValueType_t<MultiValueT>;
            ValueType value(valueRef);
            _datWriter->write(&value, sizeof(ValueType));
        }
    }
};

}

template <typename MultiValueT>
MultiValueNumericAttributeSaver<MultiValueT>::
MultiValueNumericAttributeSaver(GenerationHandler::Guard &&guard,
                                const attribute::AttributeHeader &header,
                                const MultiValueMapping &mvMapping)
    : Parent(std::move(guard), header, mvMapping),
      _mvMapping(mvMapping)
{
}



template <typename MultiValueT>
MultiValueNumericAttributeSaver<MultiValueT>::
~MultiValueNumericAttributeSaver()
{
}

template <typename MultiValueT>
bool
MultiValueNumericAttributeSaver<MultiValueT>::
onSave(IAttributeSaveTarget &saveTarget)
{
    CountWriter countWriter(saveTarget);
    WeightWriter<multivalue::is_WeightedValue_v<MultiValueType>> weightWriter(saveTarget);
    DatWriter datWriter(saveTarget);

    for (uint32_t docId = 0; docId < _frozenIndices.size(); ++docId) {
        vespalib::datastore::EntryRef idx = _frozenIndices[docId];
        vespalib::ConstArrayRef<MultiValueType> values(_mvMapping.getDataForIdx(idx));
        countWriter.writeCount(values.size());
        weightWriter.writeWeights(values);
        datWriter.writeValues(values);
    }
    return true;
}

template class MultiValueNumericAttributeSaver<int8_t>;
template class MultiValueNumericAttributeSaver<int16_t>;
template class MultiValueNumericAttributeSaver<int32_t>;
template class MultiValueNumericAttributeSaver<int64_t>;
template class MultiValueNumericAttributeSaver<float>;
template class MultiValueNumericAttributeSaver<double>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int8_t>>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int16_t>>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int32_t>>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<int64_t>>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<float>>;
template class MultiValueNumericAttributeSaver<multivalue::WeightedValue<double>>;

}  // namespace search
