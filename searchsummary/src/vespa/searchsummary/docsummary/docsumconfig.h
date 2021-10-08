// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-summarymap.h>

namespace search { class MatchingElementsFields; }
namespace search::docsummary {

class IDocsumEnvironment;
class DynamicDocsumWriter;
class ResultConfig;
class IDocsumFieldWriter;

class DynamicDocsumConfig
{
public:
    DynamicDocsumConfig(IDocsumEnvironment * env, DynamicDocsumWriter * writer) :
        _env(env),
        _writer(writer)
    { }
    virtual ~DynamicDocsumConfig() { }
    void configure(const vespa::config::search::SummarymapConfig &cfg);
protected:
    using string = vespalib::string;
    IDocsumEnvironment * getEnvironment() { return _env; }
    const IDocsumEnvironment * getEnvironment() const { return _env; }
    const ResultConfig & getResultConfig() const;

    virtual std::unique_ptr<IDocsumFieldWriter>
    createFieldWriter(const string & fieldName, const string & overrideName,
                      const string & argument, bool & rc, std::shared_ptr<MatchingElementsFields> matching_elems_fields);
private:
    IDocsumEnvironment  * _env;
    DynamicDocsumWriter * _writer;
};

}

