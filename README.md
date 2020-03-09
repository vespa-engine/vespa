<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

[![#Vespa](https://vespa.ai/img/VespaLogoBlack.png)](https://vespa.ai)

The big data serving engine - Store, search, rank and organize big data at user serving time.
Vespa is an engine for low-latency computation over large data sets.
It stores and indexes your data and executes distributed queries including evaluation of
machine-learned models over many data points in real time.

Travis-CI build status: [![Build Status](https://travis-ci.org/vespa-engine/vespa.svg?branch=master)](https://travis-ci.org/vespa-engine/vespa)

## Table of contents

- [Background](#background)
- [Install](#install)
- [Usage](#usage)
- [Contribute](#contribute)
- [Building](#building)
- [License](#license)
   
## Background


Use cases such as search, recommendation and personalization need to select a subset of data in a large corpus,
evaluate machine-learned models over the selected data, organize and aggregate it and return it, typically in less
than 100 milliseconds, all while the data corpus is continuously changing. 

This is hard to do, especially with large corpuses that needs to be distributed over multiple nodes and evaluated in 
parallel. Vespa is a platform which performs these operations for you. It has been in development for many years 
and is used on a number of large internet services and apps which serve hundreds of thousands of queries from 
Vespa per second.

## Install

To get started using Vespa pick one of the quick start documents:

- [Run on a Mac or Linux machine using Docker](https://docs.vespa.ai/documentation/vespa-quick-start.html)
- [Run on a Windows machine using Docker](https://docs.vespa.ai/documentation/vespa-quick-start-windows.html)
- [Run on a Mac or Linux machine using VirtualBox+Vagrant](https://docs.vespa.ai/documentation/vespa-quick-start-centos.html)
- [Multinode install on AWS EC2](https://docs.vespa.ai/documentation/vespa-quick-start-multinode-aws.html)
- [Multinode install on AWS ECS](https://docs.vespa.ai/documentation/vespa-quick-start-multinode-aws-ecs.html)

## Usage 

- The application created in the quickstart is fully functional and production ready, but you may want to [add more nodes](https://docs.vespa.ai/documentation/multinode-systems.html) for redundancy.
- Try the [Blog search and recommendation tutorial](https://docs.vespa.ai/documentation/tutorials/blog-search.html) to learn more about using Vespa
- See [developing applications](https://docs.vespa.ai/documentation/jdisc/developing-applications.html) on adding your own Java components to your Vespa application.
- [Vespa APIs](https://docs.vespa.ai/documentation/api.html) is useful to understand how to interface with Vespa
- Explore the [sample applications](https://github.com/vespa-engine/sample-apps/tree/master)

Full documentation is available on [https://docs.vespa.ai](https://docs.vespa.ai).

## Contribute

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) to learn how to contribute.

If you want to contribute to the documentation, see
[https://github.com/vespa-engine/documentation](https://github.com/vespa-engine/documentation)


## Building

You do not need to build Vespa to use it, but if you want to contribute you need to be able to build the code.
This section explains how to build and test Vespa. To understand where to make changes, see [Code-map.md](Code-map.md).
Some suggested improvements with pointers to code are in [TODO.md](TODO.md).

### Set up the build environment

C++ and Java building is supported on CentOS 7. The Java source can also be built on any platform having Java 11 and Maven installed. 
We recommend using the following environment: [Create C++ / Java dev environment on CentOS using VirtualBox and Vagrant](vagrant/README.md).
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
    
## License

Code licensed under the Apache 2.0 license. See [LICENSE](LICENSE) for terms.
