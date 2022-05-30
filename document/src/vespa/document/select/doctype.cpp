// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doctype.h"
#include "visitor.h"

#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>
#include <ostream>

namespace document::select {

namespace {
    bool documentTypeEqualsName(const DocumentType& type,
                                vespalib::stringref name)
    {
        return (type.getName() == name);
    }
}

DocType::DocType(vespalib::stringref doctype)
    : Node("DocType"),
      _doctype(doctype)
{
}

ResultList
DocType::contains(const Context &context) const
{
    if (context._doc != NULL) {
        const Document &doc = *context._doc;
        return
            ResultList(Result::get(
                documentTypeEqualsName(doc.getType(),
                                       _doctype)));
    }
    if (context._docId != NULL) {
        return ResultList(Result::get((context._docId->getDocType() == _doctype)));
    }
    const DocumentUpdate &upd(*context._docUpdate);
    return ResultList(Result::get(
                documentTypeEqualsName(upd.getType(), _doctype)));
}

ResultList
DocType::trace(const Context& context, std::ostream& out) const
{
    ResultList result = contains(context);
    if (context._doc != NULL) {
        const Document &doc = *context._doc;
        out << "DocType - Doc is type " << doc.getType()
            << ", wanted " << _doctype << ", returning "
            << result << ".\n";
    } else if (context._docId != NULL) {
        out << "DocType - Doc is type (document id -- unknown type)"
            << ", wanted " << _doctype << ", returning "
            << result << ".\n";
    } else {
        const DocumentUpdate &update(*context._docUpdate);
        out << "DocType - Doc is type " << update.getType()
            << ", wanted " << _doctype << ", returning "
            << result << ".\n";
    }
    return result;
}


void
DocType::visit(Visitor &v) const
{
    v.visitDocumentType(*this);
}


void
DocType::print(std::ostream& out, bool verbose,
                const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (_parentheses) out << '(';
    out << _doctype;
    if (_parentheses) out << ')';
}

}
