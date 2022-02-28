// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldsets.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <xxhash.h>

namespace document {

namespace {

uint64_t
computeHash(const Field::Set & set) {
    if (set.empty()) return 0ul;

    vespalib::asciistream os;
    for (const Field * field : set) {
        os << field->getName() << ':';
    }
    return XXH64(os.c_str(), os.size(), 0);
}

}

FieldCollection::FieldCollection(const DocumentType& type, Field::Set set)
    : _set(std::move(set)),
      _hash(computeHash(_set)),
      _docType(&type)
{ }

FieldCollection::FieldCollection(const FieldCollection&) = default;

FieldCollection::~FieldCollection() = default;

bool
FieldCollection::contains(const FieldSet& fields) const
{
    switch (fields.getType()) {
        case Type::FIELD:
            return _set.contains(static_cast<const Field &>(fields));
        case Type::SET: {
            const auto & coll = static_cast<const FieldCollection&>(fields);
            return _set.contains(coll.getFields());
        }
        case Type::NONE:
        case Type::DOCID:
            return true;
        case Type::DOCUMENT_ONLY:
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
    } else if (fields.getType() == Type::DOCUMENT_ONLY) {
        const auto * actual = src.getType().getFieldSet(DocumentOnly::NAME);
        if (actual != nullptr) {
            copyFields(dest, src, actual->asCollection());
        }
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
    } else if (fieldsToKeep.getType() == Type::DOCUMENT_ONLY) {
        const auto * actual = doc.getType().getFieldSet(DocumentOnly::NAME);
        if (actual != nullptr) {
            return stripFields(doc, actual->asCollection());
        } else {
            // XXX - should not happen
            doc.clear();
            return;
        }
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

