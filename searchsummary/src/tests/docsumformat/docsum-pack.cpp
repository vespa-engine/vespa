// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchsummary/docsummary/urlresult.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/docsummary/resultpacker.h>
#include <vespa/fastos/app.h>
#include <vespa/log/log.h>
LOG_SETUP("docsum-pack");

using namespace search::docsummary;


// needed to resolve external symbol from httpd.h on AIX
void FastS_block_usr2() {}


class MyApp : public FastOS_Application
{
private:
    bool               _rc;
    uint32_t           _cnt;
    search::docsummary::ResultConfig _config;
    search::docsummary::ResultPacker _packer;

public:
    MyApp();
    ~MyApp();

    // log test results
    void ReportTestResult(uint32_t line, bool rc);
    bool RTR(uint32_t line, bool rc)
    { ReportTestResult(line, rc); return rc; }

    // compare runtime info (,but ignore result class)
    bool Equal(search::docsummary::ResEntry *a, search::docsummary::ResEntry *b);
    bool Equal(search::docsummary::GeneralResult *a, search::docsummary::GeneralResult *b);

    void TestFieldIndex(uint32_t line, search::docsummary::GeneralResult *gres,
                        const char *field, int idx);

    void TestIntValue(uint32_t line, search::docsummary::GeneralResult *gres,
                      const char *field, uint32_t value);

    void TestDoubleValue(uint32_t line, search::docsummary::GeneralResult *gres,
                         const char *field, double value);

    void TestInt64Value(uint32_t line, search::docsummary::GeneralResult *gres,
                        const char *field, uint64_t value);

    void TestStringValue(uint32_t line, search::docsummary::GeneralResult *gres,
                         const char *field, const char *value);

    void TestDataValue(uint32_t line, search::docsummary::GeneralResult *gres,
                       const char *field, const char *value);

    void TestBasic();
    void TestFailLong();
    void TestFailShort();
    void TestFailOrder();
    void TestCompress();
    void TestCompat();
    void TestBasicInplace();
    void TestCompressInplace();

    int Main();
};

MyApp::MyApp()
    : _rc(false),
      _cnt(0u),
      _config(),
      _packer(&_config)
{}
MyApp::~MyApp() {}

void
MyApp::ReportTestResult(uint32_t line, bool rc)
{
    _cnt++;

    if (rc) {
        LOG(info, "Test case %d: SUCCESS", _cnt);
    } else {
        LOG(error, "Test case %d: FAIL (see %s:%d)", _cnt, __FILE__, line);
        _rc = false;
    }
}


bool
MyApp::Equal(search::docsummary::ResEntry *a, search::docsummary::ResEntry *b)
{
    if (a->_type != b->_type)
        return false;

    if (a->_intval != b->_intval)
        return false;

    if (a->_type != RES_INT &&
        memcmp(a->_pt, b->_pt, a->_intval) != 0)
        return false;

    return true;
}


bool
MyApp::Equal(search::docsummary::GeneralResult *a, search::docsummary::GeneralResult *b)
{
    uint32_t numEntries = a->GetClass()->GetNumEntries();

    if (b->GetClass()->GetNumEntries() != numEntries)
        return false;

    for (uint32_t i = 0; i < numEntries; i++) {

        if (!Equal(a->GetEntry(i), b->GetEntry(i)))
            return false;

        if (a->GetClass()->GetEntry(i)->_bindname != b->GetClass()->GetEntry(i)->_bindname)
            return false;
    }

    return true;
}


void
MyApp::TestFieldIndex(uint32_t line, search::docsummary::GeneralResult *gres,
                      const char *field, int idx)
{
    bool rc = (gres != NULL &&
               gres->GetClass()->GetIndexFromName(field) == idx);

    RTR(line, rc);
}


void
MyApp::TestIntValue(uint32_t line, search::docsummary::GeneralResult *gres,
                    const char *field, uint32_t value)
{
    search::docsummary::ResEntry *entry
        = (gres != NULL) ? gres->GetEntry(field) : NULL;

    bool rc = (entry != NULL &&
               entry->_type == RES_INT &&
               entry->_intval == value);

    RTR(line, rc);
}


void
MyApp::TestDoubleValue(uint32_t line, search::docsummary::GeneralResult *gres,
                       const char *field, double value)
{
    search::docsummary::ResEntry *entry
        = (gres != NULL) ? gres->GetEntry(field) : NULL;

    bool rc = (entry != NULL &&
               entry->_type == RES_DOUBLE &&
               entry->_doubleval == value);

    RTR(line, rc);
}


void
MyApp::TestInt64Value(uint32_t line, search::docsummary::GeneralResult *gres,
                      const char *field, uint64_t value)
{
    search::docsummary::ResEntry *entry
        = (gres != NULL) ? gres->GetEntry(field) : NULL;

    bool rc = (entry != NULL &&
               entry->_type == RES_INT64 &&
               entry->_int64val == value);

    RTR(line, rc);
}


void
MyApp::TestStringValue(uint32_t line, search::docsummary::GeneralResult *gres,
                       const char *field, const char *value)
{
    search::docsummary::ResEntry *entry
        = (gres != NULL) ? gres->GetEntry(field) : NULL;

    bool rc = (entry != NULL &&
               entry->_type == RES_STRING &&
               entry->_stringlen == strlen(value) &&
               strncmp(entry->_stringval, value, entry->_stringlen) == 0);

    if (!rc && entry != NULL) {
        LOG(warning,
            "string value '%.*s' != '%s'",
            (int) entry->_stringlen,
            entry->_stringval, value);
    }

    RTR(line, rc);
}


void
MyApp::TestDataValue(uint32_t line, search::docsummary::GeneralResult *gres,
                     const char *field, const char *value)
{
    search::docsummary::ResEntry *entry
        = (gres != NULL) ? gres->GetEntry(field) : NULL;

    bool rc = (entry != NULL &&
               entry->_type == RES_DATA &&
               entry->_datalen == strlen(value) &&
               strncmp(entry->_dataval, value, entry->_datalen) == 0);

    RTR(line, rc);
}


void
MyApp::TestBasic()
{
    const char *buf;
    uint32_t buflen;

    search::docsummary::urlresult     *res;
    search::docsummary::GeneralResult *gres;

    uint32_t intval     = 4;
    uint16_t shortval   = 2;
    uint8_t  byteval    = 1;
    float    floatval   = 4.5;
    double   doubleval  = 8.75;
    uint64_t int64val   = 8;
    const char *strval  = "This is a string";
    const char *datval  = "This is data";
    const char *lstrval = "This is a long string";
    const char *ldatval = "This is long data";

    RTR(__LINE__, _packer.Init(0));
    RTR(__LINE__, _packer.AddInteger(intval));
    RTR(__LINE__, _packer.AddShort(shortval));
    RTR(__LINE__, _packer.AddByte(byteval));
    RTR(__LINE__, _packer.AddFloat(floatval));
    RTR(__LINE__, _packer.AddDouble(doubleval));
    RTR(__LINE__, _packer.AddInt64(int64val));
    RTR(__LINE__, _packer.AddString(strval, strlen(strval)));
    RTR(__LINE__, _packer.AddData(datval, strlen(datval)));
    RTR(__LINE__, _packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, _packer.AddLongData(ldatval, strlen(ldatval)));
    RTR(__LINE__, _packer.GetDocsumBlob(&buf, &buflen));

    res = _config.Unpack(0, 0, 0, buf, buflen);
    gres = res->IsGeneral() ? (search::docsummary::GeneralResult *) res : NULL;

    RTR(__LINE__, gres != NULL);
    TestIntValue   (__LINE__, gres, "integer",    4);
    TestIntValue   (__LINE__, gres, "short",      2);
    TestIntValue   (__LINE__, gres, "byte",       1);
    TestDoubleValue(__LINE__, gres, "float",      floatval);
    TestDoubleValue(__LINE__, gres, "double",     doubleval);
    TestInt64Value (__LINE__, gres, "int64",      int64val);
    TestStringValue(__LINE__, gres, "string",     strval);
    TestDataValue  (__LINE__, gres, "data",       datval);
    TestStringValue(__LINE__, gres, "longstring", lstrval);
    TestDataValue  (__LINE__, gres, "longdata",   ldatval);
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetNumEntries() == 10));
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetClassID() == 0));
    delete res;
}


void
MyApp::TestFailLong()
{
    const char *buf;
    uint32_t buflen;

    uint32_t intval     = 4;
    uint16_t shortval   = 2;
    uint8_t  byteval    = 1;
    float    floatval   = 4.5;
    double   doubleval  = 8.75;
    uint64_t int64val   = 8;
    const char *strval  = "This is a string";
    const char *datval  = "This is data";
    const char *lstrval = "This is a long string";
    const char *ldatval = "This is long data";

    RTR(__LINE__, _packer.Init(0));
    RTR(__LINE__, _packer.AddInteger(intval));
    RTR(__LINE__, _packer.AddShort(shortval));
    RTR(__LINE__, _packer.AddByte(byteval));
    RTR(__LINE__, _packer.AddFloat(floatval));
    RTR(__LINE__, _packer.AddDouble(doubleval));
    RTR(__LINE__, _packer.AddInt64(int64val));
    RTR(__LINE__, _packer.AddString(strval, strlen(strval)));
    RTR(__LINE__, _packer.AddData(datval, strlen(datval)));
    RTR(__LINE__, _packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, _packer.AddLongData(ldatval, strlen(ldatval)));
    RTR(__LINE__, !_packer.AddByte(byteval));
    RTR(__LINE__, !_packer.GetDocsumBlob(&buf, &buflen));
}


void
MyApp::TestFailShort()
{
    const char *buf;
    uint32_t buflen;

    uint32_t intval     = 4;
    uint16_t shortval   = 2;
    uint8_t  byteval    = 1;
    float    floatval   = 4.5;
    double   doubleval  = 8.75;
    uint64_t int64val   = 8;
    const char *strval  = "This is a string";
    const char *datval  = "This is data";
    const char *lstrval = "This is a long string";

    RTR(__LINE__, _packer.Init(0));
    RTR(__LINE__, _packer.AddInteger(intval));
    RTR(__LINE__, _packer.AddShort(shortval));
    RTR(__LINE__, _packer.AddByte(byteval));
    RTR(__LINE__, _packer.AddFloat(floatval));
    RTR(__LINE__, _packer.AddDouble(doubleval));
    RTR(__LINE__, _packer.AddInt64(int64val));
    RTR(__LINE__, _packer.AddString(strval, strlen(strval)));
    RTR(__LINE__, _packer.AddData(datval, strlen(datval)));
    RTR(__LINE__, _packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, !_packer.GetDocsumBlob(&buf, &buflen));
}


void
MyApp::TestFailOrder()
{
    const char *buf;
    uint32_t buflen;

    uint32_t intval     = 4;
    uint16_t shortval   = 2;
    uint8_t  byteval    = 1;
    float    floatval   = 4.5;
    double   doubleval  = 8.75;
    uint64_t int64val   = 8;
    const char *strval  = "This is a string";
    const char *datval  = "This is data";
    const char *lstrval = "This is a long string";
    const char *ldatval = "This is long data";

    RTR(__LINE__, _packer.Init(0));
    RTR(__LINE__, _packer.AddInteger(intval));
    RTR(__LINE__, _packer.AddShort(shortval));
    RTR(__LINE__, !_packer.AddString(strval, strlen(strval)));
    RTR(__LINE__, !_packer.AddByte(byteval));
    RTR(__LINE__, !_packer.AddFloat(floatval));
    RTR(__LINE__, !_packer.AddDouble(doubleval));
    RTR(__LINE__, !_packer.AddInt64(int64val));
    RTR(__LINE__, !_packer.AddData(datval, strlen(datval)));
    RTR(__LINE__, !_packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, !_packer.AddLongData(ldatval, strlen(ldatval)));
    RTR(__LINE__, !_packer.GetDocsumBlob(&buf, &buflen));
}


void
MyApp::TestCompress()
{
    const char *buf;
    uint32_t buflen;

    search::docsummary::urlresult     *res;
    search::docsummary::GeneralResult *gres;

    const char *lstrval = "string string string";
    const char *ldatval = "data data data";

    RTR(__LINE__, _packer.Init(2));
    RTR(__LINE__, _packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, _packer.AddLongData(ldatval, strlen(ldatval)));
    RTR(__LINE__, _packer.GetDocsumBlob(&buf, &buflen));

    res = _config.Unpack(0, 0, 0, buf, buflen);
    gres = res->IsGeneral() ? (search::docsummary::GeneralResult *) res : NULL;

    RTR(__LINE__, gres != NULL);
    TestStringValue(__LINE__, gres, "text", lstrval);
    TestDataValue  (__LINE__, gres, "data", ldatval);
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetNumEntries() == 2));
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetClassID() == 2));
    delete res;
}


void
MyApp::TestCompat()
{
    const char *buf;
    uint32_t buflen;

    search::docsummary::urlresult     *res1;
    search::docsummary::GeneralResult *gres1;

    search::docsummary::urlresult     *res2;
    search::docsummary::GeneralResult *gres2;

    const char *strval = "string string string string";
    const char *datval = "data data data data";

    RTR(__LINE__, _packer.Init(1));
    RTR(__LINE__, _packer.AddData(strval, strlen(strval)));
    RTR(__LINE__, _packer.AddString(datval, strlen(datval)));
    RTR(__LINE__, _packer.GetDocsumBlob(&buf, &buflen));
    res1 = _config.Unpack(0, 0, 0, buf, buflen);
    gres1 = res1->IsGeneral() ? (search::docsummary::GeneralResult *) res1 : NULL;

    RTR(__LINE__, _packer.Init(2));
    RTR(__LINE__, _packer.AddLongData(strval, strlen(strval)));
    RTR(__LINE__, _packer.AddLongString(datval, strlen(datval)));
    RTR(__LINE__, _packer.GetDocsumBlob(&buf, &buflen));
    res2 = _config.Unpack(0, 0, 0, buf, buflen);
    gres2 = res2->IsGeneral() ? (search::docsummary::GeneralResult *) res2 : NULL;

    RTR(__LINE__, gres1 != NULL);
    RTR(__LINE__, gres2 != NULL);

    TestStringValue(__LINE__, gres1, "text", strval);
    TestDataValue  (__LINE__, gres1, "data", datval);
    TestFieldIndex (__LINE__, gres1, "text", 0);
    TestFieldIndex (__LINE__, gres1, "data", 1);
    RTR(__LINE__, (gres1 != NULL &&
                   gres1->GetClass()->GetNumEntries() == 2));

    TestStringValue(__LINE__, gres2, "text", strval);
    TestDataValue  (__LINE__, gres2, "data", datval);
    TestFieldIndex (__LINE__, gres2, "text", 0);
    TestFieldIndex (__LINE__, gres2, "data", 1);
    RTR(__LINE__, (gres2 != NULL &&
                   gres2->GetClass()->GetNumEntries() == 2));

    RTR(__LINE__, (gres1 != NULL &&
                   gres1->GetClass()->GetClassID() == 1));
    RTR(__LINE__, (gres2 != NULL &&
                   gres2->GetClass()->GetClassID() == 2));

    RTR(__LINE__, (gres1 != NULL && gres2 != NULL &&
                   Equal(gres1, gres2)));

    delete res1;
    delete res2;
}


void
MyApp::TestBasicInplace()
{
    const char *buf;
    uint32_t buflen;

    const search::docsummary::ResultClass *resClass;
    search::docsummary::GeneralResult *gres;

    uint32_t intval     = 4;
    uint16_t shortval   = 2;
    uint8_t  byteval    = 1;
    float    floatval   = 4.5;
    double   doubleval  = 8.75;
    uint64_t int64val   = 8;
    const char *strval  = "This is a string";
    const char *datval  = "This is data";
    const char *lstrval = "This is a long string";
    const char *ldatval = "This is long data";

    RTR(__LINE__, _packer.Init(0));
    RTR(__LINE__, _packer.AddInteger(intval));
    RTR(__LINE__, _packer.AddShort(shortval));
    RTR(__LINE__, _packer.AddByte(byteval));
    RTR(__LINE__, _packer.AddFloat(floatval));
    RTR(__LINE__, _packer.AddDouble(doubleval));
    RTR(__LINE__, _packer.AddInt64(int64val));
    RTR(__LINE__, _packer.AddString(strval, strlen(strval)));
    RTR(__LINE__, _packer.AddData(datval, strlen(datval)));
    RTR(__LINE__, _packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, _packer.AddLongData(ldatval, strlen(ldatval)));
    RTR(__LINE__, _packer.GetDocsumBlob(&buf, &buflen));

    resClass = _config.LookupResultClass(_config.GetClassID(buf, buflen));
    if (resClass == NULL) {
        gres = NULL;
    } else {
        DocsumStoreValue value(buf, buflen);
        gres = new search::docsummary::GeneralResult(resClass, 0, 0, 0);
        if (!gres->inplaceUnpack(value)) {
            delete gres;
            gres = NULL;
        }
    }

    RTR(__LINE__, gres != NULL);
    TestIntValue   (__LINE__, gres, "integer",    4);
    TestIntValue   (__LINE__, gres, "short",      2);
    TestIntValue   (__LINE__, gres, "byte",       1);
    TestDoubleValue(__LINE__, gres, "float",      floatval);
    TestDoubleValue(__LINE__, gres, "double",     doubleval);
    TestInt64Value (__LINE__, gres, "int64",      int64val);
    TestStringValue(__LINE__, gres, "string",     strval);
    TestDataValue  (__LINE__, gres, "data",       datval);
    TestStringValue(__LINE__, gres, "longstring", lstrval);
    TestDataValue  (__LINE__, gres, "longdata",   ldatval);
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetNumEntries() == 10));
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetClassID() == 0));
    delete gres;
}


void
MyApp::TestCompressInplace()
{
    const char *buf;
    uint32_t buflen;

    search::RawBuf         field1(32768);
    search::RawBuf         field2(32768);
    const search::docsummary::ResultClass   *resClass;
    search::docsummary::GeneralResult *gres;

    const char *lstrval = "string string string";
    const char *ldatval = "data data data";

    RTR(__LINE__, _packer.Init(2));
    RTR(__LINE__, _packer.AddLongString(lstrval, strlen(lstrval)));
    RTR(__LINE__, _packer.AddLongData(ldatval, strlen(ldatval)));
    RTR(__LINE__, _packer.GetDocsumBlob(&buf, &buflen));

    resClass = _config.LookupResultClass(_config.GetClassID(buf, buflen));
    if (resClass == NULL) {
        gres = NULL;
    } else {
        DocsumStoreValue value(buf, buflen);
        gres = new search::docsummary::GeneralResult(resClass, 0, 0, 0);
        if (!gres->inplaceUnpack(value)) {
            delete gres;
            gres = NULL;
        }
    }

    search::docsummary::ResEntry *e1 = (gres == NULL) ? NULL : gres->GetEntry("text");
    search::docsummary::ResEntry *e2 = (gres == NULL) ? NULL : gres->GetEntry("data");

    if (e1 != NULL)
        e1->_extract_field(&field1);
    if (e2 != NULL)
        e2->_extract_field(&field2);

    RTR(__LINE__, gres != NULL);
    RTR(__LINE__, e1 != NULL);
    RTR(__LINE__, e2 != NULL);
    RTR(__LINE__, strcmp(field1.GetDrainPos(), lstrval) == 0);
    RTR(__LINE__, strcmp(field2.GetDrainPos(), ldatval) == 0);
    RTR(__LINE__, strlen(lstrval) == field1.GetUsedLen());
    RTR(__LINE__, strlen(ldatval) == field2.GetUsedLen());
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetNumEntries() == 2));
    RTR(__LINE__, (gres != NULL &&
                   gres->GetClass()->GetClassID() == 2));
    delete gres;
}



int
MyApp::Main()
{
    _rc  = true;
    _cnt = 0;

    search::docsummary::ResultClass *resClass;

    resClass = _config.AddResultClass("c0", 0);
    resClass->AddConfigEntry("integer",    RES_INT);
    resClass->AddConfigEntry("short",      RES_SHORT);
    resClass->AddConfigEntry("byte",       RES_BYTE);
    resClass->AddConfigEntry("float",      RES_FLOAT);
    resClass->AddConfigEntry("double",     RES_DOUBLE);
    resClass->AddConfigEntry("int64",      RES_INT64);
    resClass->AddConfigEntry("string",     RES_STRING);
    resClass->AddConfigEntry("data",       RES_DATA);
    resClass->AddConfigEntry("longstring", RES_LONG_STRING);
    resClass->AddConfigEntry("longdata",   RES_LONG_DATA);

    resClass = _config.AddResultClass("c1", 1);
    resClass->AddConfigEntry("text", RES_STRING);
    resClass->AddConfigEntry("data", RES_DATA);

    resClass = _config.AddResultClass("c2", 2);
    resClass->AddConfigEntry("text", RES_LONG_STRING);
    resClass->AddConfigEntry("data", RES_LONG_DATA);

    TestBasic();
    TestFailLong();
    TestFailShort();
    TestFailOrder();
    TestCompress();
    TestCompat();
    TestBasicInplace();
    TestCompressInplace();

    LOG(info, "CONCLUSION: %s", (_rc) ? "SUCCESS" : "FAIL");
    return (_rc ? 0 : 1);
}


int
main(int argc, char **argv)
{
    MyApp myapp;
    return myapp.Entry(argc, argv);
}
