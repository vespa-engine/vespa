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
    vespalib::string   _ranking;
    vespalib::string   _resultClassName;
    bool               _dumpFeatures;
    bool               _no_locations;
    uint32_t           _stackItems;
    std::vector<char>  _stackDump;
    vespalib::string   _location;
    vespalib::duration _timeout;
    PropsMap           _propertiesMap;
public:
    GetDocsumArgs();
    ~GetDocsumArgs();

    void initFromDocsumRequest(const search::engine::DocsumRequest &req);

    void SetRankProfile(const vespalib::string &ranking) { _ranking = ranking; }
    void setResultClassName(vespalib::stringref name) { _resultClassName = name; }
    void SetStackDump(uint32_t stackItems, uint32_t stackDumpLen, const char *stackDump);
    void no_locations(bool value) { _no_locations = value; }
    bool no_locations() const { return _no_locations; }
    const vespalib::string &getLocation() const { return _location; }
    void setLocation(const vespalib::string & location) { _location = location; }
    void setTimeout(vespalib::duration timeout) { _timeout = timeout; }
    vespalib::duration getTimeout() const { return _timeout; }

    const vespalib::string & getResultClassName()      const { return _resultClassName; }
    const vespalib::stringref getStackDump()           const {
        return vespalib::stringref(&_stackDump[0], _stackDump.size());
    }

    void dumpFeatures(bool v) { _dumpFeatures = v; }
    bool dumpFeatures() const { return _dumpFeatures; }

    const PropsMap &propertiesMap() const { return _propertiesMap; }

    const search::fef::Properties &highlightTerms() const {
        return _propertiesMap.highlightTerms();
    }
};

}
