# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Execute from this directory to build the command-line client to bin/vespa
cd util
go test
cd ..

cd cmd
go test
cd ..

export GOBIN=`pwd`/bin
go install

