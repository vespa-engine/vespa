;; Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
;; testproject.el

;; Local configurations for the cpptest Emacs unit-test
;; framework. This is just an example of typical variables that one
;; usually uses This file should be located in the same directory as
;; the class(es) you want to test.

;; $Revision: 1.6 $ $Date: 2003-09-11 09:14:01 $
;; Author: Nils Sandøy <nils.sandoy@fast.no>

;; Just a message to show that this file is beeing read. Look for this
;; in the *Messages* buffer.
(message "Setting local test configuration for the module")

;; Use fastlib's new debugging features.
;; This requires fastlib 1.6.2.2+
(setq cppt-use-fastlib-debug-p t)

;; Use an underscore based naming scheme classes and method names will
;; Upcase each word instead
(setq cppt-use-underscore-p t)

;; Use author in documentation. Set this value to nil if not
(setq cppt-doc-author-p t)

;; Use the new fastlib callback method.
;; Use this for newer versions of gcc
(setq cppt-use-callback-p t)

;; This is a subdirectory of the directory in which this file, along with
;; the source code to test, resides
(setq cppt-test-dir "test")

;; Use this variable to include extra file in your test source, and
;; application files. Typically this will hold headers for log
;; functionality etc.
;; Example: (setq cppt-extra-source-includes "#include \"../Log.h\"")
(setq cppt-extra-source-includes "")

;; If the above source files are not part of a library, you will
;; probably have to include them in the fastos.project file.
;; Example: (setq cppt-extra-object-files '("../Log"))
(setq cppt-extra-object-files nil)

;; If the source code does not have a fastos.project file with all
;; required libraries for linking an executable (typically the case
;; when the source is part of a library itself), then you should use
;; this variable to provide a list of libraries which will be appended
;; to the EXTERNALLIBS section for all applications in the
;; fastos.project file.
;; Example: (setq cppt-extra-libraries '("fast"))
(setq cppt-extra-libraries nil)
(setq cppt-extra-external-libraries '("fast"))

;; Include source file in test executables.
;; Set this to nil if you are testing part of a library
(setq cppt-include-source-p "t")


;; If your initialisation code below requires special parameters for
;; running the test executables, add them here
;; Example: (setq cppt-test-parameters "--test-mode")
(setq cppt-test-parameters "")

;; If you support a special debug mode, which is executed through the
;; cppt-suite-debug or cppt-run-test-debug methods, then you should
;; add the parameter for identifying this here.
;; The parameters given here assume that the fastlib debug features are
;; turned on
;; Example: (setq cppt-test-dbflags "-d")
(setq cppt-test-dbflags "-d all -d emacs")

;; If you support logging etc, you should include code here for
;; insitializing this as part of the Main body of the test application
;; Example: 
;; Add intialization code that turns on logging, and logs to stderr in debug
;; mode
;; (setq cppt-application-init-code
;;       "RTLogDistributor::GetInstance().RegisterDestination( 
;;     new Fast_FileLogger(\"CLASSTest.log\"), FLOG_ALL);
;;   for (int i=0; i < argc; ++i) {
;;     if (strcmp(argv[i], \"-d\") == 0) {
;;       // Turn on debug mode (log to stderr)
;;       RTLogDistributor::GetInstance().RegisterDestination( 
;;         new Fast_FileLogger(stderr), FLOG_ALL);
;;       LOG_DBG(\"Running in debug mode\");
;;     }
;;   }")
(setq cppt-application-init-code "")

;; Pretty much the same as the application init code, but this is used
;; for the Main method of the test suite.
;; Example:
;; Add intialization code that turns on logging, and logs to stderr in debug
;; mode
;; (setq cppt-suite-init-code
;;   "RTLogDistributor::GetInstance().RegisterDestination( 
;;     new Fast_FileLogger(\"SUITETest.log\"), FLOG_ALL);
;;   for (int i=0; i < argc; ++i) {
;;     if (strcmp(argv[i], \"-d\") == 0) {
;;       // Turn on debug mode (log to stderr)
;;       RTLogDistributor::GetInstance().RegisterDestination( 
;;         new Fast_FileLogger(stderr), FLOG_ALL);
;;       LOG_DBG(\"Running in debug mode\");
;;     }
;;   }")
(setq cppt-suite-init-code "")
