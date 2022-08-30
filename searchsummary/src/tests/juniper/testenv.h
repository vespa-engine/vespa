// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/* Include most of the stuff that we might need */

#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/juniper/query.h>
#include <vespa/juniper/juniperdebug.h>
#include <vespa/juniper/rpinterface.h>
#include <vespa/juniper/queryhandle.h>
#include <vespa/juniper/queryparser.h>
#include <vespa/juniper/queryvisitor.h>
#include <vespa/juniper/result.h>
#include <vespa/juniper/config.h>
#include <vespa/juniper/queryparser.h>
#include <vespa/juniper/matchobject.h>
#include <vespa/juniper/SummaryConfig.h>
#include <vespa/juniper/Matcher.h>
#include <vespa/juniper/mcand.h>
#include <vespa/juniper/propreader.h>
#include <vespa/juniper/specialtokenregistry.h>

namespace juniper
{

class TestEnv
{
public:
    TestEnv(int argc, char **argv, const char* propfile);
    virtual ~TestEnv();
    void Usage(char* s);
private:
    std::unique_ptr<PropReader> _props;
    std::unique_ptr<Config>     _config;
    std::unique_ptr<Juniper>    _juniper;
    Fast_NormalizeWordFolder    _wordFolder;
    TestEnv(const TestEnv&);
    TestEnv& operator=(const TestEnv&);
};


class TestQuery
{
public:
    TestQuery(const char* qexp, const char* options = NULL);
    QueryParser _qparser;
    QueryHandle _qhandle;
};


class PropertyMap : public IJuniperProperties
{
private:
    std::map<std::string, std::string> _map;
public:
    PropertyMap();
    ~PropertyMap();
    PropertyMap &set(const char *name, const char *value);
    const char* GetProperty(const char* name, const char* def = nullptr) const override;
};


extern Config* TestConfig;
extern Juniper * _Juniper;

} // end namespace juniper

typedef juniper::TestQuery TestQuery;

