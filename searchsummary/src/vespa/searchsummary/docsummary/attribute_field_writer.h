// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/memory.h>

namespace search::attribute { class IAttributeVector; }
namespace vespalib::slime { class Cursor; }

namespace search::docsummary {

/*
 * This class reads values from a struct field attribute and inserts
 * them into proper position in an array of struct or map of struct.
 * If the value to be inserted is considered to be undefined then
 * the value is not inserted.
 */
class AttributeFieldWriter
{
protected:
    const vespalib::Memory                     _fieldName;
    const search::attribute::IAttributeVector &_attr;
    size_t                                     _len;
public:
    AttributeFieldWriter(const vespalib::string &fieldName,
                         const search::attribute::IAttributeVector &attr);
    virtual ~AttributeFieldWriter();
    virtual void fetch(uint32_t docId) = 0;
    virtual void print(uint32_t idx, vespalib::slime::Cursor &cursor) = 0;
    static std::unique_ptr<AttributeFieldWriter> create(const vespalib::string &fieldName, const search::attribute::IAttributeVector &attr);
    uint32_t size() const { return _len; }
};

}
