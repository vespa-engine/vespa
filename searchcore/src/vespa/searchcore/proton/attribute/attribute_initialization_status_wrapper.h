// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <shared_mutex>

namespace proton {

class AttributeInitializationStatusWrapper {
public:
    using SP = std::shared_ptr<AttributeInitializationStatusWrapper>;
    explicit AttributeInitializationStatusWrapper(const std::string &name);

    const std::string& getName() const { return _name; }

    void setAttributeVector(const search::AttributeVector::SP &attr);
    bool hasAttributeVector() const;

    const search::attribute::AttributeInitializationStatus& getInitializationStatus() const { return _attr->getInitializationStatus(); };
    search::attribute::AttributeInitializationStatus& getInitializationStatus() { return _attr->getInitializationStatus(); }

    void reportInitializationStatus(const vespalib::slime::Inserter &inserter) const;

private:
    mutable std::shared_mutex _mutex;

    const std::string _name;
    search::AttributeVector::SP _attr;
};

} // namespace proton
