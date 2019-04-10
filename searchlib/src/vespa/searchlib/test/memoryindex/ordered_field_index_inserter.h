// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/i_ordered_field_index_inserter.h>
#include <sstream>

namespace search::memoryindex::test {

class OrderedFieldIndexInserter : public IOrderedFieldIndexInserter {
    std::stringstream _ss;
    bool _first;
    bool _verbose;
    uint32_t _fieldId;

    void
    addComma()
    {
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
          _fieldId(0)
    {
    }

    virtual void
    setNextWord(const vespalib::stringref word) override
    {
        addComma();
        _ss << "w=" << word;
    }

    void
    setFieldId(uint32_t fieldId)
    {
        _fieldId = fieldId;
    }

    virtual void
    add(uint32_t docId,
        const index::DocIdAndFeatures &features) override
    {
        (void) features;
        addComma();
        _ss << "a=" << docId;
        if (_verbose) {
            _ss << "(";
            auto wpi = features._wordPositions.begin();
            bool firstElement = true;
            for (auto &el : features._elements) {
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
                }
                _ss << "]";
            }
            _ss << ")";
        }
    }

    virtual void
    remove(uint32_t docId) override
    {
        addComma();
        _ss << "r=" << docId;
    }

    virtual void flush() override { }
    virtual void rewind() override {
        addComma();
        _ss << "f=" << _fieldId;
    }

    std::string
    toStr() const
    {
        return _ss.str();
    }

    void
    reset()
    {
        _ss.str("");
        _first = true;
        _verbose = false;
    }

    void setVerbose() { _verbose = true; }
};

}
