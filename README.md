<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Vespa
Vespa is an engine for low-latency computation over large data sets.
It stores and indexes your data such that queries, selection and processing over the
data can be performed at serving time.

This README describes how to build and develop the Vespa engine. To get started, read the
[quick start](http://docs.vespa.ai/documentation/vespa-quick-start.html), or find the full
documentation at http://docs.vespa.ai/.

Code licensed under the Apache 2.0 license. See [LICENSE](LICENSE) for terms.

Travis-CI build status: [![Build Status](https://travis-ci.org/vespa-engine/vespa.svg?branch=master)](https://travis-ci.org/vespa-engine/vespa)

## Get started developing

### Setup build environment
C++ building is supported on CentOS 7. The Java source can be built on any platform having Java 8 and Maven installed. 
We recommend using the following environment: [Create C++ dev environment on CentOS using VirtualBox and Vagrant](vagrant/README.md).
You can also setup CentOS 7 natively and install the following build dependencies:

    sudo yum-config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-7/group_vespa-vespa-epel-7.repo
    sudo yum -y install epel-release centos-release-scl yum-utils
    sudo yum -y install ccache \
        rpm-build
    yum-builddep -y <vespa-source>/dist/vespa.spec

### Build Java modules

    export MAVEN_OPTS="-Xms128m -Xmx1024m"
    source /opt/rh/rh-maven35/enable
    bash bootstrap.sh java
    mvn -T <num-threads> install

### Build C++ modules
Replace `<build-dir>` with the name of the directory in which you'd like to build Vespa.
Replace `<source-dir>` with the directory in which you've cloned/unpacked the source tree.

    bash bootstrap-cpp.sh <source-dir> <build-dir>
    cd <build-dir>
    make -j <num-threads>
    ctest3 -j <num-threads>

### Create RPM packages
    sh dist.sh VERSION && rpmbuild -ba ~/rpmbuild/SPECS/vespa-VERSION.spec


## Run Vespa on a local machine
A basic, single-node install is found in the 
[quick start](http://docs.vespa.ai/documentation/vespa-quick-start.html).
For multi-node and using Node Admin, read [node-admin/README.md](node-admin/README.md).

## Write documentation
Update user documentation at https://github.com/vespa-engine/documentation
