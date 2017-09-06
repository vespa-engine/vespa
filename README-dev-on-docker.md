# Developing Vespa on Docker

The purpose of this document is to describe how to set up a combined development and
run-time environment for (iteratively) developing and testing Vespa.

### Start the docker container

    DOCKER_IMAGE=vespaengine/vespa-dev:latest
    SOURCE_ROOT=${HOME}/vespa/vespa
    APP_ROOT=${HOME}/vespa/sample-apps

    docker run -d -p 8080:8080 -p 8998:8998 -p 10001:10001 \
                -v ${HOME}/.ccache:/root/.ccache:delegated \
                -v ${HOME}/.m2:/root/.m2:delegated \
                -v ${SOURCE_ROOT}:/source:delegated \
                -v ${APP_ROOT}:/apps:delegated \
                --privileged \
                --name vespa \
                ${DOCKER_IMAGE} \
                /bin/sh -c "tail -f /dev/null"

Change the above paths and container name to reflect your system.

The ports defined here are for connecting to the Vespa service from the host
machine. 8080 is the default HTTP port for querying and feeding to Vespa. The
other ports are optional, the ones here are for Java remote debugging and
YourKit profiling.

We map in multiple directories from the host to the docker container. They
are all mapped with `:delegated` which speeds up writing to disk in the container. This
is helpful for instance for compiling.

The first directory is the `.ccache` directory. Having this directory on the host
system significantly speeds up C++ compiling. The first C++ build will be slow, but
subsequent builds will greatly benefit from this cache.

The `.m2` directory is the maven repository, and if Java is built in the container
artifacts will be populated to the host repository as well.

The source root is the Vespa source code, and the apps root is the root
directory for any Vespa applications you would like to try out. Here it is set
to the sample-apps directory.

### Build code

Vespa has both Java and C++ code. You can build Java either on the host or inside
the container, but host building will typically be faster.

    $ ./bootstrap.sh java
    $ mvn -nsu -T 4 clean install -DskipTests -Dmaven.javadoc.skip=true

To build C++ code, enter the docker container, create a build directory and start
the build.

    $ docker exec -it vespa bash
    $ mkdir /build
    $ cd /build
    $ /source/bootstrap-cpp.sh /source /build
    $ make -j 4     # 4 = number of threads

### Install

    $ cd /build
    $ make install

### Post install

The above will install Vespa on the system, but to actually run Vespa you need
to create the `vespa` user and group as that is not done in the docker image:

    $ groupadd -r vespa
    $ useradd -r -g vespa -d /opt/vespa -s /sbin/nologin -c "Vespa user" vespa
    $ chown -R vespa:vespa /opt/vespa

### Run Vespa

First start the config server:

    $ /opt/vespa/bin/vespa-start-configserver

Wait a short while (20 seconds or so should suffice) and then start Vespa
services:

    $ /opt/vespa/bin/vespa-start-services

### Build and deploy a test app

Any Vespa application or sample app can be built on the host system. Assuming it
has been, deploy as follows in the container:

    $ cd <path-to-application-root>
    $ /opt/vespa/bin/vespa-deploy prepare target/application
    $ /opt/vespa/bin/vespa-deploy activate

You should now be able to query Vespa from the host system.

### Changing code and rebuilding

Edit code on the host system. After that you can build as above or just the
necessary modules if you now what you are doing. Java can be built on the host
system, C++ needs to be built in the container.

To install changes, stop Vespa, install and start.

    $ /opt/vespa/bin/vespa-stop-services && /opt/vespa/bin/vespa-stop-configserver
    $ make install
    $ /opt/vespa/bin/vespa-start-configserver && sleep 20 && /opt/vespa/bin/vespa-start-services

Alternatively, you can install files directly (instead of make install) to
avoid checking the C++ build:

    $ cmake3 -P /build/cmake_install.cmake


