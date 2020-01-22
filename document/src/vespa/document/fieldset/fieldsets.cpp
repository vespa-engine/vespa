// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldsets.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>

namespace document {

bool
HeaderFields::contains(const FieldSet& fields) const
{
    switch (fields.getType()) {
    case FIELD:
        return static_cast<const Field&>(fields).isHeaderField();
    case SET:
    {
        const FieldCollection& coll = static_cast<const FieldCollection&>(fields);
        for (Field::Set::const_iterator iter = coll.getFields().begin();
             iter != coll.getFields().end();
             ++iter) {
            if (!(*iter)->isHeaderField()) {
                return false;
            }
        }

        return true;
    }
    case NONE:
    case DOCID:
    case HEADER:
        return true;
    case BODY:
    case ALL:
        return false;
    }

    return false;
}

bool
BodyFields::contains(const FieldSet& fields) const
{
    switch (fields.getType()) {
    case FIELD:
        return !static_cast<const Field&>(fields).isHeaderField();
    case SET:
    {
        const FieldCollection& coll = static_cast<const FieldCollection&>(fields);
        for (Field::Set::const_iterator iter = coll.getFields().begin();
             iter != coll.getFields().end();
             ++iter) {
            if ((*iter)->isHeaderField()) {
                return false;
            }
        }

        return true;
    }
    case NONE:
    case DOCID:
    case BODY:
        return true;
    case HEADER:
    case ALL:
        return false;
    }

    return false;
}

FieldCollection::FieldCollection(const DocumentType& type,
                                 const Field::Set& s)
    : _set(s),
      _docType(type)
{
}

bool
FieldCollection::contains(const FieldSet& fields) const
{
    switch (fields.getType()) {
    case FIELD:
        return _set.find(static_cast<const Field*>(&fields)) != _set.end();
    case SET:
    {
        const FieldCollection& coll = static_cast<const FieldCollection&>(fields);

        if (_set.size() < coll._set.size()) {
            return false;
        }

        Field::Set::const_iterator iter = coll.getFields().begin();

        while (iter != coll.getFields().end()) {
            if (_set.find(*iter) == _set.end()) {
                return false;
            }

            ++iter;
        }

        return true;
    }
    case NONE:
    case DOCID:
        return true;
    case BODY:
    case HEADER:
    case ALL:
        return false;
    }

    return false;
}

void
FieldCollection::insert(const Field& f)
{
    _set.insert(&f);
}

void
FieldCollection::insert(const Field::Set& c)
{
    _set.insert(c.begin(), c.end());
}

void
FieldSet::copyFields(Document& dest,
                     const Document& src,
                     const FieldSet& fields)
{
    if (fields.getType() == ALL) {
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
FieldSet::createDocumentSubsetCopy(const Document& src,
                                   const FieldSet& fields)
{
    auto ret = std::make_unique<Document>(src.getType(), src.getId());
    copyFields(*ret, src, fields);
    return ret;
}

void
FieldSet::stripFields(Document& doc,
                      const FieldSet& fieldsToKeep)
{
    if (fieldsToKeep.getType() == ALL) {
        return;
    } else if (fieldsToKeep.getType() == DOCID
               || fieldsToKeep.getType() == NONE)
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
    for (size_t i = 0; i < fieldsToRemove.size(); ++i) {
        doc.remove(*fieldsToRemove[i]);
    }
}

} // namespace document

