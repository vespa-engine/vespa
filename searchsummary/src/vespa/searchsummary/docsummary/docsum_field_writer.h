// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

class IDocsumStoreDocument;
class GetDocsumsState;

/*
 * Abstract class for writing a field in a document summary.
 */
class DocsumFieldWriter
{
public:
    DocsumFieldWriter()
        : _index(0)
    {
    }
    virtual ~DocsumFieldWriter() = default;
    virtual bool isGenerated() const = 0;
    virtual void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state, vespalib::slime::Inserter &target) const = 0;
    virtual const vespalib::string & getAttributeName() const;
    virtual bool isDefaultValue(uint32_t docid, const GetDocsumsState& state) const;
    void setIndex(size_t v) { _index = v; }
    size_t getIndex() const { return _index; }
    virtual bool setFieldWriterStateIndex(uint32_t fieldWriterStateIndex);
private:
    size_t _index;
    static const vespalib::string _empty;
};

}
