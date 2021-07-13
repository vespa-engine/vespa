# Execute from this directory to build the command-line client to bin/vespa
#export GOPATH="/Users/bratseth/development/vespa-engine/vespa/client/go"
export GOPATH=`pwd`
cd "$GOPATH/src/github.com/vespa-engine/vespa"
go install
