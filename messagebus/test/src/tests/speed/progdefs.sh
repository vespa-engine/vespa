#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
prog server cpp "" "./messagebus_test_cpp-server-speed_app"
prog server java "" "../../binref/runjava JavaServer"
