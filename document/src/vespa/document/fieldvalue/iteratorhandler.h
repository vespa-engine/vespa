// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "variablemap.h"
#include "modificationstatus.h"
#include <vector>

namespace document::fieldvalue {

class IteratorHandler {
public:
    class CollectionScope {
    public:
        CollectionScope(IteratorHandler &handler, const FieldValue &value)
                : _handler(handler), _value(value) {
            _handler.handleCollectionStart(_value);
        }

        ~CollectionScope() {
            _handler.handleCollectionEnd(_value);
        }

    private:
        IteratorHandler &_handler;
        const FieldValue &_value;
    };

    class StructScope {
    public:
        StructScope(IteratorHandler &handler, const FieldValue &value)
                : _handler(handler), _value(value) {
            _handler.handleStructStart(_value);
        }

        ~StructScope() {
            _handler.handleStructEnd(_value);
        }

    private:
        IteratorHandler &_handler;
        const FieldValue &_value;
    };

protected:
    class Content {
    public:
        Content(const FieldValue &fv, int weight = 1) : _fieldValue(fv), _weight(weight) {}

        int getWeight() const { return _weight; }

        const FieldValue &getValue() const { return _fieldValue; }

    private:
        const FieldValue &_fieldValue;
        int _weight;
    };

    IteratorHandler();

public:
    virtual ~IteratorHandler();
    void handlePrimitive(uint32_t fid, const FieldValue &fv);

    /**
       Handles a complex type (struct/array/map etc) that is at the end of the
       field path.
       @return Return true if you want to recurse into the members.
    */
    bool handleComplex(const FieldValue &fv);
    void handleCollectionStart(const FieldValue &fv);
    void handleCollectionEnd(const FieldValue &fv);
    void handleStructStart(const FieldValue &fv);
    void handleStructEnd(const FieldValue &fv);
    void setWeight(int weight) { _weight = weight; }
    uint32_t getArrayIndex() const { return _arrayIndexStack.back(); }
    void setArrayIndex(uint32_t index) { _arrayIndexStack.back() = index; }
    ModificationStatus modify(FieldValue &fv) { return doModify(fv); }
    fieldvalue::VariableMap &getVariables() { return _variables; }
    void setVariables(const fieldvalue::VariableMap &vars) { _variables = vars; }
    virtual bool createMissingPath() const { return false; }
private:
    virtual bool onComplex(const Content &fv) {
        (void) fv;
        return true;
    }

    virtual void onPrimitive(uint32_t fid, const Content &fv);
    virtual void onCollectionStart(const Content &fv) { (void) fv; }
    virtual void onCollectionEnd(const Content &fv) { (void) fv; }
    virtual void onStructStart(const Content &fv) { (void) fv; }
    virtual void onStructEnd(const Content &fv) { (void) fv; }
    virtual ModificationStatus doModify(FieldValue &) { return ModificationStatus::NOT_MODIFIED; };

    // Scratchpad to store pass on weight.
    int getWeight() const { return _weight; }

    int _weight;
    std::vector<uint32_t>   _arrayIndexStack;
    fieldvalue::VariableMap _variables;
};

}
