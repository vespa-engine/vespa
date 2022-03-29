// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::ClearValueUpdate
 * @ingroup document
 *
 * @brief Represents an update that clears the content of a field value.
 */
#pragma once

#include "valueupdate.h"

namespace document {

class ClearValueUpdate final : public ValueUpdate {
    ACCEPT_UPDATE_VISITOR;
public:
    ClearValueUpdate(const ClearValueUpdate& update) = delete;
    ClearValueUpdate &operator=(const ClearValueUpdate &rhs) = delete;
    ClearValueUpdate() : ValueUpdate(Clear) {}
    bool operator==(const ValueUpdate& other) const override;

    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream& buffer) override;
};

}

