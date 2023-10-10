// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "native_factory.h"
#include "request_generator.h"
#include "server_tagger.h"
#include "qps_tagger.h"
#include "latency_analyzer.h"
#include "qps_analyzer.h"
#include "request_dumper.h"
#include "ignore_before.h"

namespace vbench {

Generator::UP
NativeFactory::createGenerator(const vespalib::slime::Inspector &spec,
                               Handler<Request> &next)
{
    std::string type = spec["type"].asString().make_string();
    if (type == "RequestGenerator") {
        return Generator::UP(new RequestGenerator(spec["file"].asString().make_string(), next));
    }
    return Generator::UP();
}

Tagger::UP
NativeFactory::createTagger(const vespalib::slime::Inspector &spec,
                            Handler<Request> &next)
{
    std::string type = spec["type"].asString().make_string();
    if (type == "ServerTagger") {
        return Tagger::UP(new ServerTagger(ServerSpec(spec["host"].asString().make_string(),
                                spec["port"].asLong()), next));
    }
    if (type == "QpsTagger") {
        return Tagger::UP(new QpsTagger(spec["qps"].asLong(), next));
    }
    return Tagger::UP();
}

Analyzer::UP
NativeFactory::createAnalyzer(const vespalib::slime::Inspector &spec,
                              Handler<Request> &next)
{
    std::string type = spec["type"].asString().make_string();
    if (type == "LatencyAnalyzer") {
        return Analyzer::UP(new LatencyAnalyzer(next));
    }
    if (type == "QpsAnalyzer") {
        return Analyzer::UP(new QpsAnalyzer(next));
    }
    if (type == "RequestDumper") {
        return Analyzer::UP(new RequestDumper());
    }
    if (type == "IgnoreBefore") {
        return Analyzer::UP(new IgnoreBefore(spec["time"].asDouble(), next));
    }
    return Analyzer::UP();
}

} // namespace vbench
