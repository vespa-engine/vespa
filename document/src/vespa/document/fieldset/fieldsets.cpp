// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldsets.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <xxhash.h>

namespace document {

namespace {

uint64_t
computeHash(const FieldCollection::FieldList & list) {
    if (list.empty()) return 0ul;

    vespalib::asciistream os;
    for (const Field * field : list) {
        os << field->getName() << ':';
    }
    return XXH64(os.c_str(), os.size(), 0);
}

}

FieldCollection::FieldCollection(const DocumentType& type, FieldList list)
    : _fields(std::move(list)),
      _hash(0),
      _docType(type)
{
    std::sort(_fields.begin(), _fields.end(), Field::FieldPtrComparator());
    std::unique(_fields.begin(), _fields.end(), [](const Field *a, const Field *b) { return a->getId() == b->getId(); });
    _hash = computeHash(_fields);
}

FieldCollection::FieldCollection(const FieldCollection&) = default;

FieldCollection::~FieldCollection() = default;

bool
FieldCollection::contains(const FieldSet& fields) const
{
    switch (fields.getType()) {
        case Type::FIELD:
            return std::binary_search(_fields.begin(), _fields.end(),
                                      static_cast<const Field*>(&fields), Field::FieldPtrComparator());
        case Type::SET: {
            const auto & coll = static_cast<const FieldCollection&>(fields);

            if (_fields.size() < coll._fields.size()) return false;

            for (const Field * field : coll.getFields()) {
                if ( ! std::binary_search(_fields.begin(), _fields.end(),
                                          field, Field::FieldPtrComparator())) {
                    return false;
                }
            }

            return true;
        }
        case Type::NONE:
        case Type::DOCID:
            return true;
        case Type::ALL:
            return false;
    }

    return false;
}

void
FieldSet::copyFields(Document& dest, const Document& src, const FieldSet& fields)
{
    if (fields.getType() == Type::ALL) {
        dest.getFields() = src.getFields();
        return;
    }
    for (Document::const_iterator it(src.begin()), e(src.end());
         it != e; ++it)
    {
        const Field& f(it.field());
        if (!fields.contains(f)) {
            continue;
        }
        dest.setValue(f, *src.getValue(f));
    }
}

Document::UP
FieldSet::createDocumentSubsetCopy(const Document& src, const FieldSet& fields)
{
    auto ret = std::make_unique<Document>(src.getType(), src.getId());
    copyFields(*ret, src, fields);
    return ret;
}

void
FieldSet::stripFields(Document& doc, const FieldSet& fieldsToKeep)
{
    if (fieldsToKeep.getType() == Type::ALL) {
        return;
    } else if (fieldsToKeep.getType() == Type::DOCID
               || fieldsToKeep.getType() == Type::NONE)
    {
        doc.clear();
        return;
    }
    std::vector<const Field*> fieldsToRemove;
    for (Document::const_iterator it(doc.begin()), e(doc.end());
         it != e; ++it)
    {
        const Field& f(it.field());
        if (!fieldsToKeep.contains(f)) {
            fieldsToRemove.push_back(&f);
        }
    }
    for (const Field * field : fieldsToRemove) {
        doc.remove(*field);
    }
}

} // namespace document

