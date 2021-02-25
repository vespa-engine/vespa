// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class vespalib::ProgramOptions
 * \ingroup util
 *
 * \brief Utility class for easy parsing of program options.
 *
 * This class makes it easy to parse program options, and to write a decent
 * syntax page.
 *
 * Just call addOption to register options, and call parseOptions to do the
 * parsing. There's also a function for writing the syntax page, such that this
 * is automatically updated.
 *
 * Stuff to come later:
 *
 * Support for arguments (So you can do stuff like ./myprog file.txt, and not
 *                        only stuff like ./myprog -f file.txt)
 * Setting min and max values for numbers.
 * Support for multiargument options.
 * Automatic man page writing.
 */

#pragma once

#include <vespa/vespalib/util/exception.h>
#include <map>
#include <set>
#include <vector>
#include <memory>

namespace vespalib {

VESPA_DEFINE_EXCEPTION(InvalidCommandLineArgumentsException, Exception);

struct ProgramOptions {
    /** Utility class used by command line configurable utility. */
    class LifetimeToken {
        ProgramOptions& o;
    public:
        typedef std::unique_ptr<LifetimeToken> UP;
        LifetimeToken(ProgramOptions& op) : o(op) {}
        ~LifetimeToken() { o.clear(); }
    };
    /**
     * Utility class used to deletage stuff to be configured into multiple
     * units.
     */
    struct Configurable {
        virtual ~Configurable() {}
        /**
         * Called on configurables to have it register its options.
         * Unit must hang onto lifetimetoken until command line parsing have
         * completed. Lifetimetoken should be deleted before stuff registered
         * to be configured is deleted.
         */
        virtual void registerCommandLineOptions(ProgramOptions&, LifetimeToken::UP) = 0;

        /**
         * Called after command line parsing is complete, in order for
         * components to ensure validity of options and throw exceptionse on
         * failures.
         */
        virtual void finalizeOptions() = 0;
    };

    struct OptionParser;

    int _argc;
    const char* const* _argv;
    std::vector<std::shared_ptr<OptionParser> > _options;
    std::map<std::string, std::shared_ptr<OptionParser> > _optionMap;
    std::set<std::shared_ptr<OptionParser> > _setOptions;
    std::vector<std::shared_ptr<OptionParser> > _arguments;
    std::string _syntaxMessage;
    uint32_t _maxLeftColumnSize;
    bool _defaultsSet;
    std::vector<Configurable*> _configurables;

    ProgramOptions(const ProgramOptions&);
    ProgramOptions& operator=(const ProgramOptions&);

public:
    /**
     * If using empty constructor, setCommandLineArguments() must be called
     * before parse() and writeSyntaxPage().
     */
    ProgramOptions();
    ProgramOptions(int argc, const char* const* argv);
    virtual ~ProgramOptions();

    void addConfigurable(Configurable& c) {
        _configurables.push_back(&c);
        c.registerCommandLineOptions(
                *this, LifetimeToken::UP(new LifetimeToken(*this)));
    }

    void setCommandLineArguments(int argc, const char* const* argv);

    /**
     * In bool case, add an optional option that will be true if used.
     * In all other cases, adds a required option as there are no default.
     * Parsing will fail if required option is not set.
     */
    template<typename Type>
    OptionParser& addOption(const std::string& optionNameList,
                   Type& value,
                   const std::string& description);

    /** Add an optional option. Default value will be used if not set. */
    template<typename Type>
    OptionParser& addOption(const std::string& optionNameList,
                   Type& value,
                   const Type& defaultValue,
                   const std::string& description);

    template<typename Type>
    OptionParser& addArgument(const std::string& optionNameList,
                              Type& value,
                              const std::string& description);

    template<typename Type>
    OptionParser& addArgument(const std::string& optionNameList,
                              Type& value,
                              const Type& defaultValue,
                              const std::string& description);

    template<typename Type>
    OptionParser& addListArgument(const std::string& optionNameList,
                                  std::vector<Type>& value,
                                  const std::string& description);

    OptionParser& getOptionParser(const std::string& id);
    OptionParser& getArgumentParser(uint32_t argIndex);

    void addHiddenIdentifiers(const std::string& optionNameList);
    void setArgumentTypeName(const std::string& type, uint32_t index = 0);

    void addOptionHeader(const std::string& description);

    void setSyntaxPageMaxLeftColumnSize(uint32_t cols)
        { _maxLeftColumnSize = cols; }

    /**
     * Parses the command line arguments. Enable vespa debug logging if you want
     * to see details.
     *
     * @throws InvalidCommandLineArgumentsException on any failures.
     */
    void parse();

    /** Writes a syntax page to fit an 80 column screen. */
    void writeSyntaxPage(std::ostream& out, bool showDefaults = true);

    /** Sets some textual description added to syntax page. */
    void setSyntaxMessage(const std::string& msg);

    /**
     * Can be used after having added all the options to initialize all the
     * parameters to default values. Useful if you want to set defaults first,
     * override defaults with config of some kind, and then parse, such that
     * command line parameters override config.
     */
    void setDefaults() { setDefaults(false); }

    /**
     * Useful to clear out all options before shutdown if this class outlives
     * a class defining options.
     */
    void clear();

private:
    void parseOption(const std::string& id, OptionParser&, uint32_t& argPos);
    void parseArgument(OptionParser& opt, uint32_t& pos);
    OptionParser& addOption(std::shared_ptr<OptionParser> && opt);
    OptionParser& addArgument(std::shared_ptr<OptionParser> arg);
    void setDefaults(bool failUnsetRequired);

    struct OptionHeader;
    template<typename Number> struct NumberOptionParser;
    struct BoolOptionParser;
    struct FlagOptionParser;
    struct StringOptionParser;
    struct MapOptionParser;
    template<typename T> struct ListOptionParser;

};

// ----------------------------------------------------------------------------

// Implementation of templates and inner classes
// Not a part of the public interface

template<typename T> const char* getTypeName();
template<> inline const char* getTypeName<int32_t>() { return "int"; }
template<> inline const char* getTypeName<uint32_t>() { return "uint"; }
template<> inline const char* getTypeName<int64_t>() { return "long"; }
template<> inline const char* getTypeName<uint64_t>() { return "ulong"; }
template<> inline const char* getTypeName<float>() { return "float"; }
template<> inline const char* getTypeName<double>() { return "double"; }

struct ProgramOptions::OptionParser {
    typedef std::unique_ptr<OptionParser> UP;
    typedef std::shared_ptr<OptionParser> SP;

    std::vector<std::string> _names;
    std::vector<std::string> _hiddenNames;
    uint32_t _argCount;
    std::vector<std::string> _argTypes;
    bool _hasDefault;
    bool _invalidDefault;
    std::string _defaultString;
    std::string _description;

    OptionParser(const std::string& nameList, uint32_t argCount,
                 const std::string& desc);
    OptionParser(const std::string& nameList, uint32_t argCount,
                 const std::string& defString, const std::string& desc);
    virtual ~OptionParser();

    virtual bool isRequired() const { return !_hasDefault; }
    virtual void set(const std::vector<std::string>& arguments) = 0;
    virtual void setDefault() = 0;
    virtual void setInvalidDefault();
    virtual std::string getArgType(uint32_t /* index */) const { return "val"; }
    std::string getOptSyntaxString() const;
    std::string getArgName() const {
        std::string name = _names[0];
        for (uint32_t i=1; i<_names.size(); ++i) name += " " + _names[i];
        return name;
    }
    virtual bool isHeader() const { return false; }
    virtual bool hideFromSyntaxPage() const
        { return !isHeader() && _names.empty(); }
};

struct ProgramOptions::OptionHeader : public OptionParser {
    OptionHeader(const std::string& desc) : OptionParser("", 0, desc) {}
    void set(const std::vector<std::string>&) override {}
    void setDefault() override {}
    bool isHeader() const override { return true; }
};

template<typename Number>
struct ProgramOptions::NumberOptionParser : public OptionParser {
    Number& _number;
    Number _defaultValue;

    std::string getStringValue(Number n);

    NumberOptionParser(const std::string& nameList, Number& number,
            const std::string& description)
        : OptionParser(nameList, 1, description),
          _number(number),
          _defaultValue(number)
    {
    }

    NumberOptionParser(const std::string& nameList, Number& number,
            const Number& defValue, const std::string& desc)
        : OptionParser(nameList, 1, getStringValue(defValue), desc),
          _number(number),
          _defaultValue(defValue)
    {}

    void set(const std::vector<std::string>& arguments) override;
    void setDefault() override { _number = _defaultValue; }
    std::string getArgType(uint32_t /* index */) const override {
        return getTypeName<Number>();
    }
};

struct ProgramOptions::BoolOptionParser : public OptionParser {
    bool& _value;
    bool _defaultValue;

    BoolOptionParser(const std::string& nameList, bool& value, const std::string& description);
    void set(const std::vector<std::string>&) override { _value = true; }
    void setDefault() override { _value = false; }
};

struct ProgramOptions::FlagOptionParser : public OptionParser {
    bool& _value;
    bool _unsetValue;

    FlagOptionParser(const std::string& nameList, bool& value, const std::string& description);
    FlagOptionParser(const std::string& nameList, bool& value, const bool& unsetValue, const std::string& description);
    void set(const std::vector<std::string>&) override { _value = !_unsetValue; }
    void setDefault() override { _value = _unsetValue; }
};

struct ProgramOptions::StringOptionParser : public OptionParser {
    std::string& _value;
    std::string _defaultValue;

    StringOptionParser(const std::string& nameList, std::string& value, const std::string& description);
    StringOptionParser(const std::string& nameList, std::string& value,
                       const std::string& defVal, const std::string& desc);

    void set(const std::vector<std::string>& arguments) override { _value = arguments[0]; }
    void setDefault() override { _value = _defaultValue; }
    std::string getArgType(uint32_t /* index */) const override { return "string"; }
};

struct ProgramOptions::MapOptionParser : public OptionParser {
    typedef std::map<std::string, std::string> MapType;
    std::map<std::string, std::string>& _value;

    MapOptionParser(const std::string& nameList,
                    std::map<std::string, std::string>& value,
                    const std::string& description);

    void set(const std::vector<std::string>& arguments) override {
        _value[arguments[0]] = arguments[1];
    }

    std::string getArgType(uint32_t /* index */) const override { return "string"; }

    // Default of map is just an empty map.
    void setDefault() override { _value.clear(); }
};

template<typename T>
struct ProgramOptions::ListOptionParser : public OptionParser {
    std::vector<T>& _value;
    T _singleValue;
    OptionParser::UP _entryParser;

    ListOptionParser(const std::string& nameList,
                     std::vector<T>& value,
                     const std::string& description)
        : OptionParser(nameList, 0, description),
          _value(value)
    {
    }

    T& getSingleValue() { return _singleValue; }

    void setEntryParser(OptionParser::UP entryParser) {
        _entryParser = std::move(entryParser);
    }
    bool isRequired() const override { return false; }
    void set(const std::vector<std::string>& arguments) override {
        for (uint32_t i=0; i<arguments.size(); ++i) {
            std::vector<std::string> v;
            v.push_back(arguments[i]);
            _entryParser->set(v);
            _value.push_back(_singleValue);
        }
    }
    void setDefault() override {
        _value.clear();
    }
    std::string getArgType(uint32_t index) const override {
        return _entryParser->getArgType(index) + "[]";
    }
};

} // vespalib

