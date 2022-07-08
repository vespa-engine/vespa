// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type_utils.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

class GeneralResult;
class GetDocsumsState;

/*
 * Abstract class for writing document summaries.
 */
class DocsumFieldWriter
{
public:
    DocsumFieldWriter()
        : _index(0)
    {
    }
    virtual ~DocsumFieldWriter() = default;
    static bool IsRuntimeCompatible(ResType a, ResType b) {
        return ResTypeUtils::IsRuntimeCompatible(a, b);
    }
    virtual bool IsGenerated() const = 0;
    virtual void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) = 0;
    virtual const vespalib::string & getAttributeName() const;
    virtual bool isDefaultValue(uint32_t docid, const GetDocsumsState * state) const;
    void setIndex(size_t v) { _index = v; }
    size_t getIndex() const { return _index; }
    virtual bool setFieldWriterStateIndex(uint32_t fieldWriterStateIndex);
private:
    size_t _index;
    static const vespalib::string _empty;
};

}
