// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/propertiesmap.h>

namespace search::docsummary {

class GetDocsumArgs
{
public:
    typedef engine::PropertiesMap PropsMap;

private:
    vespalib::string  _ranking;
    uint32_t          _qflags;
    vespalib::string  _resultClassName;
    uint32_t          _stackItems;
    std::vector<char> _stackDump;
    vespalib::string  _location;
    fastos::TimeStamp _timeout;
    PropsMap          _propertiesMap;
public:
    GetDocsumArgs();
    ~GetDocsumArgs();

    void initFromDocsumRequest(const search::engine::DocsumRequest &req);

    void SetRankProfile(const vespalib::string &ranking) { _ranking = ranking; }
    void SetQueryFlags(uint32_t qflags)         { _qflags = qflags; }
    void setResultClassName(vespalib::stringref name) { _resultClassName = name; }
    void SetStackDump(uint32_t stackItems, uint32_t stackDumpLen, const char *stackDump);
    void setLocation(vespalib::stringref location) {
        _location = location;
    }

    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::TimeStamp getTimeout() const;

    const vespalib::string & getResultClassName()      const { return _resultClassName; }
    const vespalib::string & getLocation()             const { return _location; }
    const vespalib::stringref getStackDump()           const {
        return vespalib::stringref(&_stackDump[0], _stackDump.size());
    }

    uint32_t GetQueryFlags()                           const { return _qflags;       }

    const PropsMap &propertiesMap() const { return _propertiesMap; }

    const search::fef::Properties &highlightTerms() const {
        return _propertiesMap.highlightTerms();
    }
};

}
