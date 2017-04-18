// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Definition and implementation of the application for running unit tests
 * for the QueryParser class in isolation.
 *
 * @file queryparserTestApp.cpp
 *
 * @author Knut Omang
 *
 * @date Created 24 Feb 2003
 *
 * $Id$
 *
 * <pre>
 *              Copyright (c) : 2003 Fast Search & Transfer ASA
 *                              ALL RIGHTS RESERVED
 * </pre>
 ****************************************************************************/
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/testapp.h>
#include "queryparserTest.h"
#include "testenv.h"

/**
 * The QueryParserTestApp class is the main routine for running the unit
 * tests for the QueryParser class in isolation.
 *
 * @sa QueryParser @author Knut Omang
 */
class QueryParserTestApp : public vespalib::TestApp {
public:
    virtual int Main() override {
        juniper::TestEnv te(this, TEST_PATH("../rpclient/testclient.rc").c_str());
        QueryParserTest test;
        test.SetStream(&std::cout);
        test.Run(_argc, _argv);
        return (int)test.Report();
    }
};

FASTOS_MAIN(QueryParserTestApp);
