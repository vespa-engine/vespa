// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "urlresult.h"
#include "resultconfig.h"
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/data/slime/inserter.h>

namespace search { class IAttributeManager; }

namespace search::docsummary {

class GetDocsumsState;

class IDocsumFieldWriter
{
public:
    typedef std::unique_ptr<IDocsumFieldWriter> UP;
    IDocsumFieldWriter() : _index(0) { }
    virtual ~IDocsumFieldWriter() {}

    static bool IsBinaryCompatible(ResType a, ResType b)
    { return ResultConfig::IsBinaryCompatible(a, b); }

    static bool IsRuntimeCompatible(ResType a, ResType b)
    { return ResultConfig::IsRuntimeCompatible(a, b); }

    virtual bool IsGenerated() const = 0;
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) = 0;
    virtual const vespalib::string & getAttributeName() const { return _empty; }
    virtual bool isDefaultValue(uint32_t docid, const GetDocsumsState * state) const {
        (void) docid;
        (void) state;
        return false;
    }
    void setIndex(size_t v) { _index = v; }
    size_t getIndex() const { return _index; }
    virtual bool setFieldWriterStateIndex(uint32_t fieldWriterStateIndex);
private:
    size_t _index;
    static const vespalib::string _empty;
};

//--------------------------------------------------------------------------

class EmptyDFW : public IDocsumFieldWriter
{
public:
    EmptyDFW();
    virtual ~EmptyDFW();

    virtual bool IsGenerated() const override { return true; }
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) override;
};

//--------------------------------------------------------------------------

class CopyDFW : public IDocsumFieldWriter
{
private:
    uint32_t _inputFieldEnumValue;

public:
    CopyDFW();
    virtual ~CopyDFW();

    bool Init(const ResultConfig & config, const char *inputField);

    virtual bool IsGenerated() const override { return false; }
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) override;
};

//--------------------------------------------------------------------------

class AttributeDFWFactory
{
private:
    AttributeDFWFactory();
public:
    static IDocsumFieldWriter *create(IAttributeManager & vecMan, const char *vecName);
};

}
