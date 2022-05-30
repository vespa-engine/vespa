#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
TIME=time

$TIME ./vespalib_benchmark_test_app vespalib::ParamByReferenceVectorInt 200000 1
$TIME ./vespalib_benchmark_test_app vespalib::ParamByValueVectorInt 4000 1
$TIME ./vespalib_benchmark_test_app vespalib::ParamByReferenceVectorString 30000 1
$TIME ./vespalib_benchmark_test_app vespalib::ParamByValueVectorString 40 1
$TIME ./vespalib_benchmark_test_app vespalib::ReturnByReferenceVectorString 10 1
$TIME ./vespalib_benchmark_test_app vespalib::ReturnByValueVectorString 10 1
$TIME ./vespalib_benchmark_test_app vespalib::ReturnByValueMultiVectorString 10 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockSystem 1000 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockGToD 1000 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockGToD 20000 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockREALTIME 1000 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockMONOTONIC 1000 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockMONOTONIC_RAW 1000 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockPROCESS_CPUTIME_ID 2500 1
$TIME ./vespalib_benchmark_test_app vespalib::ClockTHREAD_CPUTIME_ID 2500 1
$TIME ./vespalib_benchmark_test_app vespalib::CreateVespalibString 20000 1
