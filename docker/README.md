
# Building and running Vespa on Docker (OS X and Linux)

## Installing docker
[Docker installation](https://docs.docker.com/engine/installation/)

*On OS X, the native Docker engine has NOT been tested. Please use the [Docker Toolbox](https://www.docker.com/products/docker-toolbox).*

## Building Vespa
*On OS X, execute ```source osx-setup-docker-machine.sh``` to setup the Docker VM in which to run Docker.*

Execute ```./build-vespa.sh <Vespa version number>``` to build Vespa from this source code.

The produced rpms will be available in this folder after compiliation.

## Running Vespa
*On OS X, execute ```source osx-setup-docker-machine.sh``` to setup the Docker VM in which to run Docker.*

Execute ```./run-vespa.sh <Vespa version number>``` to start Vespa.

This will create a Docker image which has the rpms from the build step installed. Vespa will be started inside the container.

*On OS X, the container runs inside the Docker VM. Execute ```docker-machine ssh vespa-docker-machine```to enter the VM.*
