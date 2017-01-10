
# Building and running Vespa on Docker (OS X and Linux)

## Installing docker
[Docker installation](https://docs.docker.com/engine/installation/)

*On Linux, the default storage device is devicemapper with loopback device and max 10GB container size. This size is too small for a full build. Please see [here](http://www.projectatomic.io/blog/2016/03/daemon_option_basedevicesize/) and [here](http://www.projectatomic.io/blog/2015/06/notes-on-fedora-centos-and-docker-storage-drivers/) to overcome this limitation.*

## Building the Vespa RPM
Execute ```./build-vespa.sh <Vespa version number>``` to build Vespa from this source code.

The produced rpms will be available in this folder after compiliation.

### Troubleshooting
If the build fails, start from scratch: ```docker rmi -f vesparun vespabuild``` - then build again


## Running Vespa
Execute ```./run-vespa.sh <Vespa version number>``` to start Vespa.

This will create a Docker image which has the rpms from the build step (or downloaded rpms to this folder) installed. Vespa will be started inside the container.

*On OS X, the container runs inside the Docker VM. Execute ```docker-machine ssh vespa-docker-machine``` to enter the VM. The services can also be reached directly from the host on the IP given by ```docker-machine ip vespa-docker-machine```*

## Building Vespa inside a Docker container
Execute ```./enter-build-container.sh``` to enter the Vespa build environment inside a Docker container.

The container is entered at the root of the Vespa source repository. Follow the build sections in [README.md](https://github.com/yahoo/vespa/blob/master/README.md) to build and test.

