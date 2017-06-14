// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1999-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchsummary/docsummary/getdocsumargs.h>

class FastS_query
{
public:
    uint32_t _dataset;
    uint32_t _flags;
    vespalib::string _stackDump;
    vespalib::string _printableQuery;
    vespalib::string _sortSpec;
    std::vector<char> _groupSpec; // this is binary
    vespalib::string  _location;
    search::fef::Properties _rankProperties;
    search::fef::Properties _featureOverrides;

    FastS_query(const FastS_query &other);
    FastS_query &operator=(const FastS_query &other);
public:
    FastS_query();
    FastS_query(const search::docsummary::GetDocsumArgs &docsumArgs);
    ~FastS_query();

    void SetStackDump(const vespalib::stringref& stackDump);
    void SetSortSpec(const char *spec) { _sortSpec = spec; }
    void SetLocation(const char *loc) { _location = loc; }
    void SetRankProperties(const search::fef::Properties &rp) { _rankProperties = rp; }
    void SetFeatureOverrides(const search::fef::Properties &fo) { _featureOverrides = fo; }
    void SetDataSet(uint32_t dataset);
    void SetQueryFlags(uint32_t flags) { _flags = flags; }
    void SetFlag(uint32_t flag) { _flags |= flag; }
    void ClearFlag(uint32_t flag) { _flags &= ~flag; }
    const vespalib::string &getStackDump() const { return _stackDump; }
    const char *GetSortSpec() const { return _sortSpec.c_str(); }
    const char *GetLocation() const { return _location.c_str(); }
    const search::fef::Properties &GetRankProperties() const { return _rankProperties; }
    const search::fef::Properties &GetFeatureOverrides() const { return _featureOverrides; }

    uint32_t GetQueryFlags() const { return _flags; }
    const char *getPrintableQuery();
    bool IsFlagSet(uint32_t flag) const { return (_flags & flag) != 0; }

    unsigned int StackDumpHashKey() const;


private:
    static unsigned int hash_str_check(const unsigned char *pt)
    {
        if (pt == NULL)
            return 0;

        unsigned int res = 0;
        for (; *pt != 0; pt++)
            res = (res << 7) + (res >> 25) + *pt;
        return res;
    }
    static bool cmp_str_check(const char *a, const char *b)
    {
        if (a == NULL && b == NULL)
            return true;
        if (a == NULL || b == NULL)
            return false;
        return (strcmp(a, b) == 0);
    }
    static bool cmp_str_ref(const vespalib::stringref &a,
                            const vespalib::stringref &b)
    {
        return (a.size() == b.size() &&
                memcmp(a.c_str(), b.c_str(), a.size()) == 0);
    }
};
