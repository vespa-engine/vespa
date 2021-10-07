;; Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
;; cpptest.el --- C++ unit test support for c++-mode

;; $Revision: 1.179 $ $Date: 2004-02-17 17:01:15 $

;; Author: Nils Sandøy <nils.sandoy@fast.no>

;; Keywords: C++, tools
;;
;; This file is not part of Emacs
;;
;; Utility functions for unit testing of C++ classes as an addition to the
;; CC mode.

;; To use this functionality, put the following code in your .emacs file
;;
;; (setq load-path (nconc '( "<TESTSUITE path>"  )load-path))
;; (require 'cpptest)
;;
;; If you desire a different function/frame-key binding (default
;; f4-f8) you should setq the cppt-xxxx-key variables in your .emacs
;; file as well.

;; This code will look for a 'testproject.el' file in the source code
;; directory, which will be loaded after all variables are
;; defined. This file should be used to set module/project specific
;; variables, like templates and cppt-test-dir. See the sample
;; testproject.el file in this directory.

;; Notes & issues:
;;
;; The cppt-fund-public-methods function still has some problems with
;; inline methods, most often resulting in the missinterpretation of
;; parts of the inlined method body as method declarations.
;;
;; If you have more than one class declared in the same header file,
;; you might run into problems. Caveat emptor!
;;
;; This code employs some features that are only supported by emacs
;; 21.x and later. Most notably hashes and the push pop methods.
;;
;; I also recommend that you byte-compile this file, to make it run
;; faster.


(require 'cppttemplates)

;; ------------------------------------------------------------------------
;; User configurable variables
;; ------------------------------------------------------------------------

(defvar cppt-use-fastlib-debug-p nil
  "*Flag indicating whether to use the fastlib/util/debug.h utility.
This requires fastlib 1.6.2.2+.")

(defvar cppt-source-location nil 
  "This variable will be set by cppt-find-builddir to the current source root.
This is to allow cppt-find-srcdir to replace a build path with the original 
source path")

;;(defvar cppt-relative-compile '("~/fbuild/official" 
;;    "~/fbuild/build/redhat7.3-i686/statusserver-0.0-with-stlport-000")
(defvar cppt-relative-compile nil
  "*Substitution to make to convert source directory to build directory.
argument should be nil or a list with two elements, the first element being the 
directory prefix to match and the second element the value to replace with.
A value of nil means no conversion")

(defvar cppt-relative-compile-versions nil
  "*Set this to t if the source directory has a versionnumber path element - e.g.
it is on the form <packagename>/nn.nn[.nn]/ (typically 0.0 for development) but
there is no such corresponding element in the build tree (fbuild typical situation). 
This path element will then be removed when constructing the build path.")

(defvar cppt-pretest-target nil
  "*Make target that is invoked before each test run")

(defvar cppt-extra-make-targets nil
  "*Extra make targets inserted in the CUSTOMMAKE part of the
fastos.project file in the test directory.  This is often used in
conjunction with cppt-pretest-target")

(defvar cppt-doc-author-p t
  "*Should the author name be used in documentation? 'nil' if not.")

(defvar cppt-use-underscore-p nil
  "*If non-nil, use an underscore based naming scheme for methods and
classes. When nil, use  uppercase to separate words.")

(defvar cppt-pretest "$(ECHO_CMD)"
  "*Commands to be inserted for PRE_TEST in fastos.project. These
commands will be run before the test executable, and in the same
shell. Typically you'll set your LD_LIBRARY_PATH here. If you use more
than one command, enclose them in a set of curly brackets {} so that
they all execute as a single command.")

(defvar cppt-posttest "$(ECHO_CMD)"
  "*Commands to be inserted for POST_TEST in fastos.project. These
commands will be run before the test executable, and in the same
shell. If you use more than one command, enclose them in a set of
curly brackets {} so that they all execute as a single command.")

(defvar cppt-test-dir "test"
  "*Name of the test directory. This is a subdirectory of the
sourcecode directory.")

(defvar cppt-LD_LIBRARY_PATH nil
  "*This will be prepended to LD_LIBRARY_PATH when executing test binaries")

(defvar cppt-use-function-keys-flag t
  "*Should cppt set function keys? This only affects C++ mode.
If you want to turn this off you must set this variable, before the
(require \'cpptest) statement.")

(defvar cppt-toggle-header-key [f4]
  "*Shortcut key to execute the \\[cppt-toggle-header-src] method.
This only affects C++ mode.")

(defvar cppt-toggle-interface-key [S-f4]
  "*Shortcut key to execute the \\[cppt-toggle-interface-headers] method.
This only affects C++ mode.")

(defvar cppt-toggle-header-method-key [C-f4]
  "*Shortcut key to execute the \\[cppt-toggle-header-src-method] method.
This only affects C++ mode.")

(defvar cppt-find-other-file-key [C-S-f4]
  "*Shortcut key to execute the \\[ff-find-other-file] method.
This only affects C++ mode.")

(defvar cppt-switch-code-test-key [f5]
  "*Shortcut key to execute the \\[cppt-switch-code-test] method.
This only affects C++ mode.")

(defvar cppt-switch-code-test-method-key [S-C-f5]
  "*Shortcut key to execute the \\[cppt-switch-code-test-method] method.
This only affects C++ mode.")

(defvar cppt-verify-test-methods-key [S-f5]
  "*Shortcut key to execute the \\[cppt-verify-test-methods] method.
This only affects C++ mode.")

(defvar cppt-new-test-method-key [C-f5]
  "*Shortcut key to execute the \\[cppt-new-test-method] method.
This only affects C++ mode.")

(defvar cppt-run-test-key [f6]
  "*Shortcut key to execute the \\[cppt-run-test] method.
This only affects C++ mode.")

(defvar cppt-run-test-debug-key [C-f6]
  "*Shortcut key to execute the \\[cppt-run-test-debug] method.
This only affects C++ mode.")

(defvar cppt-run-single-test-key [S-f2]
  "*Shortcut key to execute the \\[cppt-run-single-test] method.
This only affects C++ mode.")

(defvar cppt-run-single-test-debug-key [C-S-f2]
  "*Shortcut key to execute the \\[cppt-run-single-test-debug] method.
This only affects C++ mode.")

(defvar cppt-run-test-purify-key [S-C-f6]
  "*Shortcut key to execute the \\[cppt-run-test-purify] method.
This only affects C++ mode.")

(defvar cppt-run-test-purify-debug-key [S-M-f6]
  "*Shortcut key to execute the \\[cppt-run-test-purify-debug] method.
This only affects C++ mode.")

(defvar cppt-test-suite-key [f7]
  "*Shortcut key to execute the \\[cppt-test-suite] method.
This only affects C++ mode.")

(defvar cppt-make-test-key [S-f7]
  "*Shortcut key to execute the \\[cppt-make-test] method.
This only affects C++ mode.")

(defvar cppt-suite-debug-key [C-f7]
  "*Shortcut key to execute the \\[cppt-suite-debug] method.
This only affects C++ mode.")

(defvar cppt-suite-purify-key [S-C-f7]
  "*Shortcut key to execute the \\[cppt-suite-purify] method.
This only affects C++ mode.")

(defvar cppt-suite-purify-debug-key [S-M-f7]
  "*Shortcut key to execute the \\[cppt-suite-purify-debug] method.
This only affects C++ mode.")

(defvar cppt-make-plain-key [f8]
  "*Shortcut key to execute the \\[cppt-make-plain] method.
This only affects C++ mode.")

(defvar cppt-make-build-key [S-f8]
  "*Shortcut key to execute the \\[cppt-make-build] method.
This only affects C++ mode.")

(defvar cppt-compile-key [C-f8]
  "*Shortcut key to execute the \\[cppt-compile] method.
This only affects C++ mode.")

(defvar cppt-insert-file-doc-key [S-f9]
  "*Shortcut key to execute the \\[cppt-insert-file-doc] method.
This only affects C++ mode.")

(defvar cppt-insert-class-doc-key [C-f9]
  "*Shortcut key to execute the \\[cppt-insert-class-doc] method.
This only affects C++ mode.")

(defvar cppt-insert-method-doc-key [f9]
  "*Shortcut key to execute the \\[cppt-insert-method-doc] method.
This only affects C++ mode.")

(defvar cppt-insert-copy-disallowed-key [C-f10]
  "*Shortcut key to execute the \\[cppt-insert-copy-disallowed] method.
This only affects C++ mode.")

(defvar cppt-indent-buffer-key [S-iso-lefttab]
  "*Shortcut key to execute the \\[cppt-indent-buffer] method.
This only affects C++ mode.")

(defvar cppt-make-plain-args "\-k "
  "* Arguments for make when calling cppt-make-plain.")

(defvar cppt-use-callback-p nil
  "*With newer (3.*) versions of gcc the old callback did not
  compile. Introduced a callback in fastlib/util/callback.h and used
  this instead. To use it set cppt-use-callback to non nil.")

;; ------------------------------------------------------------------------
;; Initialization and include extentions
;; ------------------------------------------------------------------------

(defvar cppt-include-source-p "t"
  "Should we include the original file being tested in the test
executable? Set this to nil if you are including this as part of a library")

(defvar cppt-extra-source-includes
  "#include \"../Log.h\""
  "String with extra include statements that will be inserted in all
source files.  These will also be inserted into the Application and
Suite files.  These include statements will be inserted for the
EXTRA_INCLUDES string in the cppt-test-class-app-template and
cppt-suite-template variables")

(defvar cppt-extra-object-files
  '("../Log")
  "List of extra object files to be inlcuded in fastos.project for the
test and the suite applications.")

(defvar cppt-extra-libraries
  nil
  "List of extra libraries to be inlcuded in fastos.project as
LIBS.")

(defvar cppt-extra-external-libraries
  '("fast")
  "List of extra libraries to be inlcuded in fastos.project as
EXTERNALLIBS.")

(defvar cppt-application-init-code
  "FastOS_File::Delete(\"CLASS_NAMETest.log\");
  Fast_FileLogger filelogger(\"CLASS_NAMETest.log\");
  Fast_FileLogger stderrlogger(stderr);
  LogDistributor().RegisterDestination(&filelogger, FLOG_ALL);
  for (int i=0; i < _argc; ++i) {
    if (strcmp(_argv[i], \"-d\") == 0) {
      // Turn on debug mode (log to stderr)
      LogDistributor().RegisterDestination(&stderrlogger, FLOG_ALL);
      LOG_DBG(\"Running in debug mode\");
    }
  }"
  "Initialization code that is inserted for the INIT_CODE string in the
cppt-test-class-app-template variable")

(defvar cppt-suite-init-code
  "FastOS_File::Delete(\"SUITE.log\");
  Fast_FileLogger filelogger(\"SUITE.log\");
  Fast_FileLogger stderrlogger(stderr);
  LogDistributor().RegisterDestination(&filelogger, FLOG_ALL);
  for (int i=0; i < _argc; ++i) {
    if (strcmp(_argv[i], \"-d\") == 0) {
      // Turn on debug mode (log to stderr)
      LogDistributor().RegisterDestination(&stderrlogger, FLOG_ALL);
      LOG_DBG(\"Running in debug mode\");
    }
  }"
  "Initialization code that is inserted for the INIT_CODE string in the
cppt-suite-template variable")

(defvar cppt-test-parameters
  ""
  "Parameter string to pass the the test executable when running it.
This is inserted where the 'CPPT_TEST_PARAMETERS' string is found in the
cppt-project-template.")

(defvar cppt-test-dbflags
  "-d"
  "Parameter string to pass the the test executable when running it
in debug mode. This is inserted where the 'CPPT_TEST_DBFLAGS' string
is found in the cppt-project-template.")


;; ----------------------------------------------
;; Internal utility methods
;; ----------------------------------------------

(defun cppt-is-header-file-p (file-name) 
  "Is file-name a header file ?"
  (let* ((idx (string-match "[^.]+$" file-name))
         (ext (substring file-name idx)))
    (string-match "h\\(pp\\)?$" ext)))

(defun cppt-get-parent-dir (dir)
  "Retrieve the parent directory of the given directory"
;;   (message "Retrieveing parent directory of '%s'" dir)
  (let ((parent (if (string-match "\\(.*/\\)[^/]" dir)
                    (match-string 1 dir)
                  "/")))
;;     (message "Got parent '%s'" parent)
    parent))

(defun cppt-buffer-dir-name ()
  "Return the directory part of the file in the current buffer"
  (let* ((cur-file (buffer-file-name))
         (cur-dir (if (string-match "\\(.*/\\)[^/]+$" cur-file)
                      (match-string 1 cur-file)
                    "/")))
;;     (message "Current file: '%s'" cur-file)
;;     (message "Current directory: '%s'" cur-dir)
    cur-dir))

(defun cppt-test-method-name (method-name)
  "Prefix the named method with test according to the chose naming convention"
  (if cppt-use-underscore-p
      (concat "test_" method-name)
    (concat "test" (cppt-upcase-first-letter method-name))))

(defun cppt-replace-token (token replacement)
  "Do a buffer wide search replace with a fill-paragraph after each
match"
  (let ((old-case-fold case-fold-search))
    (goto-char (point-min))
    (setq case-fold-search nil)
    (while (search-forward-regexp token nil t)
      (replace-match replacement t t)
      (if (string= mode-name "C++")
          (c-fill-paragraph)))
    (setq case-fold-search old-case-fold)))

(defun cppt-default-method-name (&optional include-test-prefix-p)
  "Make a guess at a default method-name entry. This guess is based
on the text surrounding the cursor."
  (let* ((is-header-p (cppt-is-header-file-p buffer-file-name))
         (cw (current-word))
         (word (if (or include-test-prefix-p
                       (string-match "^test_?\\(.*\\)" cw))
                   (match-string 1 cw) cw))
         (regexp-stub "^\\s-*\\(?:[^ (]+\\s-+\\)*%s%s[*&]?\\([^ ()]+\\)\\s-*(\\s-*\\([^(){};]*\\)\\s-*\\(?:throw\\s-*([^)]*)\\)?\\(?:)[^(){};]*[;{]\\|,\\s-*$\\|)\\s-*$\\)")
         (regexp-header
          (format regexp-stub "\\s-*"
                  ;; Ignore or include test prefix in method name?
                  (if include-test-prefix-p "" "\\(?:test_?\\)?")))
         (regexp-src
          (format regexp-stub "\\s-*[^ (]+\\s-*::\\s-*"
                  ;; Ignore or include test prefix in method name?
                  (if include-test-prefix-p "" "\\(?:test_?\\)?")))
         (regexp (if is-header-p regexp-header regexp-src)))
    (save-excursion
      (save-restriction
         (end-of-line)
         (if (not is-header-p)
             (c-end-of-statement))
         (end-of-line)
         (if (or (search-backward-regexp regexp nil t)
                 (and (not is-header-p)
                      ;; Try searching for namespace methods (without ::)
                      (search-backward-regexp regexp-header nil t)))
            (let ((method-name (match-string 1))
                  (parameters (match-string 2)))
              (message
               "Found method declaration for method '%s' with parameters '%s'"
               method-name parameters)
              (if (string-match "^operator\\([^_].*\\)$" method-name)
                  (format
                   "operator%s%s"
                   (if cppt-use-underscore-p "_" "")
                   (cppt-get-operator-name
                    (match-string 1 method-name) parameters))
                method-name))
           word)))))

(defun cppt-print-default-method-name ()
  "Print default method name."
  (interactive)
  (message "Default method name '%s'" (cppt-default-method-name)))

(defun cppt-upcase-first-letter (str)
  "Upcase the first letter of the string argument."
  (concat (upcase (substring str 0 1)) (substring str 1)))

(defun cppt-list-contains-p (lst elem)
  "Return t if the list contains element elem."
  (or (equal elem (car lst))
      (and lst (cppt-list-contains-p (cdr lst) elem))))

(defun cppt-get-src-dir ()
  "Return the full path of the source directory"
  (let* ((reg-ex (format "\\(.*\\)%s/?$" cppt-test-dir))
         (cur-dir (cppt-buffer-dir-name))
         (src-dir (if (string-match reg-ex cur-dir)
                      (match-string 1 cur-dir)
                    cur-dir)))
    src-dir))

(defun cppt-get-test-dir ()
  "Return the full path of the test directory"
  (let* ((reg-ex (format "%s/?$" cppt-test-dir))
         (cur-dir (cppt-buffer-dir-name))
         (test-dir
          (if (string-match reg-ex cur-dir)
              cur-dir
            (format "%s%s/" cur-dir cppt-test-dir))))
    test-dir))

(defun cppt-replace-user-name ()
  "Got point-min and search and replace the USER_NAME string with the
full name of the current user. If the cppt-doc-author-p is nil, the
whole line containing the USER_NAME tag is removed"
  (goto-char (point-min))
  (if cppt-doc-author-p
      (replace-string "USER_NAME" (user-full-name))
    ;; Remove line with '  * @author  USER_NAME'
    (while (re-search-forward
            "^.*USER_NAME[ \n\r*]*\n\\(\\s-*\\*?\\s-*[^ \n\r]\\)" nil t)
      (message "Removing USER_NAME")
      (replace-match "\\1" nil nil))))

(defun cppt-insert-template (template)
  "Insert the given template at current, point and replace common
key words foun in the text. Point at sompletion is at the end of the
inserted text."
  (let ((start-point (point))
        (end-point)
        (file-name (cppt-strip-path buffer-file-name)))
    ;; Insert the template boilerplate
    (insert template)
    (setq end-point (point))
    (save-restriction
      ;; Substitute tags
      (narrow-to-region start-point end-point)
      (cppt-replace-user-name)
      (goto-char (point-min))
      (let ((creation-date nil))
        (while (search-forward "CREATION_DATE" nil t)
          (goto-char (match-beginning 0))
          (if (not creation-date)
              (setq creation-date (cppt-get-creation-date)))
          (message "Replacing CREATION_DATE with '%s'" creation-date)
          (replace-string "CREATION_DATE" creation-date)))
      (goto-char (point-min))
      (replace-string "YEAR" (format-time-string "%Y"))
      (goto-char (point-min))
      (replace-string "FILE_NAME" file-name)
      (goto-char (point-min))
      ;; Clear CVS tags
      (goto-char (point-min))
      (replace-string "CVS_TAG" "\$\Id\: \$")
      (goto-char end-point))))

(defun cppt-build-test-method-name (public-method test-methods)
  "Build a name for the test method for the named public method.
Append a number to the name if it already exists in the test-method list."
  (let ((test-method
         (if cppt-use-underscore-p
             (concat "test_" public-method)
           (concat "test" (cppt-upcase-first-letter public-method))))
        (x))
    (setq x 0)
    (while (cppt-list-contains-p test-methods test-method)
      (progn
        (setq x (+ x 1))
        (setq test-method
              (if cppt-use-underscore-p
                  (format "test_%s%d" public-method x)
                (format "test%s%d"
                        (cppt-upcase-first-letter public-method) x)))))
    test-method))

(defun cppt-build-suite-name (module-name)
  "Build the test suite name for the named module"
  (if cppt-use-underscore-p
      (concat
       (replace-regexp-in-string "_" "" module-name)
       "testsuite")
    ;;        (concat module-name "_test_suite")
    (concat (cppt-upcase-first-letter module-name) "TestSuite")))

(defun cppt-edit-test (src-file-name dir-name)
  "Open the test file for the named file.
Creates a new template for the class if no test file exists."
  (let* ((test-file-name
          (if (string-match (format "%s/?$" cppt-test-dir) dir-name)
              (format "%s/%s%s.cpp"
                      dir-name src-file-name
                      (if cppt-use-underscore-p "test" "Test"))
            (format "%s%s/%s%s.cpp"
                    dir-name cppt-test-dir src-file-name
                    (if cppt-use-underscore-p "test" "Test")))))
;;    (message "Looking for test source file '%s'" test-file-name)
    (if (file-exists-p test-file-name)
        (find-file test-file-name)
      (if (y-or-n-p (format "Add tests for file '%s.h'? " src-file-name))
          (progn
            ;; Open the header file for the current buffer before building
            ;; a new test file for it
            (find-file (cppt-find-header-file-name buffer-file-name))
            (cppt-create-test-file dir-name
                                   src-file-name
                                   test-file-name
                                   (cppt-find-class-name)
                                   (cppt-find-module-name)
                                   (cppt-find-public-method-names))
            ;; Switch back to the new buffer
            (find-file test-file-name))))))

(defun cppt-create-test-file (dir-name
                              src-file-name
                              test-file-name
                              class-name
                              module-name
                              public-methods)
  "Create a new test file for the class.
Also add the file to the module test file, alternatively create the module
test file if it does not already exist."
  ;; Check whether the target directory exists, create it if not
  (let* ((test-dir-name (format "%s/%s" dir-name cppt-test-dir)))
    (unless (file-directory-p test-dir-name)
      (if (file-exists-p test-dir-name)
          (error "Cannot create test directory '%s'.
A file with the same name exists already." test-dir-name)
        (progn
          (message "Creating test dir '%s'." test-dir-name)
          (make-directory test-dir-name)
          ;; Create a default .cvsignore file in the new directory
          (find-file (format "%s/.cvsignore" test-dir-name))
          (insert cppt-cvsignore-template)
          (save-buffer)))))
  ;; First build the header file for the new test class
  (let ((header-file-name (cppt-find-header-file-name test-file-name)))
;;     (message "Creating new test header file %s" header-file-name)
    (find-file header-file-name)
    ;; (message "Header file created")
    (goto-char (point-min))
    ;; (message "Inserting template header body")
    (cppt-insert-template cppt-test-header-template)

    (goto-char (point-min))
    (cppt-replace-token "INCLUDE_CALLBACK" 
			(if cppt-use-callback-p
			    "#include <fastlib/util/callback.h>" ""))
    (goto-char (point-min))
    (message "cpp use callback %s" cppt-use-callback-p)
    (cppt-replace-token "CALLBACK_TYPEDEF" 
			(if cppt-use-callback-p
			    (concat "typedef fast::util::callback<"
				    class-name
				    "_test> tst_method_ptr")
			  "typedef void(CLASS_NAMETest::* tst_method_ptr) ()"))

    ;; Convert to underscore based naming
    (goto-char (point-min))
    ;; (replace-string "CLASS_NAMETest" "CLASS_NAME_test")
    (if cppt-use-underscore-p
	(cppt-replace-token "CLASS_NAMETest" "CLASS_NAME_test"))


    (goto-char (point-min))
    (replace-string
     "CLASS_NAME.h"
     (format "%s.h" (replace-regexp-in-string "_" "" class-name)))
    ;; Insert class name
    (cppt-replace-token "CLASS_NAME" class-name)
    (cppt-insert-test-method-declarations public-methods)
    ;; Fill the first paragraph in file header
    (goto-char (point-min))
    (search-forward-regexp "[^\n\r /*]")
    (c-fill-paragraph)
    (save-buffer))
  ;; Build .cpp file for the new application for running the test class
  (let ((app-file-name (cppt-find-app-file-name test-file-name)))
;;     (message "Creating new test file application %s" app-file-name)
    (find-file app-file-name)
    (goto-char (point-min))
    (cppt-insert-template cppt-test-class-app-template)
    ;; Convert to underscore based naming
    (if cppt-use-underscore-p
        (progn
          (goto-char (point-min))
          (replace-string "CLASS_NAMETestApp" "CLASS_NAME_test_app")
          (goto-char (point-min))
          (replace-string "CLASS_NAMETest" "CLASS_NAME_test")))
    (goto-char (point-min))
    (replace-string "TEST_HEADER"
                    (format "%s%s"
                            (replace-regexp-in-string "_" "" class-name)
                            (if cppt-use-underscore-p "test" "Test")))
    ;; DEBUG replace
    (goto-char (point-min))
    (cppt-replace-token "DEBUG_INCLUDES" 
                        (if cppt-use-fastlib-debug-p
                            "#include <fastlib/util/debug.h>"
                          ""))
    (cppt-replace-token "DEBUG_INIT" 
                        (if cppt-use-fastlib-debug-p
                            "INIT_DEBUG(_argc, _argv);"
                          ""))
    ;; Insert extra include files
    (goto-char (point-min))
    (replace-string "EXTRA_INCLUDES" cppt-extra-source-includes)
    ;; Insert initialization code
    (goto-char (point-min))
    (replace-string "INIT_CODE"
                    ;; Format argv & argc to _argv and _argc
                    (replace-regexp-in-string
                     "\\([^_]\\)argc" "\\1_argc"
                     (replace-regexp-in-string
                      "\\([^_]\\)argv" "\\1_argv"
                      cppt-application-init-code)))
    ;; Set the suite name
    (cppt-replace-token "MODULE" module-name)
    (cppt-replace-token "CLASS_NAME" class-name)
    ;; Fill the first paragraph in file header
    (goto-char (point-min))
    (search-forward-regexp "[^\n\r /*]")
    (c-fill-paragraph)
    (save-buffer))
  ;; Build the .cpp file for the new test class
;;   (message "Creating new test file %s" test-file-name)
  (find-file test-file-name)
  (goto-char 1)
  (cppt-insert-template cppt-test-body-template)
  ;; DEBUG replace
  (goto-char (point-min))
  (cppt-replace-token "DEBUG_INCLUDES" 
		      (if cppt-use-fastlib-debug-p
			  "#include <fastlib/util/debug.h>"
			""))
  (if (not cppt-use-fastlib-debug-p)
      (cppt-replace-token "DEBUG(.*);" "")) 
      
  ;; callback call replace
  (goto-char (point-min))
  (cppt-replace-token "CALL_CALLBACK" 
		      (if cppt-use-callback-p
			  "itr->second()"
			"(this->*itr->second)()"))
  ;; Convert to underscore based naming
  (goto-char (point-min))
  (and cppt-use-underscore-p
       (goto-char (point-min))
       (replace-string "CLASS_NAMETest" "CLASS_NAME_test"))
  (goto-char (point-min))
  (replace-string "TEST_HEADER"
                  (format "%s%s"
                          (replace-regexp-in-string "_" "" class-name)
                          (if cppt-use-underscore-p "test" "Test")))
  ;; Insert extra include files
  (goto-char (point-min))
  (replace-string "EXTRA_INCLUDES" cppt-extra-source-includes)
  (cppt-replace-token "MODULE" module-name)
  (cppt-replace-token "CLASS_NAME" class-name)
  ;; Fill the first paragraph in file header
  (goto-char (point-min))
  (search-forward-regexp "[^\n\r /*]")
  (c-fill-paragraph)
  (cppt-insert-test-methods public-methods class-name)
  (cppt-insert-run-method public-methods class-name)
  (save-buffer)
  (if (< (count-windows) 2) (split-window-vertically))
  (other-window 1)
  (cppt-add-class-test dir-name
                       test-file-name
                       src-file-name
                       class-name
                       module-name)
  (other-window -1))

(defun cppt-find-header-file-name (file-name)
  "Find the name of the header file for the named file."
  (let ((header-file-name
         (concat
          (substring file-name 0
                     (string-match "[^.]+$" file-name)) "h")))
    ;; (message "Header file name: %s" header-file-name)
    header-file-name))

(defun cppt-find-app-file-name (file-name)
  "Build a name for an application file from the given file name"
  (let ((app-file-name
         (concat
          (substring file-name 0
                     (string-match "\\.[^.]+$" file-name))
          (if cppt-use-underscore-p "app.cpp" "App.cpp"))))
    ;; (message "App file name: %s" app-file-name)
    app-file-name))

(defun cppt-create-test-suite (file-name suite-name module-name)
  "Create a new test suite for the named module."
;;   (message "Creating test suite file %s" file-name)
  (find-file file-name)
  (goto-char (point-min))
  (cppt-insert-template cppt-suite-template)
  ;; DEBUG replace
  (goto-char (point-min))
  (cppt-replace-token "DEBUG_INCLUDES" 
                      (if cppt-use-fastlib-debug-p
                          "#include <fastlib/util/debug.h>"
                        ""))
  (cppt-replace-token "DEBUG_INIT" 
                      (if cppt-use-fastlib-debug-p
                          "INIT_DEBUG(_argc, _argv);"
                        ""))
  ;; Insert extra include files
  (goto-char (point-min))
  (replace-string "EXTRA_INCLUDES" cppt-extra-source-includes)
  ;; Insert initialization code
  (goto-char (point-min))
  (replace-string "INIT_CODE"
                  ;; Format argv & argc to _argv and _argc
                  (replace-regexp-in-string
                   "\\([^_]\\)argc" "\\1_argc"
                   (replace-regexp-in-string
                    "\\([^_]\\)argv" "\\1_argv"
                    cppt-suite-init-code)))
  ;; Convert to underscare naming
  (and cppt-use-underscore-p
       (goto-char (point-min))
       (replace-string "SUITEApp" "SUITE_app"))
  (cppt-replace-token "SUITE" suite-name)
  (cppt-replace-token "MODULE" module-name)
  (save-buffer))

(defun cppt-insert-libs (libs)
  "Insert one line for each of the items in the libs list"
  ;; (message "cppt-insert-libs: %s, num %d" (car libs) (length libs))
  (if libs
      (progn
        (insert (format "%s\n" (car libs)))
        (cppt-insert-libs (cdr libs)))
    ;; Insert extra libs, if any
    (if cppt-extra-libraries
	(let ((lib (car cppt-extra-libraries))
	      (rest (cdr cppt-extra-libraries)))
	  (insert "LIBS ")
	  (while lib
	    (progn
	      (insert lib " ")
	      (setq lib (car rest))
	      (setq rest (cdr rest))))
	  (insert "\n")))
    ;; Insert extra external libs, if any
    (if cppt-extra-external-libraries
	(let ((lib (car cppt-extra-external-libraries))
	      (rest (cdr cppt-extra-external-libraries)))
	  (insert "EXTERNALLIBS ")
	  (while lib
	    (progn
	      (insert lib " ")
	      (setq lib (car rest))
	      (setq rest (cdr rest))))
	  (insert "\n")))))

(defun cppt-find-project-libs (file-name)
  "Build a list with all LIBS or EXTERNALLIBS found in the named project."
  ;; (message "Searching for library dependencies within %s" file-name)
  (find-file file-name)
  (goto-char (point-min))
  (let ((libs))
    (while (search-forward-regexp
            "^\\(EXTERNALLIBS\\|LIBS\\).*$" (point-max) t)
      (let ((lib-str (match-string 0)))
        (unless (cppt-list-contains-p libs lib-str)
          (setq libs (cons lib-str libs))
          ;; (message "Found '%s', num libs: %d" (car libs) (length libs))
          )))
    (kill-buffer nil)
    libs))

(defun cppt-create-project (file-name suite-name)
  "Create a new fastos.project file"
;;   (message "Creating project file '%s'" file-name)
  (find-file file-name)
  (goto-char (point-min))
  (cppt-insert-template cppt-project-template)
  (if cppt-use-underscore-p
      (progn 
        (goto-char (point-min))
        (replace-string "%Test" "%test")
        (goto-char (point-min))
        (replace-string "%testSuite" "%testsuite")))
  (goto-char (point-min))
  (replace-string "EXTRA_MAKE_TARGETS" (or cppt-extra-make-targets ""))
  (goto-char (point-min))
  (replace-string "_PRETEST_TARGET_"
                  (if cppt-pretest-target cppt-pretest-target ""))
  (goto-char (point-min))
  (replace-string "_PRE_TEST_" cppt-pretest)
  (goto-char (point-min))
  (replace-string "_POST_TEST_" cppt-posttest)
  (goto-char (point-min))
  (replace-string "SUITE" suite-name)
  (goto-char (point-min))
  (replace-string "CPPT_TEST_PARAMETERS" cppt-test-parameters)
  (goto-char (point-min))
  (replace-string "CPPT_TEST_DBFLAGS" cppt-test-dbflags))

(defun cppt-insert-test-method-declarations (public-methods &optional comment)
  "Insert the declarations of the test methods for all methods
in the given list"
  (let ((test-methods)
        (test-method))
    (goto-char 1)
    (re-search-forward "/[* \n]+Test methods[^/]+/\n" nil t)
    (if public-methods
        (while public-methods
;;           (message "Creating test method declaration for '%s'"
;;                    (car public-methods))
          (setq test-method
                (cppt-build-test-method-name
                 (car public-methods) test-methods))
          (setq test-methods (cons test-method test-methods))
;;           (message "Inserting declaration of test method '%s'" test-method)
          (insert
           (format
            "\n  /**\n   * %s\n   */\n  void %s();\n\n"
            (if comment
                comment
              (format "Test of the '%s' method." (car public-methods)))
            test-method))
          (setq public-methods (cdr public-methods)))
      ;; No public methods for the class beeing tested
      (insert (format "
    /**
     * This is just a dummy test method to indicate that there are no tests
     * for this class
     */
    void testTest();\n\n")))))

(defun cppt-insert-test-method (test-method class-name public-method
                                            &optional comment)
  ;; Verify that the NOTEST macro exists in the file
  (save-excursion
    (save-restriction
      (goto-char (point-min))
      (if (not (search-forward "NOTEST" nil t))
          (progn 
            (message "Inserting NOTETST macro definition")
            (search-forward-regexp "\n/\\*+\n\\s-*\\*\\s-*Test methods" nil t)
            (goto-char (match-beginning 0))
            (insert cppt-notest-template)))))
  (message "Creating test method '%s' for class '%s'" test-method class-name)
  (insert (format "
/**
 * %s
 */
void %s%s::%s() {
  NOTEST(\"%s\"\);
}\n\n"
                  (if comment
                      comment
                    (format "Test of the '%s' method." public-method))
                  class-name
                  (if (string-match "_?[tT]est$" class-name)
                      ""
                    (if cppt-use-underscore-p "_test" "Test"))
                  test-method
                  public-method)))

(defun cppt-insert-test-methods (public-methods class-name &optional comment)
  "Insert test methods for all methods in the given list"
  (message "Inserting test methods for class %s" class-name)
  (let ((test-methods))
    (goto-char 1)
    (re-search-forward "/[* \n]+Test methods[^/]+/\n" nil t)
    (if public-methods
        (while public-methods
          (let* ((public-method
                  (if (listp public-methods)
                      (car public-methods)
                    public-methods))
                 (test-method
                  (cppt-build-test-method-name public-method test-methods)))
            (setq test-methods (cons test-method test-methods))
            (cppt-insert-test-method test-method
                                     class-name
                                     public-method
                                     comment)
            (setq public-methods (cdr public-methods))))
      (progn
        (message "No public methods in class '%s'. %s"
                 "Inserting default test method" class-name)
        (insert (format "
/**
 * This is just a dummy test method to indicate that there are no tests
 * for this class
 */
void %sTest::testTest() {
  _fail(\"No tests implemented for class %s\");
}\n" class-name class-name))))))

(defun cppt-insert-run-method (public-methods class-name)
  "Insert a run method to execute all test methods in the given list"
  (let ((test-methods))
    (goto-char 1)
    ;; Try to add to init method first
    (if (re-search-forward "::init() {[ \t]*\n" nil t)
        (progn
          (if public-methods
              (while public-methods
                (let* ((public-method (if (listp public-methods)
                                          (car public-methods)
                                        public-methods))
                       (test-method (cppt-build-test-method-name
                                     public-method test-methods)))
                  (setq test-methods (cons test-method test-methods))
                  (message
                   "Adding test method '%s' to method container for class '%s'"
                   test-method class-name)
                  (insert (format
			   (concat "  test_methods_.\n    insert(MethodContainer::value_type\n           (std::string(\"%s\"), \n            "
				 (if cppt-use-callback-p
				     "fast::util::make_callback(*this, &%s%s::%s)));\n"
				   "&%s%s::%s));\n"))
			   test-method
                           class-name
                           (if (string-match "_?[tT]est$" class-name)
                               ""
                             (if cppt-use-underscore-p "_test" "Test"))
                           test-method))
                  (setq public-methods (cdr public-methods))))
            (insert (format 
		     (concat "\n  test_methods_[\"test\"] = "
			   (if cppt-use-callback-p
			       "fast::util::make_callback(*this, &%s%s::test);\n"
			     "&%s%s::test;\n"))
		     class-name
		     (if (string-match "_?[tT]est$" class-name)
			 ""
		       (if cppt-use-underscore-p "_test" "Test"))))))
      ;; Use the old way of putting everything in Run instead
      (re-search-forward "::Run() {[ \t]*\n" nil t)
      (if public-methods
          (while public-methods
            (let* ((public-method (if (listp public-methods)
                                      (car public-methods)
                                    public-methods))
                   (test-method (cppt-build-test-method-name public-method
                                                             test-methods)))
              (setq test-methods (cons test-method test-methods))
              (message
               "Creating run statement for test method %s" test-method)
              (insert (format "
  if (setUp()) {
    %s();
    tearDown();
  }\n" test-method))
              (setq public-methods (cdr public-methods))))
        (insert "
  if (setUp()) {
    testTest();
    tearDown();
  }\n")))))

(defun cppt-insert-extra-objs (extra-objs)
  "Add extra OBJS ... descriptions to the current buffer"
  (if (car extra-objs)
      (progn
        (insert (format "OBJS %s\n" (car extra-objs)))
        (cppt-insert-extra-objs (cdr extra-objs)))))

(defun cppt-add-class-test (dir-name
                            test-file-name
                            src-file-name
                            class-name
                            module-name)
  "Add class test to the module test suite and the fastos.project file."
  (let* ((src-dir (cppt-get-src-dir))
         (test-name (cppt-strip-file-name test-file-name))
         (src-name (cppt-strip-file-name src-file-name))
         (suite-name (cppt-build-suite-name module-name))
         (file-name (format "%s%s/%s.cpp"
                            dir-name cppt-test-dir suite-name))
         (project-file-name
          (format "%s%s/fastos.project" dir-name cppt-test-dir))
         (test-class-name (concat class-name (if cppt-use-underscore-p
                                                 "_test" "Test"))))
    ;; Create or open the test suite source file
    (if (not (file-exists-p file-name))
        (cppt-create-test-suite file-name suite-name module-name)
      (find-file file-name))
    (goto-char (point-min))
    (if (not (search-forward "All tests for this module" nil t))
        (error "Malformed suite template")
      (progn
;;         (message "Adding %s to suite %s" test-class-name suite-name)
        (let ((start (point)))
          (insert (format "\n  AddTest(new %s());" test-class-name ))
          ;; Sort the order of the tests
          (sort-lines nil start (search-forward "}")))
        (goto-char (point-min))
        ;; Go to the end of the initial file comment section
        (search-forward "*/\n")
        (insert (format "#include \"%s.h\"\n" test-name))
        (save-buffer)))
    (let ((libs (cppt-find-project-libs
                 (format "%s/fastos.project" dir-name))))
      (setq suite-name (replace-regexp-in-string "_" "" suite-name))
      ;; Create or open the fastos.project file
      (if (not (file-exists-p project-file-name))
          (cppt-create-project project-file-name suite-name)
        (find-file project-file-name))
      (goto-char (point-min))
      (if (search-forward (concat "APPLICATION " suite-name)
                          (point-max) t)
          ;; Add class to suite dependencies
          (insert (format "\nOBJS %s%s" test-name
                          (if cppt-include-source-p
                              (format "\nOBJS ../%s" src-name)
                            "")))
        (progn
          ;; Add suite application, since not present
          (goto-char (point-min))
;;           (message "Creating APPLICATION for %s" suite-name)
          (insert (format "APPLICATION %s\nOBJS %s\nOBJS %s%s\n"
                          suite-name suite-name test-name
                          (if cppt-include-source-p
                              (format "\nOBJS ../%s" src-name)
                            "")))
          (cppt-insert-extra-objs cppt-extra-object-files)
          (cppt-insert-libs libs)))
      ;; Create separate application for the class
      (goto-char (point-min))
;;       (message "Creating APPLICATION %s" test-name)
      (insert (format "APPLICATION %s\nOBJS %s\nOBJS %s%s\n"
                      test-name test-name
                      (format "%s%s" test-name
                              (if cppt-use-underscore-p "app" "App"))
                      (if cppt-include-source-p
                          (format "\nOBJS ../%s" src-name)
                        "")))
      (cppt-insert-extra-objs cppt-extra-object-files)
      (cppt-insert-libs libs)
      (insert "\n\n")
      (save-buffer)
      ;; Generate the makefile by switching back to the source code,
      ;; and running make from that directory
      (find-file (format "%s/%s.h" src-dir src-file-name))
;;       (message "Generating makefile")
      (compile "make makefiles"))
    ;; Switch back to the suite file
    (find-file file-name)))

(defun cppt-set-lib-path (lib-path envir)
  "Prepend lib-path to the LD_LIBRARY_PATH environent variable."
  (let ((new-envir))
    (while (and (car envir)
                (not (equal t (compare-strings
                               (car envir) 0 15 "LD_LIBRARY_PATH" 0 15))))
      (push (pop envir) new-envir))
    (if (car envir)
        (progn
          ;; Modify existing LD_LIBRARY_PATH entry
          (push (format "LD_LIBRARY_PATH=%s:%s"
                        lib-path (substring (car envir) 16))
                new-envir)
          (pop envir)
          ;; Add the rest of the environment
          (while (car envir)
            (push (pop envir) new-envir)))
      ;; Add new LD_LIBRARY_PATH entry
      (push (format "LD_LIBRARY_PATH=%s" lib-path) new-envir))
    new-envir))

(defun cppt-verify-method (method-name
                           class-name
                           test-header-file
                           test-src-file)
  "Verify that the test class has a test method for the named method"
  (let ((test-method
         (if cppt-use-underscore-p
             (concat "test_" method-name)
           (concat "test" (cppt-upcase-first-letter method-name)))))
    ;; Search through the header file to see if the test method exists
    (find-file test-header-file)
    (goto-char (point-min))
    (if (search-forward (concat test-method "(") nil t)
        (message "Test method '%s' exists for method '%s'"
                 test-method method-name)
      (if (not
           (y-or-n-p (format "Add test for method '%s'? " method-name)))
          (message "Skipping test method '%s'" test-method)
        (message "Adding test method '%s'" test-method)
        (cppt-insert-test-method-declarations (list method-name))
        (recenter nil)
        (find-file test-src-file)
        (cppt-insert-test-methods (list method-name) class-name)
        (cppt-insert-run-method (list method-name) class-name)
        ;; Search to the implementation of the latest method added
        (goto-char (point-min))
        (search-forward (format "NOTEST(\"%s\")" method-name))
        (beginning-of-line)
        (recenter nil)))))

(defun cppt-find-class-name ()
  "Find the first class name of the current buffer. Will use the
name of the file instead if no class can be found"
  (interactive)
  (save-excursion
    (save-restriction
      (let ((class-name))
        (goto-char (point-min))
        (if (re-search-forward
             "^\\s-*class\\s-+\\([^ \n\r\t;{]+\\)[^;]*{" nil t)
            (setq class-name (match-string 1)))
        (if class-name
            (message "Found class '%s' in '%s'" class-name buffer-file-name)
          (progn
            (setq class-name (cppt-find-file-name-root))
            (message "No class in '%s', using '%s'"
                     buffer-file-name class-name)))
        class-name))))

(defun cppt-get-path (file-name)
  "Return just the path of the file-name up to, and including, the last /."
  (if (string-match "\\(.*/\\)" file-name)
      (match-string 1 file-name)
    file-name))

(defun cppt-strip-path (file-name)
  "Return the last part of the file-name without the leading path."
  (let ((regexp ".*/\\(.*\\)"))
    (if (string-match regexp file-name)
        (match-string 1 file-name)
      file-name)))

(defun cppt-strip-file-name (file-name)
  "Strip off leading path and any .h .cpp extension from the given file name"
  (let* ((reg-ex ".*/\\(.*\\)")
         (stripped-name file-name))
    ;; First strip off any extension
    (if (string-match "\\(.*\\)\\..*$" file-name)
        (setq stripped-name (match-string 1 file-name)))
    (cppt-strip-path stripped-name)))

(defun cppt-find-file-name-root ()
  "Find the file name of the current buffer without extension (.h | .cpp)."
  (interactive)
  (let* ((regexp "[^\\/]+$")
         (file-name
          (substring buffer-file-name
                     (string-match regexp buffer-file-name)
                     (- (string-match "[^.]+$" buffer-file-name) 1))))
    ;; (message "File name: %s" file-name)
    file-name))

(defun cppt-find-module-name ()
  "Find the module name of the current buffer."
  (interactive)
  (save-excursion
    (save-restriction
      (let* ((dir-name (cppt-buffer-dir-name))
             (re (format "/%s/$" cppt-test-dir))
             (regexp1 "[^\\/]+\\/$")
             (regexp2 "\\/$")
             (idx (string-match re dir-name))
             (module-name))
        (if idx (setq dir-name (substring dir-name 0 (+ idx 1))))
        (setq module-name (substring dir-name
                                     (string-match regexp1 dir-name)
                                     (string-match regexp2 dir-name)))
;;         (message "Module name: %s" module-name)
        module-name))))

(defun cppt-get-operator-name (operator parameters)
  "Find the textual name of the given operator"
  (let ((opr-hash (make-hash-table :test 'equal)))
    (puthash "+" "plus" opr-hash)
    (puthash "-" "minus" opr-hash)
    (puthash "*" "star" opr-hash)
    (puthash "/" "divide" opr-hash)
    (puthash "%" "mod" opr-hash)
    (puthash "^" "hat" opr-hash)
    (puthash "&" "bitand" opr-hash)
    (puthash "|" "bitor" opr-hash)
    (puthash "~" "tilde" opr-hash)
    (puthash "!" "not" opr-hash)
    (puthash "=" "assign" opr-hash)
    (puthash "<" "less" opr-hash)
    (puthash ">" "greater" opr-hash)
    (puthash "++" "increment" opr-hash)
    (puthash "--" "decrement" opr-hash)
    (puthash "==" "equality" opr-hash)
    (puthash "!=" "inequality" opr-hash)
    (puthash "<=" "lessequal" opr-hash)
    (puthash ">=" "greaterequal" opr-hash)
    (puthash "+=" "plusassign" opr-hash)
    (puthash "-=" "minusassign" opr-hash)
    (puthash "*=" "starassign" opr-hash)
    (puthash "/=" "slashassign" opr-hash)
    (puthash "%=" "modassign" opr-hash)
    (puthash "^=" "hatassign" opr-hash)
    (puthash "&=" "andassign" opr-hash)
    (puthash "|=" "orassign" opr-hash)
    (puthash "<<" "leftshift" opr-hash)
    (puthash ">>" "rightsift" opr-hash)
    (puthash ">>=" "rightsiftassign" opr-hash)
    (puthash "<<=" "leftshiftassign" opr-hash)
    (puthash "&&" "and" opr-hash)
    (puthash "||" "or" opr-hash)
    (puthash "->*" "pointertomember" opr-hash)
    (puthash "," "comma" opr-hash)
    (puthash "->" "pointer" opr-hash)
    (puthash "[]" "squarebracket" opr-hash)
    (puthash "()" "parenthesis" opr-hash)
    (puthash "new" "new" opr-hash)
    (puthash "new[]" "newarray" opr-hash)
    (puthash "delete" "delete" opr-hash)
    (puthash "delete[]" "deletearray" opr-hash)
    (let ((name (gethash operator opr-hash "unknown")))
      (if (or (string= name "increment") (string= name "decrement"))
          (if (string= parameters "")
              (concat "pre" name)
            (concat "post" name))
        name))))

(defun cppt-get-operator (operator-name)
  "Return a reg-exp for the named operator"
  (let ((opr-hash (make-hash-table :test 'equal)))
    (puthash "plus" "\\+\\s-*([^}{]+{" opr-hash)
    (puthash "minus" "-\\s-*([^}{]+{" opr-hash)
    (puthash "star" "\\*\\s-*([^}{]+{" opr-hash)
    (puthash "divide" "/\\s-*([^}{]+{" opr-hash)
    (puthash "mod" "%\\s-*([^}{]+{" opr-hash)
    (puthash "hat" "\\^\\s-*([^}{]+{" opr-hash)
    (puthash "bitand" "&\\s-*([^}{]+{" opr-hash)
    (puthash "bitor" "|\\s-*([^}{]+{" opr-hash)
    (puthash "tilde" "~\\s-*([^}{]+{" opr-hash)
    (puthash "not" "!\\s-*([^}{]+{" opr-hash)
    (puthash "assign" "=\\s-*([^}{]+{" opr-hash)
    (puthash "less" "<\\s-*([^}{]+{" opr-hash)
    (puthash "greater" ">\\s-*([^}{]+{" opr-hash)
    (puthash "postincrement" "\\+\\+\\s-*(\\s-*int\\s-*)[^}{]*{" opr-hash)
    (puthash "preincrement" "\\+\\+\\s-*(\\s-*)[^}{]*{" opr-hash)
    (puthash "postdecrement" "--\\s-*(\\s-*int\\s-*)[^}{]*{" opr-hash)
    (puthash "predecrement" "--\\s-*(\\s-*)[^}{]*{" opr-hash)
    (puthash "equality" "==\\s-*([^}{]+{" opr-hash)
    (puthash "inequality" "!=\\s-*([^}{]+{" opr-hash)
    (puthash "lessequal" "<=\\s-*([^}{]+{" opr-hash)
    (puthash "greaterequal" ">=\\s-*([^}{]+{" opr-hash)
    (puthash "plusassign" "\\+=\\s-*([^}{]+{" opr-hash)
    (puthash "minusassign" "-=\\s-*([^}{]+{" opr-hash)
    (puthash "starassign" "\\*=\\s-*([^}{]+{" opr-hash)
    (puthash "slashassign" "/=\\s-*([^}{]+{" opr-hash)
    (puthash "modassign" "%=\\s-*([^}{]+{" opr-hash)
    (puthash "hatassign" "\\^=\\s-*([^}{]+{" opr-hash)
    (puthash "andassign" "&=\\s-*([^}{]+{" opr-hash)
    (puthash "orassign" "|=\\s-*([^}{]+{" opr-hash)
    (puthash "leftshift" "<<\\s-*([^}{]+{" opr-hash)
    (puthash "rightsift" ">>\\s-*([^}{]+{" opr-hash)
    (puthash "rightsiftassign" ">>=\\s-*([^}{]+{" opr-hash)
    (puthash "leftshiftassign" "<<=\\s-*([^}{]+{" opr-hash)
    (puthash "and" "&&\\s-*([^}{]+{" opr-hash)
    (puthash "or" "||\\s-*([^}{]+{" opr-hash)
    (puthash "pointertomember" "->*\\s-*([^}{]+{" opr-hash)
    (puthash "comma" ",\\s-*([^}{]+{" opr-hash)
    (puthash "pointer" "->\\s-*([^}{]+{" opr-hash)
    (puthash "squarebracket" "\\[\\]\\s-*([^}{]+{" opr-hash)
    (puthash "parenthesis" "()\\s-*([^}{]+{" opr-hash)
    (puthash "new" "new\\s-*([^}{]+{" opr-hash)
    (puthash "newarray" "new\\[\\]\\s-*([^}{]+{" opr-hash)
    (puthash "delete" "delete\\s-*([^}{]+{" opr-hash)
    (puthash "deletearray" "delete\\[\\]\\s-*([^}{]+{" opr-hash)
    (concat "operator\\s-*" (gethash operator-name opr-hash "unknown"))))

(defun cppt-skip-comments (end)
  "Find the first line of non-comments"
  ;; Skip // and /* comments
  (goto-char
   (if (re-search-forward "^\\s-*[^ \t\r\n/][^ \t\r\n/*].*" end t)
       (match-beginning 0)
     end))
  ;; Skip lines starting with *
  (if (< (point) end)
      (goto-char
       (if (re-search-forward "^\\s-*[^ \t\n\r*].*" end t)
           (match-beginning 0)
         end))))

(defun cppt-find-methods (end-of-region &optional class-name)
  "Find all method declarations btw current point and end-of-region"
  (let ((methods)
        (method)
        (end (if end-of-region end-of-region (point-max))))
    (while (< (point) end)
      (cppt-skip-comments end)
      (if (< (point) end)
          ;; Extract method name
          (if (not (re-search-forward
                    "^\\s-+\\(\\([^ /:,{}();\t\n*]\\|[^:]::[^:]\\)+\\)\\(\\([^({},:;]\\|[^:]::[^:]\\)*[ *&\n]+\\)\\([^ *_~][^ :\n;(]*\\)\\s-*(\\s-*\\([^{};]*\\)\\s-*)[^;{/)]*[;{]" end t))
              (goto-char end)
            ;; TODO, store the complete match, for documentation
            ;; (message "Match #3 %s" (match-string 0))
            (goto-char (match-end 0))
            (let ((leadtok (match-string-no-properties 1))
                  (operator-str (match-string-no-properties 3))
                  (params (match-string-no-properties 6)))
              (setq method (match-string-no-properties 5))
;;               (message "DEBUG: leadtok '%s'" leadtok)
;;               (message "DEBUG: operator-str '%s'" operator-str)
;;               (message "DEBUG: method '%s'" method)
;;               (message "DEBUG: params '%s'" params)
              (if (string-match "operator" operator-str)
                  (setq method (concat "operator" method)))
              (if (and class-name (string-equal class-name method))
                  (message "Skipping  constructor %s" method)
                (if (or (string-match "return" leadtok)
                        (string-match "return" operator-str))
                    (message "Skipping return statement %s" method)
                  (if (string-equal "friend" leadtok)
                      (message "Skipping friendship declaration %s"
                               method)
                    ;; Handle overloaded operators
                    (if (string-match "operator\\(.*\\)$" method)
                        (setq method (format "operator_%s"
                                             (cppt-get-operator-name
                                              (match-string 1 method)
                                              params))))
                    (setq methods (cons method methods))
                    (message "Found %s C++ method %s"
                             (if class-name "public" "free") method))))))))
    methods))

(defun cppt-find-public-method-names ()
  "Find all public method declarations in the current buffer."
  (interactive)
  (let ((public-methods)
        (method)
        (start-of-class)
        (end-of-class)
        (start-public)
        (end-public)
        (cur-point)
        (class-name)
        (indentation)
        (type))
    (save-excursion
      (save-restriction
        (goto-char (point-min))
        (while (< (point) (point-max))
          (setq cur-point (point))
          ;; Find the start of the next class
          ;; If class is not found, just move to the end of the file
          (if (setq start-of-class
                    (re-search-forward
                     "^\\(\\s-*\\)\\(class\\|struct\\)\\s-+\\([^ \n;{]+\\)[^;{]+{" nil 1))
              (progn
                (setq indentation (match-string 1))
                (setq type (match-string 2))
                (setq class-name (match-string 3))
                (message "Found class name '%s'" class-name)))
          ;; Find all free method declarations before the class declaration
          (message "Locating non-class (free) methods")
          (goto-char cur-point)
          (setq public-methods
                (append public-methods (cppt-find-methods start-of-class)))
          (if (not start-of-class)
              (goto-char (point-max))
            (message "Locating public methods within class '%s'" class-name)
            (goto-char start-of-class)
            ;; Find }; indented the same way as the class declaration
            (setq end-of-class
                  (or (re-search-forward (format"^%s};" indentation) nil t)
                      (point-max)))
            (goto-char start-of-class)
            ;; Loop across all public method declarations in the class
            (while (and (setq start-public
                              (if (string-match "struct" type)
                                  (progn
                                    (setq type "")
                                    start-of-class)
                                (re-search-forward "^\\s-*public\\s-*:" nil t)))
                        (< start-public end-of-class))
              (goto-char start-public)
              (setq end-public
                    (or (re-search-forward
                         "^\\s-*\\(protected\\|private\\)\\s-*:" nil t)
                        end-of-class))
              (goto-char start-public)
              (setq public-methods
                    (append public-methods
                            (cppt-find-methods end-public class-name))))
            (goto-char end-of-class)))
        public-methods))))

(defun cppt-find-builddir (srcdir &optional test-file)
  "Return the corresponding build directory for SRCDIR according to
cppt-relative-compile. If cppt-relative-compile is nil, just return SRCDIR"
;;   (message "Resolving build directory for directory '%s'" srcdir)
   (setq cppt-source-location srcdir)
  (let ((build-dir (if cppt-relative-compile
                       (let* ((src-path (car cppt-relative-compile))
                              (build-path (cadr cppt-relative-compile))
                              (abbr-src (abbreviate-file-name srcdir)))
                         (if (string-match src-path abbr-src)
                             (let ((tmp-build (replace-match build-path t t abbr-src)))
				    (if (and cppt-relative-compile-versions 
					     (string-match "/[0-9][0-9.]+[0-9]/" 
							   tmp-build))
					(setq tmp-build (replace-match "/" t t tmp-build))
				      tmp-build)
                           tmp-build)))
                     srcdir)))
     (if (not build-dir) (setq build-dir srcdir))
;;      (message "Resolved build dir: '%s'" build-dir)
     (if (not test-file) 
	 (setq test-file "Makefile"))
     (while (and (file-exists-p build-dir)
		 (not (file-exists-p (concat build-dir test-file)))
		 (not (string-equal build-dir "/")))
;;       (message "No '%s' in  directory '%s'... trying parent directory"
;; 		test-file build-dir)
       (setq build-dir (cppt-get-parent-dir build-dir)))
    build-dir))


(defun cppt-find-containsdir (srcdir &optional test-file)
  "Return the first directory above or at this directory that contains
a Makefile or the file test-file if present"
     (if (not test-file) 
	 (setq test-file "Makefile"))
     (while (and (file-exists-p srcdir)
		 (not (file-exists-p (concat srcdir test-file)))
		 (not (string-equal srcdir "/")))
       (message "No '%s' in  directory '%s'... trying parent directory"
		test-file srcdir)
       (setq srcdir (cppt-get-parent-dir srcdir)))
     srcdir)

;; ----------------------------------------------
;; Interactive test code manipulation methods
;; ----------------------------------------------

(defun cppt-new-test-method ()
  "Interactively insert a new test method"
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let* ((default-name (cppt-default-method-name))
         (default-test-name
           (if (stringp default-name)
               (concat (if cppt-use-underscore-p "test_" "test")
                       default-name)))
         (input (read-string
                 (format "Insert new test method%s: "
                         (if (string= default-test-name "")
                             ""
                           (format " (default %s)" default-test-name)))))
         (method-name (if (string= input "")
                          (if (string= default-test-name "")
                              (error "No method name given")
                            default-test-name)
                        input)))
    (if (not (string= method-name ""))
        ;; Open the test header file
        (let* ((default-comment
                 (format "Test of the '%s' method." method-name))
               (input (read-string
                       (format "Comment (default \"%s\"): " default-comment)))
               (comment (if (string= input "") default-comment input))
               (file-name (cppt-find-file-name-root))
               (dir-name (cppt-buffer-dir-name))
               (class-name))
          (if (string-match "Suite$" file-name)
              (error "No corresponding code for test suite %s" file-name)
            (progn
              (if (string-match "\\(.*\\)\\([Aa]pp\\|[Tt]est\\)$" file-name)
                  (setq file-name (concat (match-string 1 file-name)
                                          (if cppt-use-underscore-p
                                              "test" "Test")))
                (setq file-name
                      (concat file-name
                              (if cppt-use-underscore-p "test" "Test"))))
              (if (not (string-match cppt-test-dir dir-name))
                  (setq dir-name (concat dir-name cppt-test-dir)))
              (setq file-name
                    (format "%s/%s" dir-name file-name))
              (if (and (file-exists-p (concat file-name ".h"))
                       (file-exists-p (concat file-name ".cpp")))
                  (progn
                    ;; Insert test declaration in header file
                    (find-file (concat file-name ".h"))
                    (setq class-name (cppt-find-class-name))
                    (goto-char (point-min))
                    (if (re-search-forward
                         (concat method-name "\\s-*\\s(") nil t)
                        (error "Method '%s' already exists" method-name)
                      (progn
                        (cppt-insert-test-method-declarations
                         (list method-name) comment)
                        (recenter nil)
                        ;; Insert default test implementation in src file
                        (if (< (count-windows) 2) (split-window-vertically))
                        (other-window 1)
                        (find-file (concat file-name ".cpp"))
                        (cppt-insert-test-methods
                         (list method-name) class-name comment)
                        (cppt-insert-run-method
                         (list method-name) class-name)
                        ;; Search to the implementation of the latest method
                        ;; added
                        (goto-char (point-min))
                        (search-forward
                         (format "NOTEST(\"%s\")" method-name))
                        (beginning-of-line)
                        (recenter nil))))
                (error "No test code for file '%s'"
                       buffer-file-name))))))))

(defun cppt-verify-test-methods ()
  "Verify that all public methods in the current class has test methods,
and if not, then interactively ask whether to add tests for each of them."
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let* ((start-file buffer-file-name)
         (src-file-name (cppt-find-file-name-root))
         (src-header-file)
         (src-dir (cppt-get-src-dir))
         (test-dir (cppt-get-test-dir))
         (test-header-file)
         (test-src-file)
         (class-name)
         (methods)
         (idx))
    (if (string-match "Suite$" src-file-name)
        (error "No corresponding code for test suite %s" src-file-name)
      (if (setq idx (string-match "\\(App\\|Test\\)$" src-file-name))
          (setq src-file-name (substring src-file-name 0 idx)))
      ;; Open the header file for the current class
      (delete-other-windows)
      (setq src-header-file (format "%s/%s.h" src-dir src-file-name))
      (find-file src-header-file)
      (setq class-name (cppt-find-class-name))
      (setq methods (cppt-find-public-method-names))
      ;; Open the header and src file for the test code
      (setq test-header-file (format "%s/%s%s.h"
                                     test-dir src-file-name
                                     (if cppt-use-underscore-p "test" "Test")))
      (setq test-src-file (format "%s/%s%s.cpp"
                                  test-dir src-file-name
                                  (if cppt-use-underscore-p "test" "Test")))
      (if (not (file-exists-p test-header-file))
          (cppt-switch-code-test)
        ;; Now find missing test methods
        (let ((missing))
          (while methods
            (unless (cppt-verify-method
                     (car methods) class-name test-header-file test-src-file)
              (setq missing t))
            (setq methods (cdr methods)))
          (if missing
              (find-file test-src-file)
            (message "Test methods found for all methods")
            (find-file start-file)))))))

(defun cppt-switch-code-test ()
  "Switch buffers between class code and class test-code.
If the current buffer holds class code, then the test code is opened.
If the current buffer holds the test code for a class, the code for the class
is opened"
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let* ((idx)
         (file-name (cppt-find-file-name-root))
         (directory-name (cppt-buffer-dir-name))
         (src-file)
         (hdr-file (concat file-name ".h")))
    (if (string-match "Suite$" file-name)
        (error "No corresponding code for test suite %s" file-name)
      (progn
        (if (setq idx (string-match "[aA]pp$" file-name))
            (setq file-name (substring file-name 0 idx)))
        (if (setq idx (string-match "[Tt]est$" file-name))
            (progn
              (setq src-file (concat (cppt-get-parent-dir directory-name)
                                     (substring file-name 0 idx) ".cpp"))
              (setq hdr-file (concat (cppt-get-parent-dir directory-name)
                                     (substring file-name 0 idx) ".h"))
              (find-file (if (file-exists-p src-file) src-file hdr-file)))
          (cppt-edit-test file-name directory-name))))))

(defun cppt-switch-code-test-method ()
  "Switch buffers between code- and and test-method.
If the current buffer holds class code, then the test code is opened.
If the current buffer holds the test code for a class, the code for the class
is opened"
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let* ((file-name (cppt-find-file-name-root))
         (is-test-p (or (string-match "[aA]pp$" file-name)
                        (string-match "[Tt]est$" file-name)))
         (default-name (cppt-default-method-name))
         (default-test-name (if (stringp default-name)
                                (if is-test-p
                                    default-name
                                  (cppt-test-method-name default-name))))
         (method-name (if (string= default-test-name "")
                          (error "No method name found")
                        default-test-name)))
    (cppt-switch-code-test)
    (unless (string= method-name "")
      (let* ((is-header-p (string-match "\\.h$" buffer-file-name))
             (reg-exp (if (and is-test-p
                               (string-match "^operator_?\\(.*\\)"
                                             method-name))
                          (cppt-get-operator (match-string 1 method-name))
                        (concat method-name "\\s-*([^}{]+{"))))
        ;; Go to the named method in the buffer
;;         (message "Looking for implementation of method '%s' using reg-exp '%s'"
;;                  method-name reg-exp)
        (goto-char (point-min))
        (if (or (search-forward-regexp (concat "::" reg-exp) nil t)
                (and (not is-header-p)
                     (if is-test-p
                         ;; Try the header file if no implementation is
                         ;; found in the source file
                         (progn
                           (cppt-toggle-header-src)
                           (goto-char (point-min))
                           (search-forward-regexp reg-exp nil t)))))
            (goto-char (match-beginning 0))
          (error "Could not find %smethod '%s'"
                 (if is-test-p "" "test for ")
                 method-name))))))

(defun cppt-toggle-header-src-method ()
  "Toggle btw the src and the header file for the current buffer and
go to the current method declaration or implementation.  This assumes
that the two files differ only in their extention (.h(pp) .cpp)."
  (interactive)
  (let* ((is-header-p (cppt-is-header-file-p buffer-file-name))
         (default-name (cppt-default-method-name t))
         (input (read-string
                 (format "Switch to %s of method%s: "
                         (if is-header-p "implementation" "declaration")
                         (if (string= default-name "")
                             ""
                           (format " (default %s)" default-name)))))
         (method-name (if (string= input "")
                          (if (string= default-name "")
                              (error "No method name given")
                            default-name)
                        input))
         (regexp (format (if is-header-p "::%s\\s-*(" "\\s-+%s\\s-*(")
                         method-name)))
    (cppt-toggle-header-src)
    (goto-char (point-min))
    (if (not (search-forward-regexp regexp nil t))
        (if is-header-p
            ;; Try searching for method without :: prefix in case it is
            ;; a namespace method
            (search-forward-regexp
             (format "\\s-+%s\\s-*([^)]*[),]" method-name) nil t)))))

(defun cppt-toggle-header-src ()
  "Toggle btw the src and the header file for the current buffer.
This assumes that the two files differ only in their extention (.h(pp)
.cpp)."
  (interactive)
  ;; There shouldn't be a need for reloading settings here
  (let* ((file-name buffer-file-name)
         (idx (string-match "[^.]+$" file-name))
         (ext (substring file-name idx))
         (trunk (substring file-name 0 idx))
         (h (concat trunk "h"))
         (hpp (concat trunk "hpp"))
         (cpp (concat trunk "cpp"))
         (target-file-name (if (string-match "cpp" ext)
                               (if (and (not (file-exists-p h))
                                        (file-exists-p hpp))
                                   hpp
                                 h)
                             cpp)))
    (if (or (file-exists-p target-file-name)
            (y-or-n-p (format "File '%s' does not exist. Create it? "
                              target-file-name)))
        (find-file target-file-name))))

(defun cppt-toggle-interface-headers ()
  "Toggle btw the header files for the interface and its implementation.
This assumes that the two files differ only in their i prefix"
  (interactive)
  ;; There should'nt be a need for reloading settings here
  (let* ((just-path (cppt-get-path buffer-file-name))
         (just-file-name (cppt-strip-path buffer-file-name))
         (idx (string-match "[^.]+$" just-file-name))
         (prefix (substring just-file-name 0 1))
         (target-file-name
          (format "%s%sh"
                  just-path
                  (if (string-match "i" prefix)
                      (substring just-file-name 1 idx)
                    (concat "i" (substring just-file-name 0 idx))))))
    (if (or (file-exists-p target-file-name)
            (y-or-n-p (format "File '%s' does not exist. Create it? "
                              target-file-name)))
        (find-file target-file-name))))


;; ----------------------------------------------
;; Make and run methods
;; ----------------------------------------------

(defun cppt-make-cmd (cmd &optional dir)
  "Execute make with cmd as argument"
  (let ((old-dir (cppt-buffer-dir-name)))
    (if (not dir) (setq dir (cppt-find-builddir old-dir)))
    (cd dir)
    (message "Executing compile command with args: '%s' within dir: '%s'"
             cmd dir)
    (if cmd
        (compile (concat "make " cmd))
      (call-interactively 'compile))
    (cd old-dir)
    (end-of-buffer-other-window nil)))

(defun cppt-compile ()
  "Find the lowermost directory with a makefile and interactively run compile"
  (interactive)
  (let ((compilation-read-command "t"))
    (cppt-make-cmd nil)))

(defun cppt-make (directory-name
                  module-name
                  test-file-name
                  &optional debug
                  &optional environment)
  "Run make within the given directory, then run the given class,
within the the named module."
  (let ((dir-name (cppt-find-builddir directory-name))
        (cmd (format "%s %s"
                     (if environment environment "")
                     test-file-name))
        (old-process-environment process-environment))
    (if debug (setq cmd (concat cmd debug)))
    ;; Temporarily Set LD_LIBRARY_PATH
    (if cppt-LD_LIBRARY_PATH
        (setq process-environment
              (cppt-set-lib-path cppt-LD_LIBRARY_PATH process-environment)))
    (cppt-make-cmd cmd dir-name)
    (end-of-buffer-other-window nil)
    ;; Reset LD_LIBRARY_PATH
    (if cppt-LD_LIBRARY_PATH
        (setq process-environment old-process-environment))))

(defun cppt-make-build ()
  "Execute make build"
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (cppt-make-cmd "clean all"))

(defun cppt-make-test ()
  "Execute make test"
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let* ((old-dir (cppt-buffer-dir-name))
	 (directory-name (cppt-find-builddir old-dir)))
    (if (or (string-match (format "%s/?$" cppt-test-dir)  directory-name)
            ;; Also support the old fastlib naming convention
            (string-match "tests/$" directory-name))
        (progn
          (setq directory-name (cppt-get-parent-dir directory-name))
          (cd directory-name))))
  (cppt-make-cmd "test")
  (cd old-dir))

(defun cppt-make-plain ()
  "Execute make without arguments"
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (cppt-make-cmd (if cppt-make-plain-args cppt-make-plain-args "")))

(defun cppt-run-test (&optional debug &optional prefix &optional environment)
  "Make and execute the test for the current buffer.
The buffer may contain a class to be tested, a class-test or a test suite."
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let ((file-name (cppt-find-file-name-root))
        (module-name (cppt-find-module-name))
        (test-dir (cppt-get-test-dir))
        (idx))
    (unless (or (string-match "[Tt]est$" file-name)
                (string-match "[Ss]uite$" file-name))
      (if (setq idx (string-match "[Tt]est[Aa]pp$" file-name))
          (setq file-name (format "%s%s"
                                  (substring file-name 0 idx)
                                  (if cppt-use-underscore-p "test" "Test")))
        (setq file-name
              (concat file-name
                      (if cppt-use-underscore-p "test" "Test")))))
    (cppt-make test-dir module-name
               (concat (if cppt-pretest-target
                           (concat cppt-pretest-target " "))
                       (if prefix prefix "run") file-name)
               debug
               environment)))

(defun cppt-run-test-debug ()
  "Execute the test with debug logging"
  (interactive)
  (cppt-run-test "Debug"))

(defun cppt-run-single-test (&optional debug &optional prefix)
  "Make and execute a single test method for the current buffer.
The buffer may contain a class to be tested, a class-test or a test suite."
  (interactive)
  (let* ((default-name (cppt-default-method-name))
         (input (read-string
                 (format "Run test for method%s: "
                         (if (string= default-name "")
                             ""
                           (format " (default %s)" default-name)))))
         (method-name (if (string= input "")
                          (if (string= default-name "")
                              (error "No method name given")
                            default-name)
                        input)))
    (if (not (string= method-name ""))
        (cppt-run-test
         debug prefix
         (format "METHOD=%s"
                 (if cppt-use-underscore-p
                     (concat "test_" method-name)
                   (concat "test" (cppt-upcase-first-letter
                                   method-name))))))))

(defun cppt-run-single-test-debug ()
  "Execute a single test method with debug logging"
  (interactive)
  (cppt-run-single-test "Debug"))

(defun cppt-run-single-test-purify ()
  "Execute a single test method with debug logging"
  (interactive)
  (cppt-run-single-test "" "purify"))

(defun cppt-run-test-purify ()
  "Execute the test with purify"
  (interactive)
  (cppt-run-test "" "purify"))

(defun cppt-run-test-purify-debug ()
  "Execute the test with purify in debug mode"
  (interactive)
  (cppt-run-test "Debug" "purify"))

(defun cppt-test-suite (&optional debug &optional prefix)
  "Make and execute the test suite for the module of the current buffer.
This also works if the current buffer is one of the test suites or classes."
  (interactive)
  ;; Re-Load any local user configurations
  (cppt-load-test-project "testproject.el")
  (let ((module-name (cppt-find-module-name))
        (directory-name (cppt-buffer-dir-name)))
    ;; Check whether we are in the test directory or the regular
    ;; module directory
    (unless (string-match (format "/%s/?$" cppt-test-dir) directory-name)
      (setq directory-name (concat directory-name "/" cppt-test-dir "/")))
    (cppt-make directory-name module-name
               (concat (if cppt-pretest-target
                           (concat cppt-pretest-target " "))
                       (if prefix prefix "run")
                       (cppt-build-suite-name module-name))
               debug)))

(defun cppt-suite-debug ()
  "Make and run the test suite with debug logging"
  (interactive)
  (cppt-test-suite "Debug"))

(defun cppt-suite-purify ()
  "Make and run the test suite with purify"
  (interactive)
  (cppt-test-suite "" "purify"))

(defun cppt-suite-purify-debug ()
  "Make and run the test suite with purify in debug mode"
  (interactive)
  (cppt-test-suite "Debug" "purify"))


;; ----------------------------------------------
;; Formatting methods
;; ----------------------------------------------

(defun cppt-indent (beg end)
  (message "Indenting code")
  (save-excursion
    (save-restriction
      (narrow-to-region beg end)
      (indent-region (point-min) (point-max) nil)
      (untabify (point-min) (point-max))
      (message "Indentation complete"))))

(defun cppt-indent-buffer ()
  "Indents and untabifies the current buffer."
  (interactive)
  (cppt-indent (point-min) (point-max)))

;; ----------------------------------------------
;; Automatic code generation methods
;; ----------------------------------------------

(defun cppt-insert-copy-disallowed ()
  "Insert private and unimplemented declarations of the copy CTOR and
assignment operator for the current class"
  (interactive)
  (end-of-line)
  (c-end-of-statement)
  ;; Find the class declaration and its name
  (if (not
       (search-backward-regexp
        "\\(?:class\\|struct\\)\\s-+\\(?:<[^>]+>\\s-+\\)?\\(\\w[A-Za-z0-9_]*\\)\\s-*\\(:[^:]\\|{\\|$\\)"
        nil t))
      (message "Could not find class declaration")
    (let* ((class-name (match-string 1))
           (start-of-class (point))
           (end-of-class (search-forward "};" nil t)))
      ;; find first private declaration
      (goto-char start-of-class)
      (if (not (search-forward-regexp "private\\s-*:" end-of-class t))
          ;; No private declaration, insert one
          (progn
            (goto-char end-of-class)
            (forward-line -1)
            (end-of-line)
            (insert "
private:")))
      ;; Insert empty and private copy CTOR and assignment operator
      (insert (format "

  // Assignment and copy of %s is disallowed, so the following are 
  // private and unimplemented
%s(const %s&);
%s &operator = (const %s&);
"
        class-name class-name class-name class-name class-name))
      (indent-region start-of-class (point) nil)
      (search-backward "// Assignment and copy of")
      (c-fill-paragraph))))

;; ----------------------------------------------
;; Documentation methods
;; ----------------------------------------------

(defun cppt-get-creation-date ()
  "If the file is registered in CVS use the first registration date,
otherwise use current date"
  (save-excursion
    (save-restriction
      (let ((file buffer-file-name)
            (time (current-time)))
        (if (vc-registered file)
            (progn
              (vc-call print-log file)
              (set-buffer "*vc*")
              (while (not (or (string-match "revision\\s-*1\\.1\\s-*$"
                                            (buffer-string))
                              (string-match "added, but not committed"
                                            (buffer-string))))
                (sleep-for 1))
              (if (string-match "added, but not committed" (buffer-string))
                  (format-time-string "%d %b %Y" time)
                (goto-char (point-min))
                (search-forward-regexp
                 "revision\\s-*1\\.1[^0-9d]+date:\\s-*\\([0-9]+\\)/0?\\([0-9]+\\)/0?\\([0-9]+\\) 0?\\([0-9]+\\):0?\\([0-9]+\\):0?\\([0-9]+\\)")
                (require 'parse-time)
                (let* ((year  (parse-integer (match-string 1)))
                       (month (parse-integer (match-string 2)))
                       (day   (parse-integer (match-string 3)))
                       (hour  (parse-integer (match-string 4)))
                       (min   (parse-integer (match-string 5)))
                       (sec   (parse-integer (match-string 6))))
                  (setq time (encode-time sec min hour day month year))))))
        (format-time-string "%d %b %Y" time)))))

(defun cppt-insert-doc-template (template &optional class-name type indent)
  "Substitute standard documentation tags found"
  (let ((start-point (point))
        (end-point))
    ;; Insert the documentation template
    (if indent (insert indent))
    (cppt-insert-template template)
    (setq end-point (point))
    (save-restriction
      ;; Substitute tags
      (narrow-to-region start-point end-point)
      (goto-char (point-min))
      (replace-string "TYPE" type)
      (goto-char (point-min))
      (replace-string "CLASS_NAME" class-name)
      (goto-char (point-min))
      (if indent
          (progn
            (end-of-line)
            (indent-region (point) (point-max) nil)
            (goto-char (point-min)))))))
      
(defun cppt-insert-file-doc ()
  "This will insert, a file documentation template at the very top
of the current file."
  (interactive)
  (let ((class-name nil) (namespace nil) (type))
    (save-excursion
      (goto-char (point-min))
      (while (re-search-forward
              "^\\s-*namespace\\s-+\\([^ \n\r\t;{]+\\)[^{;]*{" nil t)
        (progn
          (if namespace
              (setq namespace (format "%s::%s" namespace (match-string 1)))
            (setq namespace (match-string 1))
;;             (message "Found namespace %s" namespace)
            (setq type "namespace")
            (goto-char (match-end 0)))))
      (goto-char (point-min))
      (if (re-search-forward
           "^\\s-*\\(class\\|struct\\)\\s-+\\([^ \n\r\t:;{]+\\)[^;]*{" nil t)
          (progn
            (setq class-name
                  (if namespace
                      (format "%s::%s" namespace (match-string 2))
                    (match-string 2)))
            (setq type (match-string 1)))
;;         (message "Found %s %s" type class-name)
        (setq class-name namespace)))
    (if (y-or-n-p "Insert file documentation template? ")
        (progn
          (goto-char (point-min))
          (cppt-insert-doc-template cppt-file-doc-template class-name type)
          (re-search-forward "^ \\*\\s-*$")
          (if (and (string-match "\\.h$" buffer-file-name) class-name)
              (progn
                (insert
                 (format "Header file for the %s %s.\n * " class-name type))
                (search-backward type)
                (c-fill-paragraph)))))))

(defun cppt-insert-class-doc ()
  "This will insert, a class documentation template in front of the
first class or namespace declaration found from the current point"
  (interactive)
  (save-excursion
    (save-restriction
      (beginning-of-line)
      (search-forward "};")             ;Goto the end of the class declaration
      (re-search-backward
       "^\\(\\s-*\\)\\(class\\|struct\\|namespace\\)\\s-+\\([^ \n\r\t;{]+\\)[^;]*{"
       nil t)))
  (let ((class-name (match-string 3))
        (type (match-string 2))
        (indent (match-string 1)))
    (if class-name
        (progn
          (goto-char (match-beginning 0))
          (if (y-or-n-p
               (format "Insert documentation template for %s '%s'? "
                       type class-name))
              (progn
                (cppt-insert-doc-template
                 cppt-class-doc-template class-name type indent)
                (re-search-forward class-name)
                (end-of-line))))
      (error "No class, struct or namespace found in '%s'"
               buffer-file-name))))

(defun cppt-build-param-docs (declarations)
  "Build a string with documentation of all parameters in the
declarations string"
  (if (string-match "\\([^)]+\\))" declarations)
      (setq declarations (match-string 1 declarations)))
  (let ((documentation "")
        (type) (name))
    (while (string-match "\\s-*\\([^=,\n\r]+\\s-+[&*]?\\)\\([^ \t\n\r,=]+\\)[^,)]*,?\\([^{]*\\)"
                         declarations)
      (progn
        (setq type (match-string 1 declarations))
        (setq name (match-string 2 declarations))
        (setq declarations (match-string 3 declarations))
        (setq type (replace-regexp-in-string "[\n\r]+" "" type))
        (setq name (replace-regexp-in-string "[\n\r]+" "" name))
        ;; Collapse multipple whitespace
        (setq type (replace-regexp-in-string "\\s-\\s-+" " " type))
        ;; Remove trailing whitespace
        (setq type (replace-regexp-in-string "\\s-+$" "" type))
        (setq documentation (format "%s * @param %s a '%s' value\n"
                                    documentation name type))))
    documentation))

(defun cppt-build-exception-docs (declarations)
  "Build a string with documentation of all exceptions in the
declarations string"
  (let ((documentation "")
        (name))
    (while (and (stringp declarations)
                (string-match "\\s-*\\([^=,\n\r]+\\),?\\(.*\\)"
                              declarations))
      (progn
        (setq name (match-string 1 declarations))
        (setq declarations (match-string 2 declarations))
        (setq documentation (format "%s * @exception %s \n"
                                    documentation name))))
    documentation))

(defun cppt-insert-method-doc ()
  "This will insert, a method documentation template in front of the
first method declaration found from the current point"
  (interactive)
  ;; Search backward to the beginning of the declaration
  (end-of-line)
  (c-beginning-of-statement)
  (beginning-of-line)
  (let ((indentation)
        (dtor-flag)
        (method-name)
        (ret-type)
        (parameter-declarations)
        (start-point (point))
        (method-type)
        (exception-declarations))
    (cppt-skip-comments (point-max))
    (if (or
         ;; For method declarations
         (re-search-forward
;;             "^\\(\\s-*\\)\\(\\([^ /:,{}();\t\n*]\\|[^:]::[^:]\\)+\\)\\(\\([^({},:;]\\|[^:]::[^:]\\)*[ *&\n]+\\)\\([^ *_~][^ :\n;(]*\\)\\s-*([^)]*)[^;{/]*[;{]"
;;             "^\\(\\s-*\\)\\([^ {};\t\n]*\\s-+[^({};]*\\)\\s-+[*&]?\\s-*\\(~?\\)\\([^ *_~][^ :\n;(]*\\)\\s-*(\\([^;{]*\\))[^);{]*[;{]"
          "^\\(\\s-*\\)\\(\\([^/:,{}();\t\n*]\\|[^:]::[^:]\\)+\\)\\s-+[*&]?\\s-*\\(~?\\)\\([^ *_~][^ :\n;(]*\\)\\s-*(\\([^;{)]*\\))[^;{]*[;{]"
          (point-max) t)
         ;; For method implementations
         (and (goto-char start-point)
              (re-search-forward
               "^\\(\\s-*\\)\\([^({}\n\r\t;]*\\)\\s-+[*&]?[^: \t]+::\\(~?\\)\\([^ *_~][^ :\n;(]*\\)\\s-*(\\([^;{]*\\))[^);{]*[;{]"
               (point-max) t)))
        (progn
          (setq indentation (match-string 1))
          (setq ret-type (match-string 2))
          (setq dtor-flag (match-string 4))
          (setq method-name (match-string 5))
          (setq parameter-declarations (match-string 6))
          ;; Search for exception declarations
          (goto-char (match-beginning 0))
          (save-restriction
            (save-excursion
              (let ((end)
                    (start (point)))
                (search-forward ";")
                (setq end (match-beginning 0))
                (goto-char start)
                (if (re-search-forward
                     "throw\\s-*(\\(.*\\))"
                     end t)
                    (setq exception-declarations (match-string 1))))))
          ;; Remove method qualifiers from the return type
          (setq ret-type (replace-regexp-in-string
                          "\\(explicit\\|virtual\\|inline\\|static\\)\\s-*"
                          ""
                          ret-type))
          ;; Remove trailing whitespace from the return type
          (setq ret-type (replace-regexp-in-string "\\s-+$" "" ret-type))
          ;; Remove leading whitespace from the return type
          (setq ret-type (replace-regexp-in-string "^\\s-+" "" ret-type))
          (setq method-type
                (if (string-equal ret-type "")
                    (if (string-equal dtor-flag "")
                        "constructor"
                      "destructor")
                  "method"))
          ;; Add extra spaces to the indentation that were lost in the regexp
          (setq indentation (concat
                             (if (string-equal ret-type "") "  " " ")
                             indentation))
;;             (message "cppt-insert-method-doc: indentation '%s'" indentation)
;;             (message "cppt-insert-method-doc: ret-type '%s'" ret-type)
;;             (message "cppt-insert-method-doc: dtor-flag '%s'" dtor-flag)
;;             (message "cppt-insert-method-doc: method-name '%s'" method-name)
;;             (message "cppt-insert-method-doc: method-type '%s'" method-type)
;;             (message "cppt-insert-method-doc: parameter-declarations '%s'"
;;                      parameter-declarations)
;;             (message "cppt-insert-method-doc: exception-declarations '%s'"
;;                      exception-declarations)
          (save-restriction
            (save-excursion
              ;; Convert method name for overloaded operators
              (if (string-match "operator\\(.*\\)" method-name)
                  (progn
                    (setq method-name (format "overloaded %s"
                                              (match-string 1 method-name)))
                    (setq method-type "operator")))))
          (if (y-or-n-p
               (format "Insert documentation template for the '%s' %s? "
                       method-name method-type))
              (let ((end-point))
                (setq start-point (point))
                (save-restriction
                  ;; Insert the class documentation template
                  (insert
                   (format "%s%s" indentation cppt-method-doc-template))
                  (setq end-point (point))
                  (narrow-to-region start-point end-point)
                  ;; Substitute the different tags
                  (goto-char (point-min))
                  (replace-string "METHOD_NAME" method-name)
                  (cppt-replace-user-name)
                  (goto-char (point-min))
                  ;; Trim whitespace
                  (setq ret-type (replace-regexp-in-string
                                  "[\n\r]+" " " ret-type))
                  (setq ret-type (replace-regexp-in-string
                                  "\\s-\\s-+" " " ret-type))
                  (setq ret-type (replace-regexp-in-string
                                  "\\s-+$" "" ret-type))
                  (replace-string "RETURN_TYPE\n"
                                  (if (or (string-equal ret-type "")
                                          (string-equal ret-type "void"))
                                      ""
                                    (format " * @return a '%s' value\n"
                                            ret-type)))
                  (goto-char (point-min))
                  (replace-string
                   "PARAMETERS\n"
                   (cppt-build-param-docs parameter-declarations))
                  (goto-char (point-min))
                  (replace-string
                   "EXCEPTIONS\n"
                   (cppt-build-exception-docs exception-declarations))
                  ;; Indent comments properly
                  (goto-char (point-min))
                  (replace-regexp "^\\s-*\\*" (format "%s *" indentation))
                  (goto-char (point-max)))
                (indent-region (- start-point 1) (point) nil)
                (goto-char start-point)
                (search-forward method-name)
                (end-of-line)
                (insert (format "%s " method-type)))))
      (error "No method declaration found in '%s'" buffer-file-name))))

(defun cppt-cleanup-doc ()
  "Perform cleanup of documentation strings"
  (interactive)
  (save-excursion
    (save-restriction
      (goto-char (point-min))
      (query-replace-regexp "^\\(/\\*\\*\\)\\(\\*+\\|\\s-*\n\\s-*\\*\\*+\\)"
                            "\\1")
      (goto-char (point-min))
      (query-replace-regexp "\\*\\(\\*+\\)\\s-*\n\\s-*\\(\\*/\\)" "\\1\\2")
      (goto-char (point-min))
      (query-replace-regexp "\\(@\\(file\\|date\\) \\)\\s-+" "\\1")
      (goto-char (point-min))
      (while (search-forward-regexp
              "/\\*\\*\\([^/]\\|/[0-9A-Za-z]\\)+\\*/" nil t)
        (indent-region (match-beginning 0) (match-end 0) nil)))))

;; ----------------------------------------------
;; Load local project definitions
;; ----------------------------------------------

(defun cppt-load-test-project (&optional project-file)
  "Load the local test project file"
  (interactive)
  (if (not project-file)
      (setq project-file "testproject.el"))
  (let* ((directory-name
          (cppt-find-containsdir (cppt-buffer-dir-name) project-file))
         (project-file-name))
    (if (string-match (format "%s/$" cppt-test-dir) directory-name)
        (setq directory-name (cppt-get-parent-dir directory-name)))
    (setq project-file-name (format "%s/%s" directory-name project-file))
    (setq project-file-name
          (replace-regexp-in-string "//+" "/" project-file-name))
    ;; Look for the test project file
    (if (file-exists-p project-file-name)
        (progn
;;           (message "Loading local module configuration")
          (load-file project-file-name)))))


;; ---------------------------------------------------------------------
;; ---------------------------------------------------------------------
;; ---------------------------------------------------------------------
;; Create a minor mode
;; ---------------------------------------------------------------------
;; ---------------------------------------------------------------------
;; ---------------------------------------------------------------------
(defvar c++-test-minor-mode nil
  "Mode variable for Fast c++ unit test minor mode.")
(make-variable-buffer-local 'c++-test-minor-mode)


(if (not (assq 'c++-test-minor-mode minor-mode-alist))
    (setq minor-mode-alist
          (cons '(c++-test-minor-mode " Test")
                minor-mode-alist)))

;; Menu bar
(defvar c++-test-minor-mode-menu-map 
  (let ((map (make-sparse-keymap "Test")))
    (define-key map [indent-buffer]
      '("Indent and untabify buffer" . cppt-indent-buffer))

    (define-key map [lambda1] '("----"))

    (define-key map [insert-file-doc]
      '("Insert doc template for file..." . cppt-insert-file-doc))
    (define-key map [insert-class-doc]
      '("Insert doc template for class/struct/namespace..." .
        cppt-insert-class-doc))
    (define-key map [insert-method-doc]
      '("Insert doc template for method..." . cppt-insert-method-doc))

    (define-key map [lambda2] '("----"))

    (define-key map [insert-copy-disallowed]
      '("Insert empty copy CTOR and assignment operator..." .
        cppt-insert-copy-disallowed))

    (define-key map [lambda3] '("----"))

    (define-key map [cppt-compile]
      '("Run make with args..." . cppt-compile))
    (define-key map [make-build]
      '("Run 'make clean all'" . cppt-make-build))
    (define-key map [make-plain]
      '("Run 'make without arguments'" . cppt-make-plain))
    (define-key map [make-test]
      '("Run 'make test'" . cppt-make-test))

    (define-key map [lambda4] '("----"))

    (define-key map [suite-purify-debug]
      '("Run test suite in Purify in debug mode" . cppt-suite-purify-debug))
    (define-key map [suite-purify]
      '("Run test suite in Purify" . cppt-suite-purify))
    (define-key map [suite-debug]
      '("Run test suite in debug mode" . cppt-suite-debug))
    (define-key map [test-suite]
      '("Run test suite" . cppt-test-suite))

    (define-key map [lambda5] '("----"))

    (define-key map [run-test-purify-debug]
      '("Run tests for class in Purify in debug mode" .
        cppt-run-test-purify-debug))
    (define-key map [run-test-purify]
      '("Run tests for class in Purify" . cppt-run-test-purify))
    (define-key map [run-test-debug]
      '("Run tests for class in debug mode" . cppt-run-test-debug))
    (define-key map [run-test]
      '("Run tests for class" . cppt-run-test))

    (define-key map [lambda6] '("----"))

    (define-key map [run-single-test-debug]
      '("Run a single test in debug mode..." . cppt-run-single-test-debug))
    (define-key map [run-single-test]
      '("Run a single test for class..." . cppt-run-single-test))

    (define-key map [lambda7] '("----"))

    (define-key map [new-test-method]
      '("Create a new test method..." . cppt-new-test-method))
    (define-key map [verify-test-methods]
      '("Verify tests for all public methods" . cppt-verify-test-methods))

    (define-key map [lambda8] '("----"))

    (define-key map [switch-code-test]
      '("Toggle test- and source-file" . cppt-switch-code-test))
    (define-key map [switch-code-test-method]
      '("Toggle test- and source-method" . cppt-switch-code-test-method))
    (define-key map [toggle-header-src]
      '("Toggle header and source" . cppt-toggle-header-src))
    (define-key map [toggle-header-src-method]
      '("Toggle header and source with regards to the current method" .
        cppt-toggle-header-src-method))
    (define-key map [ff-find-other-file]
      '("Find related file, taking includes into consideration" .
        ff-find-other-file))
    (define-key map [toggle-interface-headers]
      '("Toggle header files for interface and implementation" .
        cppt-toggle-interface-headers))
    map)
  "Menu for C++ test minor mode")

;; ----------------------------------------------------------------------
;; Key bindings, the user should set the cppt-xxx-key variables
;; ----------------------------------------------------------------------
(defvar c++-test-minor-keymap 
  (let ((map (make-sparse-keymap)))
    (define-key map [menu-bar test]
      (cons "Test" c++-test-minor-mode-menu-map))
    (if cppt-use-function-keys-flag
        (progn
          (define-key map cppt-indent-buffer-key 'cppt-indent-buffer)
          (define-key map cppt-insert-file-doc-key 'cppt-insert-file-doc)
          (define-key map cppt-insert-class-doc-key 'cppt-insert-class-doc)
          (define-key map cppt-insert-method-doc-key 'cppt-insert-method-doc)
          (define-key map cppt-insert-copy-disallowed-key
            'cppt-insert-copy-disallowed)
          (define-key map cppt-compile-key 'cppt-compile)
          (define-key map cppt-make-build-key 'cppt-make-build)
          (define-key map cppt-make-plain-key 'cppt-make-plain)
          (define-key map cppt-make-test-key 'cppt-make-test)
          (define-key map cppt-suite-purify-debug-key 'cppt-suite-purify-debug)
          (define-key map cppt-suite-purify-key 'cppt-suite-purify)
          (define-key map cppt-suite-debug-key 'cppt-suite-debug)
          (define-key map cppt-test-suite-key 'cppt-test-suite)
          (define-key map cppt-run-test-purify-debug-key
            'cppt-run-test-purify-debug)
          (define-key map cppt-run-test-purify-key 'cppt-run-test-purify)
          (define-key map cppt-run-test-debug-key 'cppt-run-test-debug)
          (define-key map cppt-run-test-key 'cppt-run-test)
          (define-key map cppt-run-single-test-debug-key
            'cppt-run-single-test-debug)
          (define-key map cppt-run-single-test-key 'cppt-run-single-test)
          (define-key map cppt-new-test-method-key 'cppt-new-test-method)
          (define-key map cppt-verify-test-methods-key
            'cppt-verify-test-methods)
          (define-key map cppt-switch-code-test-key 'cppt-switch-code-test)
          (define-key map cppt-switch-code-test-method-key
            'cppt-switch-code-test-method)
          (define-key map cppt-toggle-header-method-key
            'cppt-toggle-header-src-method)
          (define-key map cppt-find-other-file-key 'ff-find-other-file)
          (define-key map cppt-toggle-interface-key
            'cppt-toggle-interface-headers)
          (define-key map cppt-toggle-header-key 'cppt-toggle-header-src)
          (define-key map [S-right] 'forward-sexp)
          (define-key map [S-left] 'backward-sexp)
          (define-key map [S-up] 'beginning-of-defun)
          (define-key map [S-down] 'end-of-defun)))
    map)
  "Keymap used for the c++ test minor mode")

(or (not (boundp 'minor-mode-map-alist))
    (assoc 'c++-test-minor-mode minor-mode-map-alist)
    (setq minor-mode-map-alist
          (cons (cons 'c++-test-minor-mode c++-test-minor-keymap)
                minor-mode-map-alist)))

(defun c++-test-minor-mode (&optional arg)
  "C++ unit test minor mode.  This minor mode is invoked automatically
as an extension of c++-mode.  It has extensive functionality for
writing, extending and running automated unit tests for C++ code.

The functionality may be roughly separated into three different areas:
  * Writing and extending unit tests
  * Executing the tests
  * Documenting code

The test commands all have the cppt- prefix, and are by default tied
to the different function keys:

\\{c++-test-minor-keymap}
"
  (interactive "P")
  (setq c++-test-minor-mode
        (if (null arg)
            (not c++-test-minor-mode)
          (> (prefix-numeric-value arg) 0)))
  (if c++-test-minor-mode
      (progn
        (message "Enabling c++ unit test minor mode")
        ;; Load any local user configurations
        ;; This is done every time c++-mode is invoked on a file.
        (cppt-load-test-project "testproject.el"))))


;; Always use this minor mode for c++-mode
(add-hook 'c++-mode-hook 'c++-test-minor-mode)

(defun cppt-makefile-make-interactive ()
  "Execute compile with argument taken from current word"
  (interactive)
  (let* ((regexp "^\\(\\w+\\)\\s-*:")
         (compile-command (concat "make "
                          (progn
                            (end-of-line)
                            (if (or (search-backward-regexp regexp nil t)
                                    (search-forward-regexp regexp nil t))
                                (match-string 1)
                              ""))))
         (compilation-read-command "t"))
    (call-interactively 'compile)
    (end-of-buffer-other-window nil)))

(defun cppt-makefile-mode-hook ()
  (local-set-key cppt-compile-key 'cppt-makefile-make-interactive)
  (local-set-key cppt-make-build-key 'cppt-make-build)
  (local-set-key cppt-make-plain-key 'cppt-make-plain)
  (local-set-key cppt-make-test-key 'cppt-make-test)
  (local-set-key cppt-suite-purify-debug-key 'cppt-suite-purify-debug)
  (local-set-key cppt-suite-purify-key 'cppt-suite-purify)
  (local-set-key cppt-suite-debug-key 'cppt-suite-debug)
  (local-set-key cppt-test-suite-key 'cppt-test-suite))
(add-hook 'makefile-mode-hook 'cppt-makefile-mode-hook)


(provide 'cpptest)
