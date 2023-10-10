// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "featurenameparser.h"
#include "featurenamebuilder.h"
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".fef.featurenameparser");

namespace {

//-----------------------------------------------------------------------------

int decodeHex(char c) {
    if (c >= '0' && c <= '9') {
        return (c - '0');
    }
    if (c >= 'a' && c <= 'f') {
        return ((c - 'a') + 10);
    }
    if (c >= 'A' && c <= 'F') {
        return ((c - 'A') + 10);
    }
    return -1;
}

//-----------------------------------------------------------------------------

template <typename A>
class IsLogged
{
private:
    A           _a;
    vespalib::string _name;

public:
    IsLogged(A a) : _a(a), _name(a.getName()) {}
    bool operator()(char c) {
        bool res = _a(c);
        LOG(info, "%s returned %s for char '%c'",
            _name.c_str(), res ? "true" : "false", c);
        return res;
    }
};

template <typename A>
class DoLog
{
private:
    A           _a;
    vespalib::string _name;

public:
    DoLog(A a) : _a(a), _name(a.getName()) {}
    bool operator()(char c) {
        bool res = _a(c);
        LOG(info, "%s returned %s for char '%c'",
            _name.c_str(), res ? "true" : "false", c);
        return res;
    }
    bool done() {
        bool res = _a.done();
        LOG(info, "%s returned %s on done signal",
            _name.c_str(), res ? "true" : "false");
        return res;
    }
};

//-----------------------------------------------------------------------------

template <typename A>
IsLogged<A> isLogged(A a) {
    return IsLogged<A>(a);
}

template <typename A>
DoLog<A> doLog(A a) {
    return DoLog<A>(a);
}

//-----------------------------------------------------------------------------

class ParseContext
{
private:
    const vespalib::string &_str;   // the input string
    uint32_t           _pos;   // current position
    char               _curr;  // current character, 0 means eos
    bool               _error; // flag indicating whether we have a parse error

public:
    ParseContext(const vespalib::string &in) : _str(in), _pos(0),
                                          _curr((in.empty()) ? 0 : in[0]),
                                          _error(false) {}
    uint32_t pos() const { return _pos; }
    char get() const { return _curr; }
    bool eos() const { return !_curr; }
    bool signalError() {
        _curr = 0; // also signals eos
        _error = true;
        return false;
    }
    bool error() {
        return _error;
    }
    void next() {
        if (eos()) {
            return;
        }
        if (++_pos < _str.size()) {
            _curr = _str[_pos];
        } else {
            _curr = 0;
        }
    }
    bool eatChar(char c) {
        if (get() != c) {
            return false;
        }
        next();
        return true;
    }
    template <typename CHECK, typename SINK>
    bool scan(CHECK check, SINK sink) {
        while (!eos()) {
            if (!check(get())) {
                break;
            }
            if (!sink(get())) {
                signalError();
            }
            next();
        }
        if (!sink.done()) {
            signalError();
        }
        return !error();
    }
};

//-----------------------------------------------------------------------------

class IsSpace
{
public:
    bool operator()(char c) const {
        switch (c) {
        case ' ':
        case '\t':
        case '\n':
        case '\r':
        case '\f':
            return true;
        default:
            return false;
        }
    }
    vespalib::string getName() const { return "IsSpace"; }
};

class Ident
{
public:
    Ident() {
        for(size_t i(0), m(256); i < m; i++) { _valid[i] = false; }
        for(size_t i('a'), m('z'); i <= m; i++) { _valid[i] = true; }
        for(size_t i('A'), m('Z'); i <= m; i++) { _valid[i] = true; }
        for(size_t i('0'), m('9'); i <= m; i++) { _valid[i] = true; }
        _valid[uint8_t('_')] = true;
        _valid[uint8_t('+')] = true;
        _valid[uint8_t('-')] = true;
        _valid[uint8_t('$')] = true;
        _valid[uint8_t('@')] = true;
    }
    bool isValid(uint8_t c) {  return _valid[c]; }
private:
    bool _valid[256];
};

static Ident _G_ident;

class IsIdent
{
public:
    bool operator()(char c) const {
        return _G_ident.isValid(c);
    }
    vespalib::string getName() const { return "IsIdent"; }
};

class IsChar
{
private:
    char _c;

public:
    IsChar(char c) : _c(c) {}
    bool operator()(char c) const {
        return (c == _c);
    }
    vespalib::string getName() const { return vespalib::make_string("IsChar(%c)", _c); }
};

template <typename A>
class IsNot
{
private:
    A _a;

public:
    IsNot(A a) : _a(a) {}
    bool operator()(char c) {
        return !(_a(c));
    }
    vespalib::string getName() const { return vespalib::make_string("IsNot(%s)", _a.getName().c_str()); }
};

template <typename A, typename B>
class IsEither
{
private:
    A _a;
    B _b;

public:
    IsEither(A a, B b) : _a(a), _b(b) {}
    bool operator()(char c) {
        return (_a(c) || _b(c));
    }
    vespalib::string getName() const { return vespalib::make_string("IsEither(%s,%s)",
                                          _a.getName().c_str(), _b.getName().c_str()); }
};

class IsEndQuote
{
private:
    bool _escape;

public:
    IsEndQuote() : _escape(false) {}
    bool operator()(char c) {
        if (_escape) {
            _escape = false;
            return false;
        }
        if (c == '\\') {
            _escape = true;
            return false;
        }
        return (c == '"');
    }
    vespalib::string getName() const { return "IsEndQuote"; }
};

//-----------------------------------------------------------------------------

class DoIgnore
{
public:
    bool operator()(char) { return true; }
    bool done() { return true; }
    vespalib::string getName() const { return "doIgnore"; }
};

class DoSave
{
private:
    vespalib::string &_dst;

public:
    DoSave(vespalib::string &str) : _dst(str) {}
    bool operator()(char c) {
        _dst.push_back(c);
        return true;
    }
    bool done() { return !_dst.empty(); }
    vespalib::string getName() const { return "doSave"; }
};

class DoDequote
{
private:
    bool          _escape; // true means we are dequoting something
    int           _hex;    // how many hex numbers left to read
    unsigned char _c;      // save up hex decoded char here
    vespalib::string  &_dst;    // where to save the dequoted string

public:
    DoDequote(vespalib::string &str) : _escape(false), _hex(0), _c(0), _dst(str) {}
    bool operator()(char c) {
        if (_escape) {
            if (_hex > 0) {
                --_hex;
                int val = decodeHex(c);
                if (val < 0) {
                    return false;
                }
                _c |= ((val & 0xf) << (_hex * 4));
                if (_hex == 0) {
                    if (_c == 0) {
                        return false;
                    }
                    _dst.push_back(_c);
                    _escape = false;
                }
            } else {
                switch (c) {
                case '"':
                    _dst.push_back('\"');
                    _escape = false;
                    break;
                case '\\':
                    _dst.push_back('\\');
                    _escape = false;
                    break;
                case 't':
                    _dst.push_back('\t');
                    _escape = false;
                    break;
                case 'n':
                    _dst.push_back('\n');
                    _escape = false;
                    break;
                case 'r':
                    _dst.push_back('\r');
                    _escape = false;
                    break;
                case 'f':
                    _dst.push_back('\f');
                    _escape = false;
                    break;
                case 'x':
                    _hex = 2;
                    _c = 0;
                    break;
                default:
                    return false; // signal error
                }
            }
        } else {
            if (c == '\\') {
                _escape = true;
            } else {
                _dst.push_back(c); // normal case (no dequoting needed)
            }
        }
        return true;
    }
    bool done() { return !_escape; }
    vespalib::string getName() const { return "doDequote"; }
};

//-----------------------------------------------------------------------------

IsSpace isSpace() { return IsSpace(); }

IsIdent isIdent() { return IsIdent(); }

IsChar isChar(char c) { return IsChar(c); }

template <typename A>
IsNot<A> isNot(A a) {
    return IsNot<A>(a);
}

template <typename A, typename B>
IsEither<A, B> isEither(A a, B b) {
    return IsEither<A, B>(a, b);
}

IsEndQuote isEndQuote() { return IsEndQuote(); }

DoIgnore doIgnore() { return DoIgnore(); }

DoSave doSave(vespalib::string &str) { return DoSave(str); }

DoDequote doDequote(vespalib::string &str) { return DoDequote(str); }

//-----------------------------------------------------------------------------

// need forward declaration of this for recursive parsing
bool normalizeFeatureName(ParseContext &ctx, vespalib::string &name);

bool parseParameters(ParseContext &ctx, std::vector<vespalib::string> &parameters)
{
    ctx.scan(isSpace(), doIgnore());
    if (!ctx.eatChar('(')) {
        return true; // no parameters = ok
    }
    for (;;) {
        vespalib::string param;
        ctx.scan(isSpace(), doIgnore());
        switch (ctx.get()) {
        case ')':
        case ',':
            break;      // empty param
        case '"':       // parse param as quoted string
            ctx.next(); // eat opening '"'
            if (!ctx.scan(isNot(isEndQuote()), doDequote(param))) {
                return false;
            }
            if (!ctx.eatChar('"')) {   // missing end quote
                return ctx.signalError();
            }
            break;
        default:        // parse param as feature name
            if (!normalizeFeatureName(ctx, param)) {
                return false;
            }
            break;
        }
        parameters.push_back(param);
        ctx.scan(isSpace(), doIgnore());
        if (ctx.eatChar(')')) {         // done
            return true;
        } else if (!ctx.eatChar(',')) { // illegal param list
            return ctx.signalError();
        }
    }
}

bool parseOutput(ParseContext &ctx, vespalib::string &output)
{
    ctx.scan(isSpace(), doIgnore());
    if (!ctx.eatChar('.')) {
        return true; // output is optional
    }
    ctx.scan(isSpace(), doIgnore());
    return ctx.scan(isEither(isIdent(), isChar('.')), doSave(output));
}

bool parseFeatureName(ParseContext &ctx, vespalib::string &baseName,
                      std::vector<vespalib::string> &parameters, vespalib::string &output)
{
    return (ctx.scan(isIdent(), doSave(baseName)) &&
            parseParameters(ctx, parameters) &&
            parseOutput(ctx, output));
}

bool normalizeFeatureName(ParseContext &ctx, vespalib::string &name) {
    vespalib::string              baseName;
    std::vector<vespalib::string> params;
    vespalib::string              output;
    if (!parseFeatureName(ctx, baseName, params, output)) {
        return false;
    }
    search::fef::FeatureNameBuilder builder;
    builder.baseName(baseName);
    for (uint32_t i = 0; i < params.size(); ++i) {
        builder.parameter(params[i]);
    }
    builder.output(output);
    name = builder.buildName();
    return true;
}

} // namespace <unnamed>

namespace search {
namespace fef {

FeatureNameParser::FeatureNameParser(const string &input)
    : _valid(false),
      _endPos(0),
      _baseName(),
      _parameters(),
      _output(),
      _executorName(),
      _featureName()
{
    ParseContext ctx(input);
    ctx.scan(isSpace(), doIgnore());
    _valid = parseFeatureName(ctx, _baseName, _parameters, _output);
    ctx.scan(isSpace(), doIgnore());
    if (!ctx.eos()) {
        _valid = ctx.signalError();
    }
    _endPos = ctx.pos();
    if (_valid && ctx.eos()) {
        FeatureNameBuilder builder;
        builder.baseName(_baseName);
        for (uint32_t i = 0; i < _parameters.size(); ++i) {
            builder.parameter(_parameters[i]);
        }
        _executorName = builder.buildName();
        builder.output(_output);
        _featureName = builder.buildName();
    } else {
        _baseName = "";
        {
            StringVector tmp;
            _parameters.swap(tmp);
        }
        _output = "";
    }
}

FeatureNameParser::~FeatureNameParser() { }


} // namespace fef
} // namespace search
