// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itablefactory.h"

namespace search::fef {

/**
 * This factory class is used to instantiate tables based on a function.
 * The name of the table specifies the function and arguments to use.
 * The following functions are supported:
 *   - expdecay(w,t)    : w * exp(-x/t)
 *   - loggrowth(w,t,s) : w * log(1 + x/s) + t
 *   - linear(w,t)      : w * x + t
 * All functions support an optional last parameter for setting the table size.
 **/
class FunctionTableFactory : public ITableFactory
{
public:
    struct ParsedName {
        vespalib::string type;
        std::vector<vespalib::string> args;
        ParsedName() noexcept : type(), args() {}
    };

    /**
     * Creates a new factory able to create tables with the given default size.
     **/
    explicit FunctionTableFactory(size_t defaultTableSize);

    /**
     * Creates a table where the given name specifies the function and arguments to use.
     **/
    [[nodiscard]] Table::SP createTable(const vespalib::string & name) const override;

    /**
     * Parses the given function name and returns true if success.
     **/
    static bool parseFunctionName(const vespalib::string & name, ParsedName & parsed);
private:
    size_t _defaultTableSize;

    bool checkArgs(const std::vector<vespalib::string> & args, size_t exp, size_t & tableSize) const;
    bool isSupported(const vespalib::string & type) const;
    bool isExpDecay(const vespalib::string & type) const { return type == "expdecay"; }
    bool isLogGrowth(const vespalib::string & type) const { return type == "loggrowth"; }
    bool isLinear(const vespalib::string & type) const { return type == "linear"; }
    Table::SP createExpDecay(double w, double t, size_t len) const;
    Table::SP createLogGrowth(double w, double t, double s, size_t len) const;
    Table::SP createLinear(double w, double t, size_t len) const;
};

}
