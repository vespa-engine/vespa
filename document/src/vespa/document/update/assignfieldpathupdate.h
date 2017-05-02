// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldpathupdate.h"
#include <vespa/document/base/documentcalculator.h>

namespace document {

class AssignFieldPathUpdate : public FieldPathUpdate
{
public:
    enum SerializationFlag
    {
        ARITHMETIC_EXPRESSION = 1,
        REMOVE_IF_ZERO = 2,
        CREATE_MISSING_PATH = 4
    };

    /** For deserialization */
    AssignFieldPathUpdate();

    AssignFieldPathUpdate(const DocumentTypeRepo& repo,
                          const DataType& type,
                          stringref fieldPath,
                          stringref whereClause,
                          const FieldValue& newValue);

    AssignFieldPathUpdate(const DocumentTypeRepo& repo,
                          const DataType& type,
                          stringref fieldPath,
                          stringref whereClause,
                          stringref expression);
    ~AssignFieldPathUpdate();

    void setRemoveIfZero(bool removeIfZero) {
        _removeIfZero = removeIfZero;
    }
    bool getRemoveIfZero() const { return _removeIfZero; }
    void setCreateMissingPath(bool createMissingPath) {
        _createMissingPath = createMissingPath;
    }
    bool getCreateMissingPath() const { return _createMissingPath; }
    const vespalib::string& getExpression() const { return _expression; }
    bool hasValue() const { return _newValue.get() != nullptr; }
    const FieldValue & getValue() const { return *_newValue; }

    FieldPathUpdate* clone() const override;

    bool operator==(const FieldPathUpdate& other) const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_IDENTIFIABLE(AssignFieldPathUpdate);
    ACCEPT_UPDATE_VISITOR;

private:
    uint8_t getSerializedType() const override { return AssignMagic; }
    void deserialize(const DocumentTypeRepo& repo, const DataType& type,
                     ByteBuffer& buffer, uint16_t version) override;

    class AssignValueIteratorHandler : public FieldValue::IteratorHandler
    {
    public:
        AssignValueIteratorHandler(const FieldValue& newValue,
                              bool removeIfZero,
                              bool createMissingPath_)
            : _newValue(newValue), _removeIfZero(removeIfZero),
            _createMissingPath(createMissingPath_)
        {}

        ModificationStatus doModify(FieldValue& fv) override;
        bool onComplex(const Content&) override { return false; }
        bool createMissingPath() const override { return _createMissingPath; }

    private:
        const FieldValue& _newValue;
        bool _removeIfZero;
        bool _createMissingPath;
    };

    class AssignExpressionIteratorHandler : public FieldValue::IteratorHandler
    {
    public:
        AssignExpressionIteratorHandler(
                const DocumentTypeRepo& repo,
                Document& doc,
                const vespalib::string& expression,
                bool removeIfZero,
                bool createMissingPath_)
            : _calc(repo, expression),
            _doc(doc),
            _removeIfZero(removeIfZero),
            _createMissingPath(createMissingPath_)
        {}

        ModificationStatus doModify(FieldValue& fv) override;
        bool onComplex(const Content&) override { return false; }
        bool createMissingPath() const override { return _createMissingPath; }

    private:
        DocumentCalculator _calc;
        Document& _doc;
        bool _removeIfZero;
        bool _createMissingPath;
    };

    std::unique_ptr<FieldValue::IteratorHandler> getIteratorHandler(Document& doc) const override;

    const DocumentTypeRepo *_repo;
    FieldValue::CP _newValue;
    vespalib::string _expression;
    bool _removeIfZero;
    bool _createMissingPath;
};

} // ns document
