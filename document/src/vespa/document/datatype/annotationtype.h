// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datatype.h"
#include <memory>
#include <vector>

namespace vespalib { class asciistream; }

namespace document {

class AnnotationType {
    int _id;
    vespalib::string _name;
    const DataType *_type;

public:
    using UP = std::unique_ptr<AnnotationType>;
    using SP = std::shared_ptr<AnnotationType>;

    AnnotationType(int id, vespalib::stringref name)
        : _id(id), _name(name), _type(0) {}
    void setDataType(const DataType &type) { _type = &type; }

    const vespalib::string & getName() const { return _name; }
    int getId() const { return _id; }
    const DataType *getDataType() const { return _type; }
    bool operator==(const AnnotationType &a2) const {
        return (getId() == a2.getId()) && (getName() == a2.getName());
    }
    bool operator!=(const AnnotationType &a2) const {
        return ! (*this == a2);
    }
    vespalib::string toString() const;

    static const AnnotationType *const TERM;
    static const AnnotationType *const TOKEN_TYPE;

    /** Used by type manager to fetch default types to register. */
    static std::vector<const AnnotationType *> getDefaultAnnotationTypes();
};

vespalib::asciistream & operator << (vespalib::asciistream & os, const AnnotationType & type);

}  // namespace document

