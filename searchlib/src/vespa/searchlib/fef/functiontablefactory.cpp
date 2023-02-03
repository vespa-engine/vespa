// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "functiontablefactory.h"
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <cmath>
#include <vespa/log/log.h>
LOG_SETUP(".fef.functiontablefactory");

namespace {

void logArgumentWarning(const vespalib::string & name, size_t exp, size_t act)
{
    LOG(warning, "Cannot create table for function '%s'. Wrong number of arguments: expected %zu to %zu, but got %zu",
        name.c_str(), exp, exp + 1, act);
}

}

namespace search::fef {

bool
FunctionTableFactory::checkArgs(const std::vector<vespalib::string> & args, size_t exp, size_t & tableSize) const
{
    if (exp <= args.size() && args.size() <= (exp + 1)) {
        if (args.size() == (exp + 1)) {
            tableSize = atoi(args.back().c_str());
        } else {
            tableSize = _defaultTableSize;
        }
        return true;
    }
    return false;
}

bool
FunctionTableFactory::isSupported(const vespalib::string & type) const
{
    return (isExpDecay(type) || isLogGrowth(type) || isLinear(type));
}

Table::SP
FunctionTableFactory::createExpDecay(double w, double t, size_t len) const
{
    Table::SP table(new Table());
    for (size_t x = 0; x < len; ++x) {
        table->add(w * std::exp(-(x / t)));
    }
    return table;
}

Table::SP
FunctionTableFactory::createLogGrowth(double w, double t, double s, size_t len) const
{
    auto table = std::make_shared<Table>();
    for (size_t x = 0; x < len; ++x) {
        table->add(w * (std::log(1 + (x / s))) + t);
    }
    return table;
}

Table::SP
FunctionTableFactory::createLinear(double w, double t, size_t len) const
{
    Table::SP table(new Table());
    for (size_t x = 0; x < len; ++x) {
        table->add(w * x + t);
    }
    return table;
}

FunctionTableFactory::FunctionTableFactory(size_t defaultTableSize) :
    _defaultTableSize(defaultTableSize)
{
}

Table::SP
FunctionTableFactory::createTable(const vespalib::string & name) const
{
    ParsedName p;
    if (parseFunctionName(name, p)) {
        if (isSupported(p.type)) {
            size_t tableSize = _defaultTableSize;
            if (isExpDecay(p.type)) {
                if (checkArgs(p.args, 2, tableSize)) {
                    return createExpDecay(vespalib::locale::c::atof(p.args[0].c_str()), vespalib::locale::c::atof(p.args[1].c_str()), tableSize);
                }
                logArgumentWarning(name, 2, p.args.size());
            } else if (isLogGrowth(p.type)) {
                if (checkArgs(p.args, 3, tableSize)) {
                    return createLogGrowth(vespalib::locale::c::atof(p.args[0].c_str()), vespalib::locale::c::atof(p.args[1].c_str()), vespalib::locale::c::atof(p.args[2].c_str()), tableSize);
                }
                logArgumentWarning(name, 3, p.args.size());
            } else if (isLinear(p.type)) {
                if (checkArgs(p.args, 2, tableSize)) {
                    return createLinear(vespalib::locale::c::atof(p.args[0].c_str()), vespalib::locale::c::atof(p.args[1].c_str()), tableSize);
                }
                logArgumentWarning(name, 2, p.args.size());
            }
        } else {
            LOG(warning, "Cannot create table for function '%s'. Function type '%s' is not supported",
                name.c_str(), p.type.c_str());
        }
    } else {
        LOG(warning, "Cannot create table for function '%s'. Could not be parsed.", name.c_str());
    }
    return {};
}

bool
FunctionTableFactory::parseFunctionName(const vespalib::string & name, ParsedName & parsed)
{
    size_t ps = name.find('(');
    size_t pe = name.find(')');
    if (ps == vespalib::string::npos || pe == vespalib::string::npos) {
        LOG(warning, "Parse error: Did not find '(' and ')' in function name '%s'", name.c_str());
        return false;
    }
    if (ps >= pe) {
        LOG(warning, "Parse error: Found ')' before '(' in function name '%s'", name.c_str());
        return false;
    }
    parsed.type = name.substr(0, ps);
    vespalib::string args = name.substr(ps + 1, pe - ps - 1);
    if (!args.empty()) {
        vespalib::StringTokenizer tokenizer(args);
        for (const auto & token : tokenizer) {
            parsed.args.emplace_back(token);
        }
    }
    return true;
}

}
