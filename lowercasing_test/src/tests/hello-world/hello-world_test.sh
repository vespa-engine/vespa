#!/bin/bash

. ../../binref/env.sh
$BINREF/compilejava HelloWorldLocal.java
sh dotest.sh
