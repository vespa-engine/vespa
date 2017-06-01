// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2001-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS


#include <vespa/log/log.h>
LOG_SETUP("docsum-parse");
#include <vespa/fnet/frt/frt.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchsummary/docsummary/urlresult.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>


// needed to resolve external symbol from httpd.h on AIX
void FastS_block_usr2() {}


class MyApp : public FastOS_Application
{
public:
    bool Equal(search::docsummary::ResConfigEntry *a, search::docsummary::ResConfigEntry *b);
    bool Equal(search::docsummary::ResultClass *a, search::docsummary::ResultClass *b);
    bool Equal(search::docsummary::ResultConfig *a, search::docsummary::ResultConfig *b);
    bool TestCorrect(const char *dirname, const char *filename);
    bool TestIncorrect(const char *dirname, const char *filename);
    int Main();
};


bool
MyApp::Equal(search::docsummary::ResConfigEntry *a, search::docsummary::ResConfigEntry *b)
{
    return ((a->_type == b->_type)
            && (strcmp(a->_bindname, b->_bindname) == 0));
}


bool
MyApp::Equal(search::docsummary::ResultClass *a, search::docsummary::ResultClass *b)
{
    bool rc = true;

    rc = rc && (a->GetNumEntries() == b->GetNumEntries());
    rc = rc && (a->GetClassID() == b->GetClassID());
    rc = rc && (strcmp(a->GetClassName(), b->GetClassName()) == 0);

    for (uint32_t i = 0; rc && i < a->GetNumEntries(); i++) {
        rc = rc && Equal(a->GetEntry(i), b->GetEntry(i));
    }

    return rc;
}


bool
MyApp::Equal(search::docsummary::ResultConfig *a, search::docsummary::ResultConfig *b)
{
    bool rc = true;

    search::docsummary::ResultClass *resClassA;
    search::docsummary::ResultClass *resClassB;

    rc = rc && (a->GetNumResultClasses() == b->GetNumResultClasses());

    resClassA = a->GetResultClasses();
    resClassB = b->GetResultClasses();

    while(rc && resClassA != NULL && resClassB != NULL) {
        rc = rc && Equal(resClassA, resClassB);
        resClassA = resClassA->GetNextClass();
        resClassB = resClassB->GetNextClass();
    }
    rc = rc && (resClassA == NULL);
    rc = rc && (resClassB == NULL);

    return rc;
}


bool
MyApp::TestCorrect(const char *dirname, const char *filename)
{
    char str1[512]; // test input file
    char str2[512]; // test output file
    char str3[512]; // summary.cf verification file

    search::docsummary::ResultConfig a;
    search::docsummary::ResultConfig b;
    search::docsummary::ResultConfig c;
    search::docsummary::ResultConfig d;

    sprintf(str1, "%s%s%s", dirname,
            FastOS_FileInterface::GetPathSeparator(), filename);
    sprintf(str2, "%s%sout.%s", dirname,
            FastOS_FileInterface::GetPathSeparator(), filename);
    sprintf(str3, "%s%sOK.%s", dirname,
            FastOS_FileInterface::GetPathSeparator(), filename);

    if (!a.ReadConfig(str1)) {
        LOG(error, "could not read config from : %s", str1);
        return false;
    }

    if (!a.WriteConfig(str2)) {
        LOG(error, "could not write config to : %s", str2);
        return false;
    }

    if (!b.ReadConfig(str2)) {
        LOG(error, "could not read config from : %s", str2);
        return false;
    }

    if (!c.ReadConfig(str3)) {
        LOG(error, "could not read config from : %s", str3);
        return false;
    }

    if (!Equal(&a, &b)) {
        LOG(error, "%s and %s does not contain the same config", str1, str2);
        return false;
    }

    if (!Equal(&a, &c)) {
        LOG(error, "%s and %s does not contain the same config", str1, str3);
        return false;
    }

    if (!Equal(&b, &c)) {
        LOG(error, "%s and %s does not contain the same config", str2, str3);
        return false;
    }

    FRT_RPCRequest *req = new FRT_RPCRequest();
    assert(req != NULL);
    c.GetConfig(req);
    d.SetConfig(req);
    if (!Equal(&c, &d)) {
        LOG(error, "RPC get/set failed (%s)", str3);
        req->SubRef();
        return false;
    }
    req->SubRef();

    return true;
}


bool
MyApp::TestIncorrect(const char *dirname, const char *filename)
{
    char str[512];

    sprintf(str, "%s%s%s", dirname,
            FastOS_FileInterface::GetPathSeparator(), filename);

    search::docsummary::ResultConfig resConfig;

    if (resConfig.ReadConfig(str)) {
        LOG(error, "'%s' did not give parse error", str);
        return false;
    }
    return true;
}


int
MyApp::Main()
{
    bool rc = true;

    FastOS_DirectoryScan dirScan("parsetest");
    LOG(info, "looking for input files in 'parsetest'...");
    while (dirScan.ReadNext()) {
        if (strncmp(dirScan.GetName(), "correct.", 8) == 0) {
            if (TestCorrect("parsetest", dirScan.GetName())) {
                LOG(info, "'%s' : positive test PASSED", dirScan.GetName());
            } else {
                LOG(error, "'%s' : positive test FAILED", dirScan.GetName());
                rc = false;
            }
        } else if (strncmp(dirScan.GetName(), "incorrect.", 10) == 0) {
            if (TestIncorrect("parsetest", dirScan.GetName())) {
                LOG(info, "'%s' : negative test PASSED", dirScan.GetName());
            } else {
                LOG(error, "'%s' : negative test FAILED", dirScan.GetName());
                rc = false;
            }
        }
    }
    return (rc ? 0 : 1);
}


int
main(int argc, char **argv)
{
    MyApp myapp;
    return myapp.Entry(argc, argv);
}
