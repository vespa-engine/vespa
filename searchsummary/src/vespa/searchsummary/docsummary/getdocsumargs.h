// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Overture Services Norway AS
// Copyright (C) 1999-2003 Fast Search & Transfer ASA

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/propertiesmap.h>

namespace search {
namespace docsummary {

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
    uint32_t          _flags;
    PropsMap          _propertiesMap;

    bool _isLocationSet;

public:
    GetDocsumArgs();
    ~GetDocsumArgs();

    void Reset();
    void Copy(GetDocsumArgs *src);
    void initFromDocsumRequest(const search::engine::DocsumRequest &req);

    void SetRankProfile(const vespalib::string &ranking) { _ranking = ranking; }
    void SetQueryFlags(uint32_t qflags)         { _qflags = qflags; }
    void SetResultClassName(uint32_t len, const char *name) {
        _resultClassName.assign(name, len);
    }
    void setResultClassName(const vespalib::stringref & name) { _resultClassName = name; }
    void SetStackDump(uint32_t stackItems,
                      uint32_t stackDumpLen, const char *stackDump);
    void SetLocation(uint32_t locationLen, const char *location) {
        if ((_isLocationSet = (location != NULL))) {
            _location.assign(location, locationLen);
        }
    }

    void
    setFlags(uint32_t flags)
    {
        _flags = flags;
    }

    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::TimeStamp getTimeout() const;

    const vespalib::string & getRankProfile()          const { return _ranking; }
    const vespalib::string & getResultClassName()      const { return _resultClassName; }
    const vespalib::string & getLocation()             const { return _location; }
    const vespalib::stringref getStackDump()           const {
        return vespalib::stringref(&_stackDump[0], _stackDump.size());
    }

    uint32_t GetQueryFlags()                           const { return _qflags;       }
    uint32_t GetStackItems()                           const { return _stackItems;   }
    uint32_t GetLocationLen()                          const { return _location.size(); }
    uint32_t getFlags()                                const { return _flags; }

    const PropsMap &propertiesMap() const { return _propertiesMap; }

    const search::fef::Properties &rankProperties() const {
        return _propertiesMap.rankProperties();
    }
    const search::fef::Properties &featureOverrides() const {
        return _propertiesMap.featureOverrides();
    }
    const search::fef::Properties &highlightTerms() const {
        return _propertiesMap.highlightTerms();
    }
};

}
}

