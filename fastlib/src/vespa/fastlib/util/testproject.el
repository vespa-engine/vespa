;; Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
;; testproject.el

;;  Local configurations for the cpptest Emacs unit-test framework

;; Author: Nils Sandøy <nils.sandoy@fast.no>

(message "Setting local test configuration")

(setq cppt-test-dir "tests")

(setq cppt-use-underscore-p t)

(setq cppt-extra-object-files '())

;; We have no parameters to our test executables
(setq cppt-test-parameters "")

;; Use -d to turn on debug mode
(setq cppt-test-dbflags "-d")
