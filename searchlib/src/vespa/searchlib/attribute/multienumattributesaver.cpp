// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multienumattributesaver.h"
#include "multivalueattributesaverutils.h"
#include <vespa/searchcommon/attribute/multivalue.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.multi_enum_attribute_saver");

using vespalib::GenerationHandler;
using search::multivalueattributesaver::CountWriter;
using search::multivalueattributesaver::WeightWriter;

namespace search {

namespace {

/*
 * Class to write enum indexes mapped over to either enum values
 * or values, depending on the requirements of the save target.
 */
class DatWriter
{
    std::vector<IEnumStore::Index>        _indexes;
    const EnumAttributeSaver::Enumerator &_enumerator;
    std::unique_ptr<search::BufferWriter> _datWriter;
    std::function<bool()>                 _compaction_interferred;

public:
    DatWriter(IAttributeSaveTarget &saveTarget,
              const EnumAttributeSaver::Enumerator &enumerator,
              std::function<bool()> compaction_interferred)
        : _indexes(),
          _enumerator(enumerator),
          _datWriter(saveTarget.datWriter().allocBufferWriter()),
          _compaction_interferred(compaction_interferred)
    {
        assert(saveTarget.getEnumerated());
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
            for (auto ref : _indexes) {
                uint32_t enumValue = _enumerator.map_entry_ref_to_enum_value_or_zero(ref);
                assert(enumValue != 0u || _compaction_interferred());
                // Enumerator enumerates known entry refs (based on
                // dictionary tree) to values >= 1, but file format
                // starts enumeration at 0.
                --enumValue;
                _datWriter->write(&enumValue, sizeof(uint32_t));
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
            _indexes.push_back(multivalue::get_value_ref(valueRef).load_acquire());
        }
    }
};

}

template <typename MultiValueT>
MultiValueEnumAttributeSaver<MultiValueT>::
MultiValueEnumAttributeSaver(GenerationHandler::Guard &&guard,
                             const attribute::AttributeHeader &header,
                             const MultiValueMapping &mvMapping,
                             const IEnumStore &enumStore)
    : Parent(std::move(guard), header, mvMapping),
      _mvMapping(mvMapping),
      _enumSaver(enumStore),
      _enum_store(enumStore),
      _compaction_count(enumStore.get_compaction_count())
{
}


template <typename MultiValueT>
bool
MultiValueEnumAttributeSaver<MultiValueT>::compaction_interferred() const
{
    return _compaction_count != _enum_store.get_compaction_count();
}

template <typename MultiValueT>
MultiValueEnumAttributeSaver<MultiValueT>::~MultiValueEnumAttributeSaver() = default;

template <typename MultiValueT>
bool
MultiValueEnumAttributeSaver<MultiValueT>::
onSave(IAttributeSaveTarget &saveTarget)
{
    bool compaction_broke_save = false;
    CountWriter countWriter(saveTarget);
    WeightWriter<multivalue::is_WeightedValue_v<MultiValueType>> weightWriter(saveTarget);
    DatWriter datWriter(saveTarget, _enumSaver.get_enumerator(),
                        [this]() { return compaction_interferred(); });
    _enumSaver.writeUdat(saveTarget);
    _enumSaver.get_enumerator().enumerateValues();
    for (uint32_t docId = 0; docId < _frozenIndices.size(); ++docId) {
        vespalib::datastore::EntryRef idx = _frozenIndices[docId];
        vespalib::ConstArrayRef<MultiValueType> handle(_mvMapping.getDataForIdx(idx));
        countWriter.writeCount(handle.size());
        weightWriter.writeWeights(handle);
        datWriter.writeValues(handle);
        if (((docId % 0x1000) == 0) && compaction_interferred()) {
            compaction_broke_save = true;
            break;
        }
    }
    datWriter.flush();
    _enumSaver.clear();
    if (compaction_interferred()) {
        compaction_broke_save = true;
    }
    if (compaction_broke_save) {
        LOG(warning, "Aborted save of attribute vector to '%s' due to compaction of unique values", get_file_name().c_str());
    }
    return !compaction_broke_save;
}

using EnumIdxArray = vespalib::datastore::AtomicEntryRef;
using EnumIdxWset = multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

template class MultiValueEnumAttributeSaver<EnumIdxArray>;
template class MultiValueEnumAttributeSaver<EnumIdxWset>;

}  // namespace search
