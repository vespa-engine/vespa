// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/i_ordered_field_index_inserter.h>
#include <sstream>

namespace search::memoryindex::test {

class OrderedFieldIndexInserter : public IOrderedFieldIndexInserter {
    std::stringstream _ss;
    bool _first;
    bool _verbose;
    bool _show_interleaved_features;
    uint32_t _fieldId;

    void addComma() {
        if (!_first) {
            _ss << ",";
        } else {
            _first = false;
        }
    }

public:
    OrderedFieldIndexInserter()
        : _ss(),
          _first(true),
          _verbose(false),
          _show_interleaved_features(false),
          _fieldId(0)
    {
    }

    virtual void setNextWord(const vespalib::stringref word) override {
        addComma();
        _ss << "w=" << word;
    }

    void setFieldId(uint32_t fieldId) {
        _fieldId = fieldId;
    }

    virtual void add(uint32_t docId,
                     const index::DocIdAndFeatures &features) override {
        (void) features;
        addComma();
        _ss << "a=" << docId;
        if (_verbose) {
            _ss << "(";
            auto wpi = features.word_positions().begin();
            bool firstElement = true;
            if (_show_interleaved_features) {
                _ss << "fl=" << features.field_length() <<
                    ",occs=" << features.num_occs();
                firstElement = false;
            }
            for (auto &el : features.elements()) {
                if (!firstElement) {
                    _ss << ",";
                }
                firstElement = false;
                _ss << "e=" << el.getElementId() << ",w=" <<
                    el.getWeight() <<  ",l=" <<
                    el.getElementLen() << "[";
                bool firstWordPos = true;
                for (uint32_t i = 0; i < el.getNumOccs(); ++i) {
                    if (!firstWordPos) {
                        _ss << ",";
                    }
                    firstWordPos = false;
                    _ss << wpi->getWordPos();
                    ++wpi;
                }
                _ss << "]";
            }
            _ss << ")";
        }
    }

    virtual vespalib::datastore::EntryRef getWordRef() const override { return vespalib::datastore::EntryRef(); }

    virtual void remove(uint32_t docId) override {
        addComma();
        _ss << "r=" << docId;
    }

    virtual void flush() override { }
    virtual void commit() override { }
    virtual void rewind() override {
        addComma();
        _ss << "f=" << _fieldId;
    }

    std::string toStr() const {
        return _ss.str();
    }

    void reset() {
        _ss.str("");
        _first = true;
        _verbose = false;
    }

    void setVerbose() { _verbose = true; }
    void set_show_interleaved_features() { _show_interleaved_features = true; }
};

}
