// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "valueupdate.h"

namespace document {

/**
 * Represents an update that removes the field value from the document.
 *
 * Wrapped in a MapValueUpdate it removes the addressed array element or
 * weighted set entry instead.
 */
class ClearValueUpdate final : public ValueUpdate {
    ACCEPT_UPDATE_VISITOR;

public:
    ClearValueUpdate(const ClearValueUpdate& update) = delete;
    ClearValueUpdate& operator=(const ClearValueUpdate& rhs) = delete;
    ClearValueUpdate() : ValueUpdate(Clear) {}
    bool operator==(const ValueUpdate& other) const override;

    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream& buffer) override;
};

} // namespace document
