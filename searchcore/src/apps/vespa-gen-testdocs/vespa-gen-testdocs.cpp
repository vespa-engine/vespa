// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("vespa-gen-testdocs");
#include <algorithm>
#include <string>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <iostream>
#include <openssl/sha.h>

typedef vespalib::hash_set<vespalib::string> StringSet;
typedef vespalib::hash_set<uint32_t> UIntSet;
typedef std::vector<vespalib::string> StringArray;
typedef std::shared_ptr<StringArray> StringArraySP;
using namespace vespalib::alloc;
using vespalib::string;

void
usageHeader(void)
{
    using std::cerr;
    cerr <<
        "vespa-gen-testdocs version 0.0\n"
        "\n"
        "USAGE:\n";
}

string
prependBaseDir(const string &baseDir,
               const string &file)
{
    if (baseDir.empty() || baseDir == ".")
        return file;
    return baseDir + "/" + file;
}


void
shafile(const string &baseDir,
        const string &file)
{
    unsigned char digest[SHA256_DIGEST_LENGTH];
    SHA256_CTX c; 
    string fullFile(prependBaseDir(baseDir, file));
    FastOS_File f;
    std::ostringstream os;
    Alloc buf = Alloc::alloc(65536, MemoryAllocator::HUGEPAGE_SIZE, 0x1000);
    f.EnableDirectIO();
    bool openres = f.OpenReadOnly(fullFile.c_str());
    if (!openres) {
        LOG(error, "Could not open %s for sha256 checksum", fullFile.c_str());
        abort();
    }
    int64_t flen = f.GetSize();
    int64_t remainder = flen;
    SHA256_Init(&c);
    while (remainder > 0) {
        int64_t thistime =
            std::min(remainder, static_cast<int64_t>(buf.size()));
        f.ReadBuf(buf.get(), thistime);
        SHA256_Update(&c, buf.get(), thistime);
        remainder -= thistime;
    }
    f.Close();
    SHA256_Final(digest, &c);
    for (unsigned int i = 0; i < SHA256_DIGEST_LENGTH; ++i) {
        os.width(2);
        os.fill('0');
        os << std::hex << static_cast<unsigned int>(digest[i]);
    }
    LOG(info,
        "SHA256(%s)= %s",
        file.c_str(),
        os.str().c_str());
}

class StringGenerator
{
    search::Rand48 &_rnd;

public:
    StringGenerator(search::Rand48 &rnd);

    void
    rand_string(string &res, uint32_t minLen, uint32_t maxLen);

    void
    rand_unique_array(StringArray &res,
                      uint32_t minLen,
                      uint32_t maxLen,
                      uint32_t size);
};


StringGenerator::StringGenerator(search::Rand48 &rnd)
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
        res.append('a' + _rnd.lrand48() % ('z' - 'a' + 1));
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
    typedef std::shared_ptr<FieldGenerator> SP;

protected:
    const string _name;

public:
    FieldGenerator(const string &name);

    virtual
    ~FieldGenerator(void);

    virtual void
    setup(void);

    virtual void
    clear(void);

    virtual void
    deleteHistogram(const string &baseDir,
                    const string &name);

    virtual void
    writeHistogram(const string &baseDir,
                   const string &name);

    virtual void
    generate(vespalib::asciistream &doc, uint32_t id) = 0;
};



FieldGenerator::FieldGenerator(const string &name)
    : _name(name)
{
}

FieldGenerator::~FieldGenerator(void)
{
}


void
FieldGenerator::setup(void)
{
}


void
FieldGenerator::clear(void)
{
}


void
FieldGenerator::deleteHistogram(const string &baseDir,
                                const string &name)
{
    (void) baseDir;
    (void) name;
}


void
FieldGenerator::writeHistogram(const string &baseDir,
                               const string &name)
{
    (void) baseDir;
    (void) name;
}


class RandTextFieldGenerator : public FieldGenerator
{
    search::Rand48 &_rnd;
    uint32_t _numWords;
    StringArray _strings;
    std::vector<uint32_t> _histogram;
    UIntSet _wnums;
    uint32_t _colls;
    uint32_t _minFill;
    uint32_t _randFill;

public:
    RandTextFieldGenerator(const string &name,
                           search::Rand48 &rnd,
                           uint32_t numWords,
                           uint32_t minFill,
                           uint32_t maxFill);

    virtual
    ~RandTextFieldGenerator(void);

    virtual void
    setup(void) override;

    virtual void
    clear(void) override;

    virtual void
    deleteHistogram(const string &baseDir, const string &name) override;

    virtual void
    writeHistogram(const string &baseDir, const string &name) override;

    virtual void
    generate(vespalib::asciistream &doc, uint32_t id) override;
};


RandTextFieldGenerator::RandTextFieldGenerator(const string &name,
                                               search::Rand48 &rnd,
                                               uint32_t numWords,
                                               uint32_t minFill,
                                               uint32_t randFill)
    : FieldGenerator(name),
      _rnd(rnd),
      _numWords(numWords),
      _strings(),
      _histogram(),
      _wnums(),
      _colls(0u),
      _minFill(minFill),
      _randFill(randFill)
{
}



RandTextFieldGenerator::~RandTextFieldGenerator(void)
{
}


void
RandTextFieldGenerator::setup(void)
{
    LOG(info,
        "generating dictionary for field %s (%u words)",
        _name.c_str(), _numWords);
    StringGenerator(_rnd).rand_unique_array(_strings, 5, 10, _numWords);
    _histogram.resize(_numWords);
}


void
RandTextFieldGenerator::clear(void)
{
    typedef std::vector<uint32_t>::iterator HI;
    for (HI i(_histogram.begin()), ie(_histogram.end()); i != ie; ++i) {
        *i = 0;
    }
    _colls = 0;
}


void
RandTextFieldGenerator::deleteHistogram(const string &baseDir,
                                        const string &name)
{
    string fname(prependBaseDir(baseDir, name) + "-" + _name);
    FastOS_File::Delete(fname.c_str());
}


void
RandTextFieldGenerator::writeHistogram(const string &baseDir,
                                       const string &name)
{
    LOG(info, "%u word collisions for field %s", _colls, _name.c_str());
    string fname(name + "-" + _name);
    string fullName(prependBaseDir(baseDir, fname));
    LOG(info, "Writing histogram %s", fname.c_str());
    Fast_BufferedFile f(new FastOS_File);
    f.WriteOpen(fullName.c_str());
    uint32_t numWords = _strings.size();
    assert(numWords == _histogram.size());
    for (uint32_t wNum = 0; wNum < numWords; ++wNum) {
        f.WriteString(_strings[wNum].c_str());
        f.WriteString(" ");
        f.addNum(_histogram[wNum], 0, ' ');
        f.WriteString("\n");
    }
    f.Flush();
    f.Close();
    shafile(baseDir, fname);
}


void
RandTextFieldGenerator::generate(vespalib::asciistream &doc, uint32_t id)
{
    (void) id;
    doc << "  <" << _name << ">";
    _wnums.clear();
    uint32_t gLen = _minFill + _rnd.lrand48() % (_randFill + 1);
    bool first = true;
    for (uint32_t i = 0; i < gLen; ++i) {
        if (!first)
            doc << " ";
        first = false;
        uint32_t wNum = _rnd.lrand48() % _strings.size();
        if (_wnums.insert(wNum).second)
            _histogram[wNum]++;
        else
            ++_colls;
        const string &s(_strings[wNum]);
        assert(s.size() > 0);
        doc << s;
    }
    doc << "</" << _name << ">\n";
}


class ModTextFieldGenerator : public FieldGenerator
{
    search::Rand48 &_rnd;
    std::vector<uint32_t> _mods;

public:
    ModTextFieldGenerator(const string &name,
                          search::Rand48 &rnd,
                          const std::vector<uint32_t> &mods);
    
    virtual
    ~ModTextFieldGenerator(void);

    virtual void
    clear(void) override;

    virtual void
    writeHistogram(const string &name);

    virtual void
    generate(vespalib::asciistream &doc, uint32_t id) override;
};


ModTextFieldGenerator::ModTextFieldGenerator(const string &name,
                                             search::Rand48 &rnd,
                                             const std::vector<uint32_t> &mods)
    : FieldGenerator(name),
      _rnd(rnd),
      _mods(mods)
{
}
    

ModTextFieldGenerator::~ModTextFieldGenerator(void)
{
}


void
ModTextFieldGenerator::clear(void)
{
}


void
ModTextFieldGenerator::writeHistogram(const string &name)
{
    (void) name;
}


void
ModTextFieldGenerator::generate(vespalib::asciistream &doc, uint32_t id)
{
    typedef std::vector<uint32_t>::const_iterator MI;
    doc << "  <" << _name << ">";
    bool first = true;
    for (MI mi(_mods.begin()), me(_mods.end()); mi != me; ++mi) {
        uint32_t m = *mi;
        if (!first)
            doc << " ";
        first = false;
        doc << "w" << m << "w" << (id % m);
    }
    doc << "</" << _name << ">\n";
}


class IdTextFieldGenerator : public FieldGenerator
{
public:
    IdTextFieldGenerator(const string &name);
    
    virtual
    ~IdTextFieldGenerator(void);

    virtual void
    clear(void) override;

    virtual void
    writeHistogram(const string &name);

    virtual void
    generate(vespalib::asciistream &doc, uint32_t id) override;
};


IdTextFieldGenerator::IdTextFieldGenerator(const string &name)
    : FieldGenerator(name)
{
}
    

IdTextFieldGenerator::~IdTextFieldGenerator(void)
{
}


void
IdTextFieldGenerator::clear(void)
{
}


void
IdTextFieldGenerator::writeHistogram(const string &name)
{
    (void) name;
}


void
IdTextFieldGenerator::generate(vespalib::asciistream &doc, uint32_t id)
{
    doc << "  <" << _name << ">";
    doc << id;
    doc << "</" << _name << ">\n";
}


class RandIntFieldGenerator : public FieldGenerator
{
    search::Rand48 &_rnd;
    uint32_t _low;
    uint32_t _count;

public:
    RandIntFieldGenerator(const string &name,
                          search::Rand48 &rnd,
                          uint32_t low,
                          uint32_t count);
    
    virtual
    ~RandIntFieldGenerator(void);

    virtual void
    clear(void) override;

    virtual void
    writeHistogram(const string &name);

    virtual void
    generate(vespalib::asciistream &doc, uint32_t id) override;
};



RandIntFieldGenerator::RandIntFieldGenerator(const string &name,
                                             search::Rand48 &rnd,
                                             uint32_t low,
                                             uint32_t count)
    : FieldGenerator(name),
      _rnd(rnd),
      _low(low),
      _count(count)
{
}
    

RandIntFieldGenerator::~RandIntFieldGenerator(void)
{
}


void
RandIntFieldGenerator::clear(void)
{
}


void
RandIntFieldGenerator::writeHistogram(const string &name)
{
    (void) name;
}


void
RandIntFieldGenerator::generate(vespalib::asciistream &doc, uint32_t id)
{
    (void) id;
    doc << "  <" << _name << ">";
    uint32_t r = _low + _rnd.lrand48() % _count;
    doc << r;
    doc << "</" << _name << ">\n";
}

class DocumentGenerator
{
    string _docType;
    string _idPrefix;
    vespalib::asciistream _doc;
    typedef std::vector<FieldGenerator::SP> FieldVec;
    const FieldVec _fields;
 
    void
    setup(void);
public:   
    DocumentGenerator(const string &docType,
                      const string &idPrefix,
                      const FieldVec &fields);

    ~DocumentGenerator(void);

    void
    clear(void);

    void
    deleteHistogram(const string &baseDir,
                    const string &name);

    void
    writeHistogram(const string &baseDir,
                   const string &name);

    void
    generate(uint32_t id);

    void
    generate(uint32_t docMin, uint32_t docCount,
             const string &baseDir,
             const string &feedFileName,
             bool headers);
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


DocumentGenerator::~DocumentGenerator(void)
{
}

void
DocumentGenerator::setup(void)
{
    typedef FieldVec::const_iterator FI;
    for (FI i(_fields.begin()), ie(_fields.end()); i != ie; ++i) {
        (*i)->setup();
    }
}


void
DocumentGenerator::clear(void)
{
    typedef FieldVec::const_iterator FI;
    for (FI i(_fields.begin()), ie(_fields.end()); i != ie; ++i) {
        (*i)->clear();
    }
}


void
DocumentGenerator::generate(uint32_t id)
{
    _doc.clear();
    _doc << "<document documenttype=\"" << _docType << "\" documentid= \"" <<
        _idPrefix << id << "\">\n";
    typedef FieldVec::const_iterator FI;
    for (FI i(_fields.begin()), ie(_fields.end()); i != ie; ++i) {
        (*i)->generate(_doc, id);
    }
    _doc << "</document>\n";
}


void
DocumentGenerator::deleteHistogram(const string &baseDir,
                                   const string &name)
{
    typedef FieldVec::const_iterator FI;
    for (FI i(_fields.begin()), ie(_fields.end()); i != ie; ++i) {
        (*i)->deleteHistogram(baseDir, name);
    }
}
                            
void
DocumentGenerator::writeHistogram(const string &baseDir,
                                  const string &name)
{
    typedef FieldVec::const_iterator FI;
    for (FI i(_fields.begin()), ie(_fields.end()); i != ie; ++i) {
        (*i)->writeHistogram(baseDir, name);
    }
}
                            
void
DocumentGenerator::generate(uint32_t docMin, uint32_t docCount,
                            const string &baseDir,
                            const string &feedFileName,
                            bool headers)
{
    string fullName(prependBaseDir(baseDir, feedFileName));
    FastOS_File::Delete(fullName.c_str());
    string histname(feedFileName + ".histogram");
    deleteHistogram(baseDir, histname);
    Fast_BufferedFile f(new FastOS_File);
    f.WriteOpen(fullName.c_str());
    clear();
    if (headers) {
        f.WriteString("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
        f.WriteString("<vespafeed>\n");
    }
    uint32_t docLim = docMin + docCount;
    for (uint32_t id = docMin; id < docLim; ++id) {
        generate(id);
        f.WriteBuf(_doc.c_str(), _doc.size());
    }
    if (headers) {
        f.WriteString("</vespafeed>\n");
    }
    f.Flush();
    f.Close();
    LOG(info, "Calculating sha256 for %s", feedFileName.c_str());
    shafile(baseDir, feedFileName);
    writeHistogram(baseDir, histname);
}


class SubApp
{
protected:
    FastOS_Application &_app;

public:
    SubApp(FastOS_Application &app)
        : _app(app)
    {
    }

    virtual
    ~SubApp(void)
    {
    }

    virtual void
    usage(bool showHeader) = 0;

    virtual bool
    getOptions(void) = 0;

    virtual int
    run(void) = 0;
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
    search::Rand48 _rnd;
    string _outFile;
    bool _headers;
    
public:
    GenTestDocsApp(FastOS_Application &app)
        : SubApp(app),
          _baseDir(""),
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
          _headers(false)
    {
        _mods.push_back(2);
        _mods.push_back(3);
        _mods.push_back(5);
        _mods.push_back(7);
        _mods.push_back(11);
        _rnd.srand48(42);
    }

    virtual
    ~GenTestDocsApp(void)
    {
    }

    virtual void
    usage(bool showHeader) override;

    virtual bool
    getOptions(void) override;

    virtual int
    run(void) override;
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
        " [--randtextfield name]\n"
        " [--modtextfield name]\n"
        " [--idtextfield name]\n"
        " [--randintfield name]\n"
        " [--docidlimit docIdLimit]\n"
        " [--mindocid mindocid]\n"
        " [--numwords numWords]\n"
        " [--doctype docType]\n"
        " [--headers]\n"
        " outFile\n";
}

bool
GenTestDocsApp::getOptions(void)
{
    int c;
    const char *optArgument = NULL;
    int longopt_index = 0;
    static struct option longopts[] = {
        { "basedir", 1, NULL, 0 },
        { "randtextfield", 1, NULL, 0 },
        { "modtextfield", 1, NULL, 0 },
        { "idtextfield", 1, NULL, 0 },
        { "randintfield", 1, NULL, 0 },
        { "docidlimit", 1, NULL, 0 },
        { "mindocid", 1, NULL, 0 },
        { "numwords", 1, NULL, 0 },
        { "doctype", 1, NULL, 0 },
        { "headers", 0, NULL, 0 }, 
        { NULL, 0, NULL, 0 }
    };
    enum longopts_enum {
        LONGOPT_BASEDIR,
        LONGOPT_RANDTEXTFIELD,
        LONGOPT_MODTEXTFIELD,
        LONGOPT_IDTEXTFIELD,
        LONGOPT_RANDINTFIELD,
        LONGOPT_DOCIDLIMIT,
        LONGOPT_MINDOCID,
        LONGOPT_NUMWORDS,
        LONGOPT_DOCTYPE,
        LONGOPT_HEADERS
    };
    int optIndex = 2;
    while ((c = _app.GetOptLong("v",
                                optArgument,
                                optIndex,
                                longopts,
                                &longopt_index)) != -1) {
        FieldGenerator::SP g;
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_BASEDIR:
                _baseDir = optArgument;
                break;
            case LONGOPT_RANDTEXTFIELD:
                g.reset(new RandTextFieldGenerator(optArgument,
                                                   _rnd,
                                                   _numWords,
                                                   20,
                                                   50));
                _fields.push_back(g);
                break;
            case LONGOPT_MODTEXTFIELD:
                g.reset(new ModTextFieldGenerator(optArgument,
                                                  _rnd,
                                                  _mods));
                _fields.push_back(g);
                break;
            case LONGOPT_IDTEXTFIELD:
                g.reset(new IdTextFieldGenerator(optArgument));
                _fields.push_back(g);
                break;
            case LONGOPT_RANDINTFIELD:
                g.reset(new RandIntFieldGenerator(optArgument,
                                                  _rnd,
                                                  0,
                                                  100000));
                _fields.push_back(g);
                break;
                break;
            case LONGOPT_DOCIDLIMIT:
                _docIdLimit = atoi(optArgument);
                break;
            case LONGOPT_MINDOCID:
                _minDocId = atoi(optArgument);
                break;
            case LONGOPT_NUMWORDS:
                _numWords = atoi(optArgument);
                break;
            case LONGOPT_DOCTYPE:
                _docType = optArgument;
                break;
            case LONGOPT_HEADERS:
                _headers = true;
                break;
            default:
                if (optArgument != NULL) {
                    LOG(error,
                        "longopt %s with arg %s",
                        longopts[longopt_index].name, optArgument);
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
    _optIndex = optIndex;
    if (_optIndex >= _app._argc) {
        return false;
    }
    _outFile = _app._argv[optIndex];
    return true;
}


int
GenTestDocsApp::run(void)
{
    printf("Hello world\n");
    string idPrefix("id:test:");
    idPrefix += _docType;
    idPrefix += "::";
    DocumentGenerator dg(_docType,
                         idPrefix,
                         _fields);
    LOG(info, "generating %s", _outFile.c_str());
    dg.generate(_minDocId, _docIdLimit, _baseDir, _outFile, _headers);
    LOG(info, "done");
    return 0;
}


class App : public FastOS_Application
{
public:
    void
    usage(void);

    int
    Main(void) override;
};


void
App::usage(void)
{
    GenTestDocsApp(*this).usage(true);
}

int
App::Main(void)
{
    if (_argc < 2) {
        usage();
        return 1;
    }
    std::unique_ptr<SubApp> subApp;
    if (strcmp(_argv[1], "gentestdocs") == 0)
        subApp.reset(new GenTestDocsApp(*this));
    if (subApp.get() != NULL) {
        if (!subApp->getOptions()) {
            subApp->usage(true);
            return 1;
        }
        return subApp->run();
    }
    usage();
    return 1;
}


int
main(int argc, char **argv)
{
    App app;
    return app.Entry(argc, argv);
}
