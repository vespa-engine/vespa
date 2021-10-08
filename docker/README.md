<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Building Vespa RPM on Docker (OS X and Linux)

## Installing docker
[Docker installation](https://docs.docker.com/engine/installation/)

*On Linux, the default storage device is devicemapper with loopback device and max 10GB container size. This size is too small for a full build. Please see [here](http://www.projectatomic.io/blog/2016/03/daemon_option_basedevicesize/) and [here](http://www.projectatomic.io/blog/2015/06/notes-on-fedora-centos-and-docker-storage-drivers/) to overcome this limitation.*


## Building Vespa RPM
Execute ```./build-vespa.sh <Vespa version number>``` to build Vespa from this source code.

The produced rpms will be available in this folder after compilation.
The version number will be compiled into binaries and must be on the form x.y.z, like 7.1.2 -
but has no other meaning than that.


## Troubleshooting
- Use ```docker logs CONTAINER``` for output - useful if the commands above fail.

- If the build fails, start from scratch and build again. Clean local docker if docker image disk full:
    - ```docker rm -v $(docker ps -a -q -f status=exited)```
    - ```docker rmi $(docker images -f "dangling=true" -q)```

- _Directory renamed before its status could be extracted_ can be caused by [1219](https://github.com/docker/for-mac/issues/1219) - workaround (from the issue): "It may be an overlay storage driver issue - you can add ```{"storage-driver":"aufs"}``` in the advanced daemon preferences pane and see if that makes a difference."
