// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include <vespa/searchlib/parsequery/simplequerystack.h>

/** Marks as empty
 */
FastS_query::FastS_query()
    : _dataset(0),
      _flags(0),
      _stackDump(),
      _sortSpec(),
      _groupSpec(),
      _location(),
      _rankProperties(),
      _featureOverrides()
{
};

FastS_query::FastS_query(const search::docsummary::GetDocsumArgs &docsumArgs)
    : _dataset(0),                        // not known
      _flags(docsumArgs.GetQueryFlags()),
      _stackDump(docsumArgs.getStackDump()),
      _sortSpec(),                    // not known
      _groupSpec(),                   // not known
      _location(),
      _rankProperties(docsumArgs.rankProperties()),
      _featureOverrides(docsumArgs.featureOverrides())
{
    // _query = search::SimpleQueryStack::StackbufToString(docsumArgs.getStackDump());
    if (docsumArgs.getLocation().size() > 0) {
        _location = strdup(docsumArgs.getLocation().c_str());
    }
}


void
FastS_query::SetStackDump(const vespalib::stringref &stackRef)
{
    _stackDump = stackRef;
}

const char *
FastS_query::getPrintableQuery()
{
    if (_printableQuery.empty()) {
        _printableQuery = search::SimpleQueryStack::StackbufToString(_stackDump);
    }
    return  _printableQuery.c_str();
}

FastS_query::~FastS_query()
{
}


void
FastS_query::SetDataSet(uint32_t dataset)
{
    _dataset = dataset;
}

unsigned int
FastS_query::StackDumpHashKey() const
{
    unsigned int res = 0;
    const unsigned char *p;
    const unsigned char *e;
    p = (const unsigned char *) _stackDump.begin();
    e = (const unsigned char *) _stackDump.end();
    while (p != e) {
        res = (res << 7) + (res >> 25) + *p;
        p++;
    }
    return res;
}

namespace
{

// This is ugly, somebody please find a better way.

class SizeCollector : public search::fef::IPropertiesVisitor
{
    static const size_t _stringFuzz = 15; // Compensate for malloc() waste
    static const size_t _vectorFuzz = 15;
    static const size_t _mapFuzz = 15;
    size_t _size;
public:
    SizeCollector()
        : _size(0)
    {
    }

    virtual void
    visitProperty(const search::fef::Property::Value &key,
                  const search::fef::Property &values) override
    {
        // Account for std::map element size
        _size += _mapFuzz;
        // Account for key string size
        _size += key.size() + _stringFuzz;
        size_t numValues = values.size();
        // Account for value vector size
        if (numValues > 0) {
            _size += numValues * sizeof(search::fef::Property::Value) + _vectorFuzz;
            for (size_t i = 0; i < numValues; ++i) {
                // Account for string sizes in value vector
                const search::fef::Property::Value &str = values.getAt(i);
                _size += str.size() + _stringFuzz;
            }
        }
    }

    size_t
    getSize() const
    {
        return _size;
    }
};

}
