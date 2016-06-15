
;; ------------------------------------------------------------------------
;; Test class .h template
;; ------------------------------------------------------------------------

(defvar cppt-test-header-template "/**
 * Definition of the automated unit test class for the CLASS_NAME class.
 *
 * @file FILE_NAME
 *
 * @author USER_NAME
 *
 * @date Created CREATION_DATE
 *
 * CVS_TAG
 *
 * <pre>
 * Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 * </pre>
 ***************************************************************************/
#ifndef TEST_CLASS_NAME_H
#define TEST_CLASS_NAME_H

#include \"../CLASS_NAME.h\"
#include <map>
#include <fastlib/testsuite/test.h>
INCLUDE_CALLBACK

/**
 * The CLASS_NAMETest class holds 
 * the unit tests for the CLASS_NAME class.
 *
 * @sa      CLASS_NAME
 * @author  USER_NAME
 */
class CLASS_NAMETest : public Test {

  /*************************************************************************
   *                      Test methods
   *
   * This section contains boolean methods for testing each public method
   * in the class ing tested
   *************************************************************************/

  /*************************************************************************
   *                      Test administration methods
   *************************************************************************/

  /**
   * Set up common stuff for all test methods.
   * This method is called immediately before each test method is called
   */
  bool setUp();

  /**
   * Tear down common stuff for all test methods.
   * This method is called immediately after each test method is called
   */
  void tearDown();

  CALLBACK_TYPEDEF;
  typedef std::map<std::string, tst_method_ptr> MethodContainer;
  MethodContainer test_methods_;
  void init();

protected:

  /**
   * Since we are running within Emacs, the default behavior of
   * print_progress which includes backspace does not work.
   * We'll use a single '.' instead.
   */
  virtual void print_progress() { *m_osptr << '.' << std::flush; }

public:

   CLASS_NAMETest() : Test(\"CLASS_NAME\") { init(); }
   ~CLASS_NAMETest() {}

  /*************************************************************************
   *                         main entry points
   *************************************************************************/
  void Run(MethodContainer::iterator &itr);
  virtual void Run();
  void Run(const char *method);
  void Run(int argc, char* argv[]);
};

#endif // TEST_CLASS_NAME_H

// Local Variables:
// mode:c++
// End:
" "This is the template for the header file for a single test class.
The 'CLASS_NAME' string is replaced with the name of the class to test.
Redefine this variable in your .emacs file, after (require 'cpptest)
if you want to change this template.")



;; ------------------------------------------------------------------------
;; Test class .cpp template
;; ------------------------------------------------------------------------

(defvar cppt-test-body-template "/**
 * Implementation of the automated unit test class for the CLASS_NAME class.
 *
 * @file FILE_NAME
 *
 * @author USER_NAME
 *
 * @date Created CREATION_DATE
 *
 * CVS_TAG
 *
 * <pre>
 * Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 * </pre>
 ***************************************************************************/
#include <fastos/fastos.h>
#include \"TEST_HEADER.h\"
DEBUG_INCLUDES
EXTRA_INCLUDES

/*************************************************************************
 *                      Test methods
 *
 * This section contains boolean methods for testing each public method
 * in the class being tested
 *************************************************************************/

/*************************************************************************
 *                      Test administration methods
 *************************************************************************/

/**
 * Set up common stuff for all test methods.
 * This method is called immediately before each test method is called
 */
bool CLASS_NAMETest::setUp() {
  return true;
}

/**
 * Tear down common stuff for all test methods.
 * This method is called immediately after each test method is called
 */
void CLASS_NAMETest::tearDown() {
}

/**
 * Build up a map with all test methods
 */ 
void CLASS_NAMETest::init() {
}

/*************************************************************************
 *                         main entry points
 *************************************************************************/


void CLASS_NAMETest::Run(MethodContainer::iterator &itr) {
  try {
    DEBUG(\"CLASS_NAMETest\", 3, \"Running test method '\" + itr->first + \"'\");
    if (setUp()) {
      CALL_CALLBACK;
      tearDown();
    }
  } catch (...) {
    _fail(\"Got unknown exception in test method \" + itr->first);
  }
}

void CLASS_NAMETest::Run(const char* method) {
  MethodContainer::iterator pos(test_methods_.find(method));
  if (pos != test_methods_.end()) {
    Run(pos);
  } else {
    std::cerr << \"ERROR: No test method named \\\"\"
              << method << \"\\\"\" << std::endl;
    _fail(\"No such method\");
  }
}

void CLASS_NAMETest::Run() {
  for (MethodContainer::iterator itr(test_methods_.begin());
       itr != test_methods_.end();
       ++itr)
    Run(itr);
}

/*
 * Parse runtime arguments before running.
 * If the -m METHOD parameter is given, run only that method
 */
void CLASS_NAMETest::Run(int argc, char* argv[]) {
  for (int i = 1; i < argc; ++i) {
    if (strcmp(argv[i], \"-m\") == 0 && argc > i + 1) {
      Run(argv[++i]);
      return;
    }
  }
  Run();
}
" "This is the template for the source file for a single test class.
The 'CLASS_NAME' string is replaced with the name of the class to test.
Redefine this variable in your .emacs file, after (require 'cpptest)
if you want to change this template.")



(defvar cppt-notest-template "
// Comment out cerr below to ignore unimplemented tests
#define NOTEST(name) \\
std::cerr << std::endl << __FILE__ << ':' << __LINE__ << \": \" \\
          << \"No test for method '\" << (name) << \"'\" << std::endl;
" "Definition of the NOTEST macro.")


;; ------------------------------------------------------------------------
;; Test class application .cpp template
;; ------------------------------------------------------------------------

(defvar cppt-test-class-app-template "/**
 * Definition and implementation of the application for running unit tests
 * for the CLASS_NAME class in isolation.
 *
 * @file FILE_NAME
 *
 * @author USER_NAME
 *
 * @date Created CREATION_DATE
 *
 * CVS_TAG
 *
 * <pre>
 * Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 * </pre>
 ****************************************************************************/
#include <fastos/fastos.h>
#include \"TEST_HEADER.h\"
DEBUG_INCLUDES
EXTRA_INCLUDES

/**
 * The CLASS_NAMETestApp class is the main routine 
 * for running the unit tests for the CLASS_NAME class 
 * in isolation.
 *
 * @sa CLASS_NAME
 * @author  USER_NAME
 */
class CLASS_NAMETestApp : public FastOS_Application {
public:
  virtual int Main() {
    DEBUG_INIT
    INIT_CODE
    CLASS_NAMETest test;
    test.SetStream(&std::cout);
    test.Run(_argc, _argv);
    return (int)test.Report();
  }
};

FASTOS_MAIN(CLASS_NAMETestApp);
" "This is the template for the test application for a single test class.
The 'CLASS_NAME' string is replaced with the name of the class to test.
Redefine this variable in your .emacs file, after (require 'cpptest)
if you want to change this template.")

;; ------------------------------------------------------------------------
;; Test suite template
;; ------------------------------------------------------------------------

(defvar cppt-suite-template "/**
 * Implementation of the test suite application SUITE.
 *
 * @file FILE_NAME
 *
 * @author USER_NAME
 *
 * @date Created CREATION_DATE
 *
 * CVS_TAG
 *
 * <pre>
 * Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 * </pre>
 ****************************************************************************/
#include <fastos/fastos.h>
#include <fastlib/testsuite/suite.h>
DEBUG_INCLUDES
EXTRA_INCLUDES

/**
 * The SUITE class runs all the unit tests 
 * for the MODULE module.
 *
 * @author USER_NAME
 */
class SUITE : public Suite {

public:
  SUITE();
};

SUITE::SUITE() :
  Suite(\"SUITE\", &std::cout)
{
  // All tests for this module
}

/**
 * The SUITEApp class holds the main body for
 * running the SUITE class.
 *
 * @author USER_NAME
 */
class SUITEApp : public FastOS_Application {
public:
  virtual int Main();
};

int SUITEApp::Main() {
  DEBUG_INIT
  INIT_CODE
  SUITE suite;
  suite.Run();
  long failures = suite.Report();
  suite.Free();
  return (int)failures;
}

FASTOS_MAIN(SUITEApp);
" "This is the template for the source file for a complete test suite.
The 'SUITE' string is replaced with the directory of the classes to test,
with the string 'TestSute' appended.

Redefine this variable in your .emacs file, after (require 'cpptest)
if you want to change this template.")

;; ------------------------------------------------------------------------
;; .cvsignore template
;; ------------------------------------------------------------------------

(defvar cppt-cvsignore-template
  "*Test
*Suite
*test
*suite
dummylib
semantic.cache
*.log
Makefile
.depend"
  "Default content for the .cvsignore file which is put into any newly
created test directories")



;; ------------------------------------------------------------------------
;; fastos.project template
;; ------------------------------------------------------------------------

(defvar cppt-project-template "
CUSTOMMAKE

EXTRA_MAKE_TARGETS

# Build any libraries given as dependencies in the cppt-pretest-target variable
%.a : ../*.h ../*.cpp
	$(MAKE) -C $(@D) $(@F)

# Don't warn for inline
# We need this for STL
ifeq ($(CC_PROG),gcc)
# Don't warn for inline
# We need this for STL
CFLAGS:=$(subst -Winline,,$(CFLAGS)) -Wno-ctor-dtor-privacy
# And force exceptions.
CXX_FLAGS := $(subst -fno-exceptions,-fexceptions,$(CXX_FLAGS))
endif

# Test the whole suite
.PHONY: test
test: _PRETEST_TARGET_ runSUITE

# Run purify on the whole suite
purify: purifySUITE

# Automatically rebuild .depend when source files change
.depend: $(wildcard *.h) $(wildcard *.cpp)
	@$(ECHO_CMD) \"*** Generating new .depend...\"
	@$(ECHO_CMD) > .depend.NEW $(DISCARDALL)
	$(HIDE)$(CXX_MKDEP) $(INCLUDE_PATHS) $(SRCS_CPP) >> .depend.NEW
	$(HIDE)$(MKDEP_POSTPROCESS) .depend.NEW > .depend
	$(HIDE)$(DELETE_CMD) .depend.NEW

ifeq ($(CC_PROG),cc)
# Don't optimize for better stacktrace in dbx.
CFLAGS := $(subst -xO3,,$(CFLAGS))
endif

# Use these for setting up and tearing down test scaffolding (test data etc)
PRE_TEST=_PRE_TEST_
POST_TEST=_POST_TEST_

# Parameters to pass to the test programs
TEST_PARAM=CPPT_TEST_PARAMETERS
TEST_DB_FLAGS=CPPT_TEST_DBFLAGS

###################################################################
# Unit test methods for single class
###################################################################

# Run unit test for a single class
run%Test : %Test
\t$(HIDE)if [ x$(METHOD) = x ]; then \\
\t \t$(ECHO_CMD) \"*** Testing class '$(subst Test,,$<)'\"; \\
\t \t($(PRE_TEST) && \\ 
\t \t ./$< $(TEST_PARAM) && \\
\t \t $(POST_TEST)) || exit $$?; \\
\t \t$(ECHO_CMD) \"*** All tests in class '$<' OK\"; \\
\t else \\
\t \t$(ECHO_CMD) \"*** Running test method '$(METHOD)' for class '$(subst Test,,$<)'\"; \\
\t \t($(PRE_TEST) && \\ 
\t \t ./$< -m $(METHOD) $(TEST_PARAM) && \\
\t \t $(POST_TEST)) || exit $$?; \\
\t \t$(ECHO_CMD) \"*** All tests in test method '$(subst METHOD=,,$(METHOD))' OK\"; \\
\tfi


# Run unit test for a single class in debug mode
run%TestDebug : %Test
\t$(HIDE)if [ x$(METHOD) = x ]; then \\
\t \t$(ECHO_CMD) \"*** Testing class '$(subst TestDebug,,$<)' in debug mode\"; \\
\t \t($(PRE_TEST) && \\ 
\t \t ./$< $(TEST_PARAM) $(TEST_DB_FLAGS) && \\
\t \t $(POST_TEST)) || exit $$?; \\
\t \t$(ECHO_CMD) \"*** All tests in class '$<' OK\"; \\
\t else \\
\t \t$(ECHO_CMD) \"*** Running test method '$(METHOD)' for class '$(subst TestDebug,,$<)' in debug mode\"; \\
\t \t($(PRE_TEST) && \\ 
\t \t ./$< -m $(METHOD) $(TEST_PARAM) $(TEST_DB_FLAGS) && \\
\t \t $(POST_TEST)) || exit $$?; \\
\t \t$(ECHO_CMD) \"*** All tests in test method '$(subst METHOD=,,$(METHOD))' OK\"; \\
\tfi

# Run unit test for a single class in purify
purify%Test : Purified%Test
\t$(HIDE)$(ECHO_CMD) \"*** Testing class with Purify\"
\t$(HIDE)($(PRE_TEST) && \\ 
\t ./$< $(TEST_PARAM) && \\
\t $(POST_TEST)) || exit $$?
\t$(HIDE)$(ECHO_CMD) \"*** All tests in $< OK\"

# Run unit test for a single class in purify with debugging
purify%TestDebug : Purified%Test
\t$(HIDE)$(ECHO_CMD) \"*** Testing class with Purify in debug mode\"
\t$(HIDE)($(PRE_TEST) && \\ 
\t ./$< $(TEST_PARAM) $(TEST_DB_FLAGS) && \\
\t $(POST_TEST)) || exit $$?
\t$(HIDE)$(ECHO_CMD) \"*** All tests in $< OK\"

###################################################################
# Unit test methods for test suite
###################################################################

# Run unit tests for all classes
run%TestSuite : %TestSuite
\t$(HIDE)$(ECHO_CMD) \"*** Testing suite '$(subst TestSuite,,$<)'\"
\t$(HIDE)($(PRE_TEST) && \\ 
\t ./$< $(TEST_PARAM) && \\
\t $(POST_TEST)) || exit $$?
\t$(HIDE)$(ECHO_CMD) \"*** All tests in $< OK\"

# Run unit tests for all classes in the package in debug mode
run%TestSuiteDebug : %TestSuite
\t$(HIDE)$(ECHO_CMD) \"*** Testing suite '$(subst TestSuiteDebug,,$<)' in debug mode\"
\t$(HIDE)($(PRE_TEST) && \\ 
\t ./$< $(TEST_PARAM) $(TEST_DB_FLAGS) && \\
\t $(POST_TEST)) || exit $$?
\t$(HIDE)$(ECHO_CMD) \"*** All tests in $< OK\"

# Run unit tests for all classes in purify mode
purify%TestSuite : Purified%TestSuite
\t$(HIDE)$(ECHO_CMD) \"*** Testing suite with Purify\"
\t$(HIDE)($(PRE_TEST) && \\ 
\t ./$< $(TEST_PARAM) && \\
\t $(POST_TEST)) || exit $$?
\t$(HIDE)$(ECHO_CMD) \"*** All tests in $< OK **\"

# Run unit tests for all classes in purify mode with debugging
purify%TestSuiteDebug : Purified%TestSuite
\t$(HIDE)$(ECHO_CMD) \"*** Testing suite with Purify mode in debug mode\"
\t$(HIDE)($(PRE_TEST) && \\ 
\t ./$< $(TEST_PARAM) $(TEST_DB_FLAGS) && \\
\t $(POST_TEST)) || exit $$?
\t$(HIDE)$(ECHO_CMD) \"*** All tests in $< OK **\"

################################################################
# Build purify executables
################################################################
Purified%: %
\t$(HIDE)$(ECHO_CMD) \"*** Building purify binary $@\"
\t$(HIDE)purify -chain-length=12 -max-threads=250 \
\t-static-checking-default=aggressive -always-use-cache-dir=yes \
\tCC $(CXX_FLAGS) $(LOCALFLAGS) \
\t$(APP_$(shell echo $< | tr '[:lower:]' '[:upper:]')_OBJS) \
\t$(COMPILEEXEOUTPUT_FLAG) $@  \
\t$($(MAKETARGET)LIB_APP_$(shell echo $< | tr '[:lower:]' '[:upper:]')) \
\t$(LINK_FLAGS)
\t$(HIDE)$(ECHO_CMD) \"*** Done $<\"
"
  "This is the template for an empty fastos.project file.

The 'SUITE' string is replaced with the directory of the classes to test,
with the string 'TestSute' appended.

The 'EXTRA_MAKE_TARGETS' string is replaced with the value of the
cppt-extra-make-targets variable

Redefine this variable in your .emacs file, after (require 'cpptest)
if you want to change this template.")

;; -----------------------------------------------------------------------
;; Documentation templates
;; -----------------------------------------------------------------------

(defvar cppt-file-doc-template "/**
 * 
 * @file FILE_NAME
 * @author USER_NAME
 * @date Created CREATION_DATE
 * CVS_TAG
 *
 * <pre>
 * Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 * </pre>
 ****************************************************************************/
"
  "This is a template for generating a file documenation.The USER_NAME
string will be replaced by the full name of the user. The
CREATION_DATE string will be replaced by todays date. The YEAR string
will be replaced by the current year. The FILE_NAME string will be
replaced by the name of the file.")

(defvar cppt-class-doc-template "/**
 * The CLASS_NAME TYPE 
 *
 * @sa anotherClass::anotherMethod
 * @author  USER_NAME
 */
"
  "This is a template for generating a default class documenation. The
TYPE string will be replaced by class or namsespace. The CLASS_NAME_NAME
string will be replaced by the name of the class or namespace. The
USER_NAME string will be replaced by the full name of the user.")

(defvar cppt-method-doc-template "/**
 * The METHOD_NAME 
 *
 * @author USER_NAME
RETURN_TYPE
EXCEPTIONS
PARAMETERS
 */
"
  "This is a template for generating a default method
documenation. The METHOD_NAME string will be replaced by the name of
the method. The USER_NAME string will be replaced by the fulll name of
the user. The RETURN_TYPE string will be replaced by the return type
of the method (if not a ctor or dtor). The PARAMETERS string will be
replaced by a series of '* @parameter PARAMETER_NAME a PARAMETER_TYPE
value' strings for each parameter of the method, if any.")




(provide 'cppttemplates)