#!/bin/bash
$VALGRIND ./searchlib_attribute_searchable_adapter_test_sh
rm -f ./my_logctl_file
VESPA_LOG_CONTROL_FILE=./my_logctl_file VESPA_LOG_LEVEL=all $VALGRIND ./searchlib_attribute_searchable_adapter_test_app
