// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldpathupdate.h"

namespace document {

class ArrayFieldValue;

class AddFieldPathUpdate : public FieldPathUpdate
{
public:
    /** For deserialization */
    AddFieldPathUpdate();
    AddFieldPathUpdate(const DataType& type, stringref fieldPath,
                       stringref whereClause, const ArrayFieldValue& values);
    ~AddFieldPathUpdate();

    FieldPathUpdate* clone() const override;
    bool operator==(const FieldPathUpdate& other) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    const ArrayFieldValue & getValues() const { return *_values; }

    DECLARE_IDENTIFIABLE(AddFieldPathUpdate);
    ACCEPT_UPDATE_VISITOR;

private:
    uint8_t getSerializedType() const override { return AddMagic; }
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream) override;

    std::unique_ptr<fieldvalue::IteratorHandler> getIteratorHandler(Document &, const DocumentTypeRepo &) const override;

    vespalib::CloneablePtr<ArrayFieldValue> _values;
};

}
