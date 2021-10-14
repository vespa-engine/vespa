// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <algorithm>
#include <memory>
#include "dynamicdatavalue.h"

namespace vespalib
{

class SimpleDynamicData : public DynamicDataValue
{
private:
    struct DataUnit {
        uint32_t id;
        Value *v;
        DataUnit(uint32_t id_, Value *v_) : id(id_), v(v_) {}
    };
    typedef std::vector<DataUnit> DataUnits;
    DataUnits _values;

    virtual bool setValueIfExisting(uint32_t id, const Value& v) {
        for (DataUnits::iterator it = _values.begin(); it != _values.end(); ++it) {
            if (it->id == id) {
                delete it->v;
                it->v = v.clone();
                return true;
            }
        }
        return false;
    }
    virtual void addNewValue(uint32_t id, const Value& v) {
        _values.push_back(DataUnit(id, v.clone()));
    }

    void swap(SimpleDynamicData& other) {
        std::swap(_values, other._values);
    }

public:
    virtual ~SimpleDynamicData() {
        // we own contained data, must delete it
        for (DataUnits::iterator it = _values.begin(); it != _values.end(); ++it) {
            delete it->v;
        }
    }
    virtual bool hasValue(uint32_t id) const {
        for (DataUnits::const_iterator it = _values.begin(); it != _values.end(); ++it) {
            if (it->id == id) return true;
        }
        return false;
    }
    virtual void deleteValue(uint32_t id) {
        for (DataUnits::iterator it = _values.begin(); it != _values.end(); ++it) {
            if (it->id == id) {
                delete it->v;
                _values.erase(it);
                return;
            }
        }
    }
    virtual const Value& getValue(uint32_t id) const {
        for (DataUnits::const_iterator it = _values.begin(); it != _values.end(); ++it) {
            if (it->id == id) {
                return *(it->v);
            }
        }
        throw IllegalArgumentException("id not found");
    }
    virtual Value *getValueRef(uint32_t id) const {
        for (DataUnits::const_iterator it = _values.begin(); it != _values.end(); ++it) {
            if (it->id == id) {
                return it->v;
            }
        }
        return NULL;
    }
    virtual void visitValues(ValueReceiverI& visitor) const {
        for (DataUnits::const_iterator it = _values.begin(); it != _values.end(); ++it) {
            visitor(it->id, *(it->v));
        }
    }

    SimpleDynamicData() : _values() {}
    SimpleDynamicData(const SimpleDynamicData& other) :
        DynamicDataValue(),
        _values()
    {
        for (DataUnits::const_iterator it = other._values.begin();
             it != other._values.end();
             ++it)
        {
            addNewValue(it->id, *(it->v));
        }
    }

    SimpleDynamicData& operator= (const SimpleDynamicData& other) {
        SimpleDynamicData tmp(other);
        swap(tmp);
        return *this;
    }

    virtual SimpleDynamicData *clone() const {
        SimpleDynamicData *ret = new SimpleDynamicData(*this);
        return ret;
    }
};



} // namespace vespalib

