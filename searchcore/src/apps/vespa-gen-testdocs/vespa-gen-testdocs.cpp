// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <sstream>
#include <openssl/evp.h>
#include <cassert>
#include <filesystem>
#include <getopt.h>
#include <limits>
#include <string>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("vespa-gen-testdocs");

using StringSet = vespalib::hash_set<std::string>;
using StringArray = std::vector<std::string>;
using namespace vespalib::alloc;
using std::string;

namespace {

struct EvpMdCtxDeleter {
    void operator()(EVP_MD_CTX* evp_md_ctx) const noexcept {
        EVP_MD_CTX_free(evp_md_ctx);
    }
};

using EvpMdCtxPtr = std::unique_ptr<EVP_MD_CTX, EvpMdCtxDeleter>;

}

void
usageHeader()
{
    using std::cerr;
    cerr << "vespa-gen-testdocs version 0.0\n\nUSAGE:\n";
}

string
prependBaseDir(const string &baseDir,
               const string &file)
{
    if (baseDir.empty() || baseDir == ".")
        return file;
    return baseDir + "/" + file;
}

std::vector<string>
splitArg(const string &arg)
{
    std::vector<string> argv;
    string::size_type pos = 0;
    for (;;) {
        auto found = arg.find(',', pos);
        if (found == string::npos) {
            break;
        }
        argv.emplace_back(arg.substr(pos, found - pos));
        pos = found + 1;
    }
    argv.emplace_back(arg.substr(pos, string::npos));
    return argv;
}

void
shafile(const string &baseDir, const string &file)
{
    unsigned char digest[EVP_MAX_MD_SIZE];
    unsigned int digest_len = 0;
    string fullFile(prependBaseDir(baseDir, file));
    FastOS_File f;
    std::ostringstream os;
    Alloc buf = Alloc::alloc_aligned(64_Ki, 0x1000);
    f.EnableDirectIO();
    bool openres = f.OpenReadOnly(fullFile.c_str());
    if (!openres) {
        LOG(error, "Could not open %s for sha256 checksum", fullFile.c_str());
        LOG_ABORT("should not be reached");
    }
    int64_t flen = f.getSize();
    int64_t remainder = flen;
    EvpMdCtxPtr md_ctx(EVP_MD_CTX_new());
    const EVP_MD* md = EVP_get_digestbyname("SHA256");
    EVP_DigestInit_ex(md_ctx.get(), md, nullptr);
    while (remainder > 0) {
        int64_t thistime =
            std::min(remainder, static_cast<int64_t>(buf.size()));
        f.ReadBuf(buf.get(), thistime);
        EVP_DigestUpdate(md_ctx.get(), buf.get(), thistime);
        remainder -= thistime;
    }
    EVP_DigestFinal_ex(md_ctx.get(), &digest[0], &digest_len);
    assert(digest_len > 0u && digest_len <= EVP_MAX_MD_SIZE);
    for (unsigned int i = 0; i < digest_len; ++i) {
        os.width(2);
        os.fill('0');
        os << std::hex << static_cast<unsigned int>(digest[i]);
    }
    LOG(info, "SHA256(%s)= %s", file.c_str(), os.str().c_str());
}

class StringGenerator
{
    vespalib::Rand48 &_rnd;

public:
    StringGenerator(vespalib::Rand48 &rnd);

    void rand_string(string &res, uint32_t minLen, uint32_t maxLen);

    void rand_unique_array(StringArray &res, uint32_t minLen, uint32_t maxLen, uint32_t size);
};


StringGenerator::StringGenerator(vespalib::Rand48 &rnd)
    : _rnd(rnd)
{
}


void
StringGenerator::rand_string(string &res,
                             uint32_t minLen,
                             uint32_t maxLen)
{
    uint32_t len = minLen + _rnd.lrand48() % (maxLen - minLen + 1);
    
    res.clear();
    for (uint32_t i = 0; i < len; ++i) {
        res += 'a' + _rnd.lrand48() % ('z' - 'a' + 1);
    }
}
    

void
StringGenerator::rand_unique_array(StringArray &res,
                                   uint32_t minLen,
                                   uint32_t maxLen,
                                   uint32_t size)
{
    StringSet set(size * 2);
    string s;
    
    res.reserve(size);
    for (uint32_t i = 0; i < size; ++i) {
        do {
            rand_string(s, minLen, maxLen);
        } while (!set.insert(s).second);
        assert(s.size() > 0);
        res.push_back(s);
    }
}


class FieldGenerator
{
public:
    using SP = std::shared_ptr<FieldGenerator>;

protected:
    const string _name;

public:
    FieldGenerator(const string &name);
    virtual ~FieldGenerator();
    virtual void setup();
    virtual void generateXML(vespalib::asciistream &doc, uint32_t id);
    virtual void generateJSON(vespalib::asciistream &doc, uint32_t id);
    virtual void generateValue(vespalib::asciistream &doc, uint32_t id);
    virtual bool isString() const { return true; }
};



FieldGenerator::FieldGenerator(const string &name)
    : _name(name)
{
}

FieldGenerator::~FieldGenerator()
{
}

void
FieldGenerator::setup()
{
}

void
FieldGenerator::generateXML(vespalib::asciistream &doc, uint32_t id)
{
    doc << "  <" << _name << ">";
    generateValue(doc, id);
    doc << "</" << _name << ">\n";
}

void
FieldGenerator::generateJSON(vespalib::asciistream &doc, uint32_t id)
{
    doc << "\"" << _name << "\": ";
    bool needQuote = isString();
    if (needQuote) {
        doc << "\"";
    }
    generateValue(doc, id);
    if (needQuote) {
        doc << "\"";
    }
}

void
FieldGenerator::generateValue(vespalib::asciistream &, uint32_t)
{
}

class ConstTextFieldGenerator : public FieldGenerator
{
    string _value;
public:
    ConstTextFieldGenerator(std::vector<string> argv) noexcept;
    ~ConstTextFieldGenerator() override;
    void generateValue(vespalib::asciistream &doc, uint32_t id) override;
};

ConstTextFieldGenerator::ConstTextFieldGenerator(std::vector<string> argv) noexcept
    : FieldGenerator(argv[0]),
      _value()
{
    if (argv.size() > 1) {
        _value = argv[1];
    }
}

ConstTextFieldGenerator::~ConstTextFieldGenerator() = default;

void
ConstTextFieldGenerator::generateValue(vespalib::asciistream &doc, uint32_t)
{
    doc << _value;
}

class PrefixTextFieldGenerator : public FieldGenerator
{
    string _prefix;
    uint32_t _mod;
    uint32_t _div;
public:
    PrefixTextFieldGenerator(std::vector<string> argv) noexcept;
    ~PrefixTextFieldGenerator() override;
    void generateValue(vespalib::asciistream &doc, uint32_t id) override;
};

PrefixTextFieldGenerator::PrefixTextFieldGenerator(std::vector<string> argv) noexcept
    : FieldGenerator(argv[0]),
      _prefix(),
      _mod(std::numeric_limits<uint32_t>::max()),
      _div(1u)
{
    if (argv.size() > 1) {
        _prefix = argv[1];
        if (argv.size() > 2) {
            if (!argv[2].empty()) {
                _mod = atol(argv[2].c_str());
            }
            if (argv.size() > 3) {
                if (!argv[3].empty()) {
                    _div = atol(argv[3].c_str());
                }
            }
        }
    }
}

PrefixTextFieldGenerator::~PrefixTextFieldGenerator() = default;

void
PrefixTextFieldGenerator::generateValue(vespalib::asciistream &doc, uint32_t id)
{
    doc << _prefix << ((id / _div) % _mod);
}

class RandTextFieldGenerator : public FieldGenerator
{
    vespalib::Rand48 &_rnd;
    uint32_t _numWords;
    StringArray _strings;
    uint32_t _minFill;
    uint32_t _randFill;

public:
    RandTextFieldGenerator(const string &name,
                           vespalib::Rand48 &rnd,
                           uint32_t numWords,
                           uint32_t minFill,
                           uint32_t maxFill);
    virtual ~RandTextFieldGenerator();
    void setup() override;
    void generateValue(vespalib::asciistream &doc, uint32_t id) override;
};


RandTextFieldGenerator::RandTextFieldGenerator(const string &name,
                                               vespalib::Rand48 &rnd,
                                               uint32_t numWords,
                                               uint32_t minFill,
                                               uint32_t randFill)
    : FieldGenerator(name),
      _rnd(rnd),
      _numWords(numWords),
      _strings(),
      _minFill(minFill),
      _randFill(randFill)
{
}



RandTextFieldGenerator::~RandTextFieldGenerator()
{
}


void
RandTextFieldGenerator::setup()
{
    LOG(info,
        "generating dictionary for field %s (%u words)",
        _name.c_str(), _numWords);
    StringGenerator(_rnd).rand_unique_array(_strings, 5, 10, _numWords);
}


void
RandTextFieldGenerator::generateValue(vespalib::asciistream &doc, uint32_t)
{
    uint32_t gLen = _minFill + _rnd.lrand48() % (_randFill + 1);
    bool first = true;
    for (uint32_t i = 0; i < gLen; ++i) {
        if (!first)
            doc << " ";
        first = false;
        uint32_t wNum = _rnd.lrand48() % _strings.size();
        const string &s(_strings[wNum]);
        assert(s.size() > 0);
        doc << s;
    }
}

class ModTextFieldGenerator : public FieldGenerator
{
    std::vector<uint32_t> _mods;

public:
    ModTextFieldGenerator(const string &name,
                          vespalib::Rand48 &rnd,
                          const std::vector<uint32_t> &mods);
    virtual ~ModTextFieldGenerator();
    void generateValue(vespalib::asciistream &doc, uint32_t id) override;
};


ModTextFieldGenerator::ModTextFieldGenerator(const string &name,
                                             [[maybe_unused]] vespalib::Rand48 &rnd,
                                             const std::vector<uint32_t> &mods)
    : FieldGenerator(name),
      _mods(mods)
{
}
    

ModTextFieldGenerator::~ModTextFieldGenerator()
{
}


void
ModTextFieldGenerator::generateValue(vespalib::asciistream &doc, uint32_t id)
{
    using MI = std::vector<uint32_t>::const_iterator;
    bool first = true;
    for (MI mi(_mods.begin()), me(_mods.end()); mi != me; ++mi) {
        uint32_t m = *mi;
        if (!first)
            doc << " ";
        first = false;
        doc << "w" << m << "w" << (id % m);
    }
}


class IdTextFieldGenerator : public FieldGenerator
{
public:
    IdTextFieldGenerator(const string &name);
    virtual ~IdTextFieldGenerator();
    void generateValue(vespalib::asciistream &doc, uint32_t id) override;
};


IdTextFieldGenerator::IdTextFieldGenerator(const string &name)
    : FieldGenerator(name)
{
}
    

IdTextFieldGenerator::~IdTextFieldGenerator()
{
}


void
IdTextFieldGenerator::generateValue(vespalib::asciistream &doc, uint32_t id)
{
    doc << id;
}


class RandIntFieldGenerator : public FieldGenerator
{
    vespalib::Rand48 &_rnd;
    uint32_t _low;
    uint32_t _count;

public:
    RandIntFieldGenerator(const string &name,
                          vespalib::Rand48 &rnd,
                          uint32_t low,
                          uint32_t count);
    virtual ~RandIntFieldGenerator();
    void generateValue(vespalib::asciistream &doc, uint32_t id) override;
    bool isString() const override { return false; }
};



RandIntFieldGenerator::RandIntFieldGenerator(const string &name,
                                             vespalib::Rand48 &rnd,
                                             uint32_t low,
                                             uint32_t count)
    : FieldGenerator(name),
      _rnd(rnd),
      _low(low),
      _count(count)
{
}
    

RandIntFieldGenerator::~RandIntFieldGenerator()
{
}


void
RandIntFieldGenerator::generateValue(vespalib::asciistream &doc, uint32_t)
{
    uint32_t r = _low + _rnd.lrand48() % _count;
    doc << r;
}

class DocumentGenerator
{
    string _docType;
    string _idPrefix;
    vespalib::asciistream _doc;
    using FieldVec = std::vector<FieldGenerator::SP>;
    const FieldVec _fields;
 
    void
    setup();
public:   
    DocumentGenerator(const string &docType,
                      const string &idPrefix,
                      const FieldVec &fields);
    ~DocumentGenerator();
    void generateXML(uint32_t id);
    void generateJSON(uint32_t id);
    void generate(uint32_t docMin, uint32_t docIdLimit,
                  const string &baseDir,
                  const string &feedFileName,
                  bool headers, bool json);
};


DocumentGenerator::DocumentGenerator(const string &docType,
                                     const string &idPrefix,
                                     const FieldVec &fields)
    : _docType(docType),
      _idPrefix(idPrefix),
      _doc(),
      _fields(fields)
{
    setup();
}


DocumentGenerator::~DocumentGenerator()
{
}

void
DocumentGenerator::setup()
{
    using FI = FieldVec::const_iterator;
    for (FI i(_fields.begin()), ie(_fields.end()); i != ie; ++i) {
        (*i)->setup();
    }
}


void
DocumentGenerator::generateXML(uint32_t id)
{
    _doc.clear();
    _doc << "<document documenttype=\"" << _docType << "\" documentid=\"" <<
        _idPrefix << id << "\">\n";
    for (const auto &field : _fields) {
        field->generateXML(_doc, id);
    }
    _doc << "</document>\n";
}

void
DocumentGenerator::generateJSON(uint32_t id)
{
    _doc.clear();
    _doc << "  { \"put\": \"" << _idPrefix << id << "\",\n    \"fields\": {";
    bool first = true;
    for (const auto &field : _fields) {
        if (!first) {
            _doc << ",";
        }
        first = false;
        _doc << "\n      ";
        field->generateJSON(_doc, id);
    }
    _doc << "\n    }\n  }";
}

void
DocumentGenerator::generate(uint32_t docMin, uint32_t docIdLimit,
                            const string &baseDir,
                            const string &feedFileName,
                            bool headers, bool json)
{
    string fullName(prependBaseDir(baseDir, feedFileName));
    std::filesystem::remove(std::filesystem::path(fullName));
    Fast_BufferedFile f;
    f.WriteOpen(fullName.c_str());
    if (json) {
        bool first = true;
        f.WriteString("[\n");
        for (uint32_t id = docMin; id < docIdLimit; ++id) {
            if (!first) {
                f.WriteString(",\n");
            }
            first = false;
            generateJSON(id);
            f.WriteBuf(_doc.c_str(), _doc.size());
        }
        f.WriteString("\n]\n");
    } else {
        if (headers) {
            f.WriteString("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
            f.WriteString("<vespafeed>\n");
        }
        for (uint32_t id = docMin; id < docIdLimit; ++id) {
            generateXML(id);
            f.WriteBuf(_doc.c_str(), _doc.size());
        }
        if (headers) {
            f.WriteString("</vespafeed>\n");
        }
    }
    f.Flush();
    bool close_ok = f.Close();
    assert(close_ok);
    LOG(info, "Calculating sha256 for %s", feedFileName.c_str());
    shafile(baseDir, feedFileName);
}


class SubApp
{
public:
    virtual void usage(bool showHeader) = 0;
    virtual bool getOptions(int argc, char **argv) = 0;
    virtual int run() = 0;
    virtual ~SubApp() = default;
};

class GenTestDocsApp : public SubApp
{
    string _baseDir;
    string _docType;
    uint32_t _minDocId;
    uint32_t _docIdLimit;
    bool _verbose;
    int _numWords;
    int _optIndex;
    std::vector<FieldGenerator::SP> _fields;
    std::vector<uint32_t> _mods;
    vespalib::Rand48 _rnd;
    string _outFile;
    bool _headers;
    bool _json;
    
public:
    GenTestDocsApp()
        : _baseDir(""),
          _docType("testdoc"),
          _minDocId(0u),
          _docIdLimit(5u),
          _verbose(false),
          _numWords(1000),
          _optIndex(1),
          _fields(),
          _mods(),
          _rnd(),
          _outFile(),
          _headers(false),
          _json(false)
    {
        _mods.push_back(2);
        _mods.push_back(3);
        _mods.push_back(5);
        _mods.push_back(7);
        _mods.push_back(11);
        _rnd.srand48(42);
    }

    ~GenTestDocsApp() override
    {
    }

    void usage(bool showHeader) override;
    bool getOptions(int argc, char **argv) override;
    int run() override;
};


void
GenTestDocsApp::usage(bool showHeader)
{
    using std::cerr;
    if (showHeader)
        usageHeader();
    cerr <<
        "vespa-gen-testdocs gentestdocs\n"
        " [--basedir basedir]\n"
        " [--consttextfield name]\n"
        " [--prefixtextfield name]\n"
        " [--randtextfield name]\n"
        " [--modtextfield name]\n"
        " [--idtextfield name]\n"
        " [--randintfield name]\n"
        " [--docidlimit docIdLimit]\n"
        " [--mindocid mindocid]\n"
        " [--numwords numWords]\n"
        " [--doctype docType]\n"
        " [--headers]\n"
        " [--json]\n"
        " outFile\n";
}

bool
GenTestDocsApp::getOptions(int argc, char **argv)
{
    int c;
    int longopt_index = 0;
    static struct option longopts[] = {
        { "basedir", 1, NULL, 0 },
        { "consttextfield", 1, NULL, 0 },
        { "prefixtextfield", 1, NULL, 0 },
        { "randtextfield", 1, NULL, 0 },
        { "modtextfield", 1, NULL, 0 },
        { "idtextfield", 1, NULL, 0 },
        { "randintfield", 1, NULL, 0 },
        { "docidlimit", 1, NULL, 0 },
        { "mindocid", 1, NULL, 0 },
        { "numwords", 1, NULL, 0 },
        { "doctype", 1, NULL, 0 },
        { "headers", 0, NULL, 0 }, 
        { "json", 0, NULL, 0 },
        { NULL, 0, NULL, 0 }
    };
    enum longopts_enum {
        LONGOPT_BASEDIR,
        LONGOPT_CONSTTEXTFIELD,
        LONGOPT_PREFIXTEXTFIELD,
        LONGOPT_RANDTEXTFIELD,
        LONGOPT_MODTEXTFIELD,
        LONGOPT_IDTEXTFIELD,
        LONGOPT_RANDINTFIELD,
        LONGOPT_DOCIDLIMIT,
        LONGOPT_MINDOCID,
        LONGOPT_NUMWORDS,
        LONGOPT_DOCTYPE,
        LONGOPT_HEADERS,
        LONGOPT_JSON
    };
    optind = 2;
    while ((c = getopt_long(argc, argv, "v",
                            longopts,
                            &longopt_index)) != -1) {
        FieldGenerator::SP g;
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_BASEDIR:
                _baseDir = optarg;
                break;
            case LONGOPT_CONSTTEXTFIELD:
                _fields.emplace_back(std::make_shared<ConstTextFieldGenerator>(splitArg(optarg)));
                break;
            case LONGOPT_PREFIXTEXTFIELD:
                _fields.emplace_back(std::make_shared<PrefixTextFieldGenerator>(splitArg(optarg)));
                break;
            case LONGOPT_RANDTEXTFIELD:
                g.reset(new RandTextFieldGenerator(optarg,
                                                   _rnd,
                                                   _numWords,
                                                   20,
                                                   50));
                _fields.push_back(g);
                break;
            case LONGOPT_MODTEXTFIELD:
                g.reset(new ModTextFieldGenerator(optarg,
                                                  _rnd,
                                                  _mods));
                _fields.push_back(g);
                break;
            case LONGOPT_IDTEXTFIELD:
                g.reset(new IdTextFieldGenerator(optarg));
                _fields.push_back(g);
                break;
            case LONGOPT_RANDINTFIELD:
                g.reset(new RandIntFieldGenerator(optarg,
                                                  _rnd,
                                                  0,
                                                  100000));
                _fields.push_back(g);
                break;
            case LONGOPT_DOCIDLIMIT:
                _docIdLimit = atoi(optarg);
                break;
            case LONGOPT_MINDOCID:
                _minDocId = atoi(optarg);
                break;
            case LONGOPT_NUMWORDS:
                _numWords = atoi(optarg);
                break;
            case LONGOPT_DOCTYPE:
                _docType = optarg;
                break;
            case LONGOPT_HEADERS:
                _headers = true;
                break;
            case LONGOPT_JSON:
                _json = true;
                break;
            default:
                if (optarg != NULL) {
                    LOG(error,
                        "longopt %s with arg %s",
                        longopts[longopt_index].name, optarg);
                } else {
                    LOG(error,
                        "longopt %s",
                        longopts[longopt_index].name);
                }
            }
            break;
        case 'v':
            _verbose = true;
            break;
        default:
            return false;
        }
    }
    _optIndex = optind;
    if (_optIndex >= argc) {
        return false;
    }
    _outFile = argv[optind];
    return true;
}


int
GenTestDocsApp::run()
{
    printf("Hello world\n");
    string idPrefix("id:test:");
    idPrefix += _docType;
    idPrefix += "::";
    DocumentGenerator dg(_docType,
                         idPrefix,
                         _fields);
    LOG(info, "generating %s", _outFile.c_str());
    dg.generate(_minDocId, _docIdLimit, _baseDir, _outFile, _headers, _json);
    LOG(info, "done");
    return 0;
}


class App
{
public:
    void usage();
    int main(int argc, char **argv);
};


void
App::usage()
{
    GenTestDocsApp().usage(true);
}

int
App::main(int argc, char **argv)
{
    if (argc < 2) {
        usage();
        return 1;
    }
    std::unique_ptr<SubApp> subApp;
    if (strcmp(argv[1], "gentestdocs") == 0)
        subApp = std::make_unique<GenTestDocsApp>();
    if (subApp.get() != nullptr) {
        if (!subApp->getOptions(argc, argv)) {
            subApp->usage(true);
            return 1;
        }
        return subApp->run();
    }
    usage();
    return 1;
}


int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
