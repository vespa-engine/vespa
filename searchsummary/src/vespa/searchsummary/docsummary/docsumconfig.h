// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-summarymap.h>

namespace search { class MatchingElementsFields; }
namespace search::docsummary {

class IDocsumEnvironment;
class IDocsumFieldWriterFactory;
class DocsumFieldWriter;
class DynamicDocsumWriter;
class ResultConfig;

class DynamicDocsumConfig
{
public:
    DynamicDocsumConfig(IDocsumEnvironment * env, DynamicDocsumWriter * writer) :
        _env(env),
        _writer(writer)
    { }
    virtual ~DynamicDocsumConfig() = default;
    void configure(const vespa::config::search::SummarymapConfig &cfg);
protected:
    using string = vespalib::string;
    IDocsumEnvironment * getEnvironment() { return _env; }
    const ResultConfig & getResultConfig() const;

    virtual std::unique_ptr<IDocsumFieldWriterFactory> make_docsum_field_writer_factory();
private:
    IDocsumEnvironment  * _env;
    DynamicDocsumWriter * _writer;
};

}

