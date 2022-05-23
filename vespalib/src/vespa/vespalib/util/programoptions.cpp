// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "programoptions.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <boost/lexical_cast.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".programoptions");

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION(InvalidCommandLineArgumentsException, Exception);

namespace {

    std::string UNSET_TOKEN = "-_-/#UNSET#\\-_-";

    // Use tokenizer instead when moved from document
    std::vector<std::string> splitString(const std::string& source, char split)
    {
        std::vector<std::string> target;
        std::string::size_type start = 0;
        std::string::size_type stop = source.find(split);
        while (stop != std::string::npos) {
            target.push_back(source.substr(start, stop - start));
            start = stop + 1;
            stop = source.find(split, start);
        }
        target.push_back(source.substr(start));
        return target;
    }

}

template<typename Number>
std::string ProgramOptions::NumberOptionParser<Number>::getStringValue(Number n)
{
    std::ostringstream ost;
    ost << n;
    return ost.str();
}

template<typename Number>
void ProgramOptions::NumberOptionParser<Number>::set(const std::vector<std::string>& arguments)
{
    try{
        _number = boost::lexical_cast<Number>(arguments[0]);
    } catch (const boost::bad_lexical_cast& e) {
        std::ostringstream ost;
        ost << "The argument '" << arguments[0]
            << "' can not be interpreted as a number of type "
            << getTypeName<Number>() << ".";
        throw InvalidCommandLineArgumentsException(
                ost.str(), VESPA_STRLOC);
    }
}

ProgramOptions::FlagOptionParser::~FlagOptionParser() = default;

ProgramOptions::StringOptionParser::~StringOptionParser() = default;

ProgramOptions::OptionParser::OptionParser(
        const std::string& nameList, uint32_t argCount, const std::string& desc)
    : _names(splitString(nameList, ' ')),
      _hiddenNames(),
      _argCount(argCount),
      _argTypes(argCount),
      _hasDefault(false),
      _invalidDefault(false),
      _defaultString(),
      _description(desc)
{
    if (nameList == "") _names.clear();
}

ProgramOptions::OptionParser::OptionParser(
        const std::string& nameList, uint32_t argCount,
        const std::string& defString, const std::string& desc)
    : _names(splitString(nameList, ' ')),
      _hiddenNames(),
      _argCount(argCount),
      _argTypes(argCount),
      _hasDefault(true),
      _invalidDefault(false),
      _defaultString(defString),
      _description(desc)
{ }

ProgramOptions::OptionParser::~OptionParser() { }

void
ProgramOptions::OptionParser::setInvalidDefault()
{
    _invalidDefault = true;
}

std::string ProgramOptions::OptionParser::getOptSyntaxString() const
{
    std::ostringstream ost;
    for (uint32_t i=0; i<_names.size(); ++i) {
        ost << (_names[i].size() == 1 ? " -" : " --");
        ost << _names[i];
    }
    for (uint32_t i=0; i<_argCount; ++i) {
        std::string type = (_argTypes[i] != "" ? _argTypes[i] : getArgType(i));
        ost << " <" << type << ">";
    }
    return ost.str();
}

ProgramOptions::ProgramOptions()
    : _argc(0),
      _argv(0),
      _options(),
      _optionMap(),
      _setOptions(),
      _syntaxMessage(),
      _maxLeftColumnSize(30),
      _defaultsSet(false)
{ }

ProgramOptions::ProgramOptions(int argc, const char* const* argv)
    : _argc(argc),
      _argv(argv),
      _options(),
      _optionMap(),
      _setOptions(),
      _syntaxMessage(),
      _maxLeftColumnSize(30),
      _defaultsSet(false)
{ }

ProgramOptions::~ProgramOptions() { }

void
ProgramOptions::clear() {
    _configurables.clear();
    _options.clear();
    _optionMap.clear();
    _setOptions.clear();
    _arguments.clear();
}

void
ProgramOptions::setCommandLineArguments(int argc, const char* const* argv)
{
    _argc = argc;
    _argv = argv;
}

void
ProgramOptions::setSyntaxMessage(const std::string& msg)
{
    _syntaxMessage = msg;
}

void
ProgramOptions::addHiddenIdentifiers(const std::string& optionNameList)
{
    if (_options.size() == 0) {
        throw InvalidCommandLineArgumentsException(
                "Cannot add hidden identifier to last "
                "option as no option has been added yet.", VESPA_STRLOC);
    }
    OptionParser::SP opt = _options.back();
    if (opt->isHeader()) {
        throw InvalidCommandLineArgumentsException(
                "Cannot add option arguments to option header.", VESPA_STRLOC);
    }
    std::vector<std::string> newIds(splitString(optionNameList, ' '));
    for (uint32_t i=0; i<newIds.size(); ++i) {
        std::map<std::string, OptionParser::SP>::const_iterator it(
                _optionMap.find(newIds[i]));
        if (it != _optionMap.end()) {
            throw InvalidCommandLineArgumentsException(
                    "Option '" + newIds[i] + "' is already registered.",
                    VESPA_STRLOC);
        }
    }
    for (uint32_t i=0; i<newIds.size(); ++i) {
        _optionMap[newIds[i]] = opt;
        opt->_hiddenNames.push_back(newIds[i]);
    }
}

void
ProgramOptions::setArgumentTypeName(const std::string& name, uint32_t index)
{
    if (_options.size() == 0) {
        throw InvalidCommandLineArgumentsException(
                "Cannot add hidden identifier to last "
                "option as no option has been added yet.", VESPA_STRLOC);
    }
    OptionParser::SP opt = _options.back();
    if (opt->isHeader()) {
        throw InvalidCommandLineArgumentsException(
                "Cannot add option arguments to option header.", VESPA_STRLOC);
    }
    opt->_argTypes[index] = name;
}

void
ProgramOptions::addOptionHeader(const std::string& description)
{
    _options.push_back(OptionParser::SP(new OptionHeader(description)));
}

namespace {
    bool isNumber(const std::string& arg) {
        if (arg.size() > 1 && arg[0] == '-'
            && arg[1] >= '0' && arg[1] <= '9')
        {
            return true;
        }
        return false;
    }
}

void
ProgramOptions::parse()
{
    try{
        std::ostringstream ost;
        ost << "Parsing options:\n";
        for (int i=0; i<_argc; ++i) {
            ost << "  " << i << ": '" << _argv[i] << "'\n";
        }
        LOG(debug, "%s", ost.str().c_str());
        uint32_t argPos = 0;
        uint32_t optPos = 1;
        for (; optPos < static_cast<uint32_t>(_argc); ++optPos) {
            std::string s(_argv[optPos]);
                // Skip arguments
            if (s.size() < 2 || s[0] != '-' || s == "--" || isNumber(s)) {
                if (argPos <= optPos) { // No more options to parse
                    break;
                } else { // This has already been consumed as argument
                    continue;
                }
            }
            if (argPos <= optPos) { argPos = optPos + 1; }
            if (s.substr(0, 2) == "--") {
                std::string id(s.substr(2));
                LOG(debug, "Parsing long option %s at pos %u, arg pos is now "
                           "%u", id.c_str(), optPos, argPos);
                std::map<std::string, OptionParser::SP>::const_iterator it(
                        _optionMap.find(id));
                if (it == _optionMap.end()) {
                    throw InvalidCommandLineArgumentsException(
                            "Invalid option '" + id + "'.", VESPA_STRLOC);
                }
                parseOption(id, *it->second, argPos);
                _setOptions.insert(it->second);
            } else {
                LOG(debug, "Parsing short options %s at pos %u, arg pos is now "
                           "%u.", s.c_str() + 1, optPos, argPos);
                for (uint32_t shortPos = 1; shortPos < s.size(); ++shortPos) {
                    std::string id(s.substr(shortPos, 1));
                    LOG(debug, "Parsing short option %s, arg pos is %u.",
                               id.c_str(), argPos);
                    std::map<std::string, OptionParser::SP>::const_iterator it(
                            _optionMap.find(id));
                    if (it == _optionMap.end()) {
                        throw InvalidCommandLineArgumentsException(
                                "Invalid option '" + id + "'.", VESPA_STRLOC);
                    }
                    parseOption(id, *it->second, argPos);
                    _setOptions.insert(it->second);
                }
            }
        }
        if (!_defaultsSet) setDefaults(true);
        for (uint32_t i=0; i<_arguments.size(); ++i) {
            OptionParser& opt(*_arguments[i]);
            if (opt._argCount == 0) {
                LOG(debug, "Parsing list argument %s. Pos is %u.",
                        opt.getArgName().c_str(), optPos);
                std::vector<std::string> arguments;
                for (uint32_t j=optPos; j<static_cast<uint32_t>(_argc); ++j) {
                    arguments.push_back(_argv[j]);
                }
                opt.set(arguments);
                optPos = _argc;
                LOG(debug, "Done. Pos is now %u.", optPos);
            } else if (optPos + opt._argCount > static_cast<uint32_t>(_argc)) {
                if (!opt.isRequired()) {
                    LOG(debug, "Setting default for argument %u.", i);
                    opt.setDefault();
                } else {
                    throw InvalidCommandLineArgumentsException(
                        "Insufficient data is given to set required argument '"
                        + opt.getArgName() + "'.", VESPA_STRLOC);
                }
            } else {
                parseArgument(opt, optPos);
            }
        }
    } catch (const vespalib::Exception& e) {
        throw;
    }
    for (uint32_t i=0; i<_configurables.size(); ++i) {
        _configurables[i]->finalizeOptions();
    }
}

void
ProgramOptions::setDefaults(bool failUnsetRequired)
{
    for (uint32_t i=0; i<_options.size(); ++i) {
        OptionParser::SP opt(_options[i]);
        if (opt->isHeader()) {
            continue;
        }
        std::set<OptionParser::SP>::const_iterator it(_setOptions.find(opt));
        if (it == _setOptions.end()) {
            if (opt->_hasDefault) {
                opt->setDefault();
            } else if (failUnsetRequired) {
                throw InvalidCommandLineArgumentsException(
                    "Option '" + opt->_names[0]
                    + "' has no default and must be set.", VESPA_STRLOC);
            }
        }
    }
    _defaultsSet = true;
}

void
ProgramOptions::parseOption(
        const std::string& id, OptionParser& opt, uint32_t& argPos)
{
    LOG(debug, "Parsing option %s. Argpos is %u.", id.c_str(), argPos);
    std::vector<std::string> arguments;
    for (; arguments.size() != opt._argCount; ++argPos) {
        if (argPos >= static_cast<uint32_t>(_argc)) {
            throw InvalidCommandLineArgumentsException(
                    vespalib::make_string(
                        "Option '%s' needs %u arguments. Only %u available.",
                        id.c_str(), opt._argCount, (uint32_t) arguments.size()),
                    VESPA_STRLOC);
        }
        if (strlen(_argv[argPos]) >= 2 && _argv[argPos][0] == '-'
            && !isNumber(_argv[argPos]))
        {
            continue;
        }
        arguments.push_back(_argv[argPos]);
    }
    opt.set(arguments);
    LOG(debug, "Done. Argpos is now %u.", argPos);
}

void
ProgramOptions::parseArgument(OptionParser& opt, uint32_t& pos)
{
    LOG(debug, "Parsing argument %s. Pos is %u.",
        opt.getArgName().c_str(), pos);
    std::vector<std::string> arguments;
    for (; arguments.size() != opt._argCount; ++pos) {
        assert(pos < static_cast<uint32_t>(_argc));
        arguments.push_back(_argv[pos]);
    }
    opt.set(arguments);
    LOG(debug, "Done. Pos is now %u.", pos);
}

namespace {
    std::vector<std::string> breakText(const std::vector<std::string>& source,
                                       uint32_t maxLen,
                                       int preserveWordSpaceLimit = -1)
    {
        if (preserveWordSpaceLimit < 0) {
            preserveWordSpaceLimit = maxLen / 5;
        }
        std::vector<std::string> result;
        for (uint32_t i=0; i<source.size(); ++i) {
            std::vector<std::string> split(splitString(source[i], '\n'));
            for (uint32_t j=0; j<split.size(); ++j) {
                    // Process each line of input here to possible break
                    // it.
                std::string line = split[j];
                while (true) {
                        // If the line is already short enough, we're done.
                    if (line.size() <= maxLen) {
                        result.push_back(line);
                        break;
                    }
                        // Otherwise, find the last space before max len
                    std::string::size_type pos(
                            line.rfind(" ", maxLen));
                    if (pos != std::string::npos
                        && pos > maxLen - preserveWordSpaceLimit)
                    {
                            // If the space comes late enough, add the line up
                            // to that point, and let the remainder go to a new
                            // line.
                        result.push_back(line.substr(0, pos));
                        line = line.substr(pos+1);
                    } else {
                            // If the space is not late enough, force break
                            // inside a word and add a dash.
                        result.push_back(line.substr(0, maxLen - 1) + "-");
                        line = line.substr(maxLen - 1);
                    }
                }
            }
        }
        return result;
    }

    std::vector<std::string> breakText(const std::string& source,
                                       uint32_t maxLen,
                                       int preserveWordSpaceLimit = -1)
    {
        std::vector<std::string> v(1);
        v[0] = source;
        return breakText(v, maxLen, preserveWordSpaceLimit);
    }
}

void
ProgramOptions::writeSyntaxPage(std::ostream& out, bool showDefaults)
{
    bool hasOptions = false;
    for(uint32_t i=0; i<_options.size(); ++i) {
        if (!_options[i]->isHeader() && !_options[i]->hideFromSyntaxPage()) {
            hasOptions = true;
        }
    }
    if (!_syntaxMessage.empty()) {
        out << "\n";
        std::vector<std::string> text(breakText(_syntaxMessage, 80));
        for (uint32_t i=0; i<text.size(); ++i) {
            out << text[i] << "\n";
        }
    }
    if (_argc > 0) {
        std::string progName = _argv[0];
        out << "\nUsage: ";
        std::string::size_type pos = progName.rfind("/");
        if (pos != std::string::npos) {
            out << progName.substr(pos+1);
        } else {
            out << progName;
        }
        if (hasOptions) {
            out << " [options]";
        }
        for (uint32_t i=0; i<_arguments.size(); ++i) {
            OptionParser& opt(*_arguments[i]);
            out << (opt.isRequired() ? " <" : " [")
                << opt.getArgName();
            if (opt._argCount == 0) out << "...";
            out << (opt.isRequired() ? ">" : "]");
        }
        out << "\n";
    }
    if (_arguments.size() > 0) {
        out << "\nArguments:\n";
            // To reuse option parser objects, argument names get split on
            // whitespace. Concatenating to show nice text
        std::vector<std::string> argNames(_arguments.size());
        uint32_t argSize = 10;
        for(uint32_t i=0; i<_arguments.size(); ++i) {
            OptionParser& opt(*_arguments[i]);
            argNames[i] = opt.getArgName()
                        + " (" + opt.getArgType(0) + ")";
            if (argNames[i].size() <= _maxLeftColumnSize) {
                argSize = std::max(argSize,
                                   static_cast<uint32_t>(argNames[i].size()));
            }
        }
            // Calculate indent used for extra lines
        std::string indent = "    "; // 1 space indent + " : " in between.
        for (uint32_t i=0; i<argSize; ++i) indent += ' ';

        for(uint32_t i=0; i<_arguments.size(); ++i) {
            OptionParser& opt(*_arguments[i]);
            out << " " << argNames[i];
            if (argNames[i].size() > _maxLeftColumnSize) {
                out << "\n ";
                for (uint32_t j=0; j<argSize; ++j) {
                    out << " ";
                }
            } else {
                for (uint32_t j=argNames[i].size(); j<argSize; ++j) {
                    out << " ";
                }
            }
            std::vector<std::string> message(
                    breakText(opt._description, 80 - indent.size()));
            if (opt._hasDefault) {
                if (message.back().size() + indent.size() + 11 <= 80) {
                    message.back() += " (optional)";
                } else {
                    message.push_back("(optional)");
                }
            }
            for (uint32_t j=0; j<message.size(); ++j) {
                out << (j == 0 ? " : " : indent) << message[j] << "\n";
            }
        }
    }
    if (hasOptions) {
        if (!_options[0]->isHeader()) {
            out << "\nOptions:\n";
        }
        uint32_t argSize = 10;
        for(uint32_t i=0; i<_options.size(); ++i) {
            OptionParser& opt(*_options[i]);
            if (opt.isHeader() || opt.hideFromSyntaxPage()) continue;
            argSize = std::max(argSize, static_cast<uint32_t>(
                                    opt.getOptSyntaxString().size()));
        }
            // If too much space is used in first colo, calculate inset again,
            // such that we don't need to push to max just because one is
            // too long
        if (argSize > _maxLeftColumnSize) {
            argSize = 10;
            for(uint32_t i=0; i<_options.size(); ++i) {
                OptionParser& opt(*_options[i]);
                if (opt.isHeader() || opt.hideFromSyntaxPage()) continue;
                uint32_t size = static_cast<uint32_t>(
                        opt.getOptSyntaxString().size());
                if (size <= _maxLeftColumnSize) {
                    argSize = std::max(argSize, size);
                }
            }
        }
        std::string indent = "   ";
        for (uint32_t i=0; i<argSize; ++i) indent += ' ';
        for(uint32_t i=0; i<_options.size(); ++i) {
            OptionParser& opt(*_options[i]);
            if (opt.isHeader()) {
                out << "\n" << opt._description << ":\n";
                continue;
            }
            if (opt.hideFromSyntaxPage()) continue;
            std::string optStr = opt.getOptSyntaxString();
            out << optStr;
            for (uint32_t j=optStr.size(); j<argSize; ++j) {
                out << " ";
            }
            std::vector<std::string> message(
                    breakText(opt._description, 80 - indent.size()));
            if (showDefaults) {
                std::string s;
                if (!opt._hasDefault) {
                    s = "(required)";
                } else if (!opt._invalidDefault
                           && opt._defaultString != UNSET_TOKEN)
                {
                    s = "(default " + opt._defaultString + ")";
                }
                if (s.size() > 0) {
                    if (message.back().size() + indent.size()
                            + 1 + s.size() <= 80)
                    {
                        message.back() += " " + s;
                    } else {
                        message.push_back(s);
                    }
                }
            }
            for (uint32_t j=0; j<message.size(); ++j) {
                out << (j == 0 ? " : " : indent) << message[j] << "\n";
            }
        }
    }
}

ProgramOptions::OptionParser&
ProgramOptions::addOption(OptionParser::SP && opt)
{
    for (uint32_t i=0; i<opt->_names.size(); ++i) {
        std::map<std::string, OptionParser::SP>::const_iterator it(
                _optionMap.find(opt->_names[i]));
        if (it != _optionMap.end()) {
            throw InvalidCommandLineArgumentsException(
                    "Option '" + opt->_names[i] + "' is already registered.",
                    VESPA_STRLOC);
        }
    }
    _options.push_back(opt);
    for (uint32_t i=0; i<opt->_names.size(); ++i) {
        _optionMap[opt->_names[i]] = opt;
    }
    return *opt;
}

ProgramOptions::OptionParser&
ProgramOptions::addArgument(std::shared_ptr<OptionParser> arg)
{
    if (!_arguments.empty() && !_arguments.back()->isRequired()) {
        if (arg->isRequired()) {
            throw InvalidCommandLineArgumentsException(
                    "Argument '" + arg->_names[0] + "' is required and cannot "
                    "follow an optional argument.", VESPA_STRLOC);
        }
    }
    if (!_arguments.empty() && _arguments.back()->_argCount == 0) {
        throw InvalidCommandLineArgumentsException(
                "Argument '" + arg->_names[0] + "' cannot follow a list "
                "argument that will consume all remaining arguments.",
                VESPA_STRLOC);
        
    }
    _arguments.push_back(arg);
    return *arg;
}

ProgramOptions::OptionParser&
ProgramOptions::getOptionParser(const std::string& id)
{
    std::map<std::string, OptionParser::SP>::const_iterator it(
            _optionMap.find(id));
    if (it == _optionMap.end()) {
        throw InvalidCommandLineArgumentsException(
                "No option registered with id '" + id + "'.", VESPA_STRLOC);
    }
    return *it->second;
}

ProgramOptions::OptionParser&
ProgramOptions::getArgumentParser(uint32_t argIndex)
{
    if (argIndex >= _arguments.size()) {
        std::ostringstream ost;
        ost << "Only " << _arguments.size()
            << " arguments registered. Thus argument " << argIndex
            << " does not exist.";
        throw InvalidCommandLineArgumentsException(ost.str(), VESPA_STRLOC);
    }
    return *_arguments[argIndex];
}

ProgramOptions::BoolOptionParser::BoolOptionParser(
        const std::string& nameList, bool& value, const std::string& desc)
    : OptionParser(nameList, 0, UNSET_TOKEN, desc),
      _value(value),
      _defaultValue(false)
{
}

ProgramOptions::FlagOptionParser::FlagOptionParser(
        const std::string& nameList, bool& value, const std::string& desc)
    : OptionParser(nameList, 0, UNSET_TOKEN, desc),
      _value(value),
      _unsetValue(false)
{
    _invalidDefault = true;
}

ProgramOptions::FlagOptionParser::FlagOptionParser(
        const std::string& nameList, bool& value, const bool& unsetValue,
        const std::string& desc)
    : OptionParser(nameList, 0, unsetValue ? "true" : "false", desc),
      _value(value),
      _unsetValue(unsetValue)
{
    _invalidDefault = true;
}

ProgramOptions::StringOptionParser::StringOptionParser(
        const std::string& nameList, std::string& val, const std::string& desc)
    : OptionParser(nameList, 1, desc),
      _value(val),
      _defaultValue()
{
}

ProgramOptions::StringOptionParser::StringOptionParser(
        const std::string& nameList, std::string& value,
        const std::string& defValue, const std::string& desc)
    : OptionParser(nameList, 1, '"' + defValue + '"', desc),
      _value(value),
      _defaultValue(defValue)
{
}

ProgramOptions::MapOptionParser::MapOptionParser(
        const std::string& nameList, std::map<std::string, std::string>& value,
        const std::string& description)
    : OptionParser(nameList, 2, "empty", description),
      _value(value)
{
}

#define VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(type, parsertype) \
template<> \
ProgramOptions::OptionParser& \
ProgramOptions::addOption(const std::string& optionNameList, \
                          type& value, const std::string& desc) \
{ \
    return addOption(OptionParser::SP( \
            new parsertype(optionNameList, value, desc))); \
}

VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(bool, FlagOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(std::string, StringOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(int32_t, NumberOptionParser<int32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(uint32_t, NumberOptionParser<uint32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(int64_t, NumberOptionParser<int64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(uint64_t, NumberOptionParser<uint64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(float, NumberOptionParser<float>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(double, NumberOptionParser<double>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDOPTION(MapOptionParser::MapType, MapOptionParser);

#define VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(type, parsertype) \
template<> \
ProgramOptions::OptionParser& \
ProgramOptions::addOption(const std::string& optionNameList, \
                          type& value, const type& defVal, \
                          const std::string& desc) \
{ \
    return addOption(OptionParser::SP( \
            new parsertype(optionNameList, value, defVal, desc))); \
}

VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(bool, FlagOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(std::string, StringOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(int32_t, NumberOptionParser<int32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(uint32_t, NumberOptionParser<uint32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(int64_t, NumberOptionParser<int64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(uint64_t, NumberOptionParser<uint64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(float, NumberOptionParser<float>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDOPTION(double, NumberOptionParser<double>);

#define VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(type, parsertype) \
template<> \
ProgramOptions::OptionParser& \
ProgramOptions::addArgument(const std::string& name, \
                            type& value, \
                            const std::string& desc) \
{ \
    return addArgument(OptionParser::SP( \
                new parsertype(name, value, desc))); \
}

VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(bool, BoolOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(std::string, StringOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(int32_t, NumberOptionParser<int32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(uint32_t, NumberOptionParser<uint32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(int64_t, NumberOptionParser<int64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(uint64_t, NumberOptionParser<uint64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(float, NumberOptionParser<float>);
VESPALIB_PROGRAMOPTIONS_IMPL_NODEF_ADDARGUMENT(double, NumberOptionParser<double>);

#define VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(type, parsertype) \
template<> \
ProgramOptions::OptionParser& \
ProgramOptions::addArgument(const std::string& name, \
                            type& value, const type& defVal, \
                            const std::string& desc) \
{ \
    return addArgument(OptionParser::SP( \
                new parsertype(name, value, defVal, desc))); \
}

VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(std::string, StringOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(int32_t, NumberOptionParser<int32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(uint32_t, NumberOptionParser<uint32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(int64_t, NumberOptionParser<int64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(uint64_t, NumberOptionParser<uint64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(float, NumberOptionParser<float>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDARGUMENT(double, NumberOptionParser<double>);

#define VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(type, parsertype) \
template<> \
ProgramOptions::OptionParser& \
ProgramOptions::addListArgument(const std::string& name, \
                                std::vector<type>& value, \
                                const std::string& desc) \
{ \
    ListOptionParser<type>* listParser( \
            new ListOptionParser<type>(name, value, desc)); \
    OptionParser::UP entryParser( \
            new parsertype(name, listParser->getSingleValue(), desc)); \
    listParser->setEntryParser(std::move(entryParser)); \
    return addArgument(OptionParser::SP(listParser)); \
}

VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(std::string, StringOptionParser);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(int32_t, NumberOptionParser<int32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(uint32_t, NumberOptionParser<uint32_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(int64_t, NumberOptionParser<int64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(uint64_t, NumberOptionParser<uint64_t>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(float, NumberOptionParser<float>);
VESPALIB_PROGRAMOPTIONS_IMPL_ADDLISTARGUMENT(double, NumberOptionParser<double>);

template struct ProgramOptions::NumberOptionParser<int32_t>;
template struct ProgramOptions::NumberOptionParser<uint32_t>;
template struct ProgramOptions::NumberOptionParser<int64_t>;
template struct ProgramOptions::NumberOptionParser<uint64_t>;
template struct ProgramOptions::NumberOptionParser<float>;
template struct ProgramOptions::NumberOptionParser<double>;

} // vespalib
