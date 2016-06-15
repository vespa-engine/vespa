#!/bin/bash
$VALGRIND ./searchcore_feedhandler_test_app
rm -rf mytlsdir
rm -rf myfilecfg
