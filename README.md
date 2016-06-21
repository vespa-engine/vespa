# Vespa

Vespa is an engine for low-latency computation over large data sets.
It stores and indexes your data such that queries, selection and processing over the
data can be performed at serving time.

This README describes how to build and develop the Vespa engine. If you want to use Vespa
you can go to the
[quick start guide](http://yahoo.github.io/vespa/vespa-quick-start.html), or take a
look at our
[user documentation](http://yahoo.github.io/vespa/vespatoc.html).

## Getting started developing

### Setting up local git config

    git config --global user.name "John Doe"
    git config --global user.email johndoe@host.com

### Setting up build environment

    sudo yum -y  install epel-release
    # TODO: Install build deps or depend on Build-Require in .spec file?

### Building Java modules

Java modules can be built on any environment having Java and Maven:

    sh bootstrap.sh
    mvn install

### Building C++ modules

C++ building is currently supported on CentOS 7:

TODO: List required build dependencies

    sh bootstrap.sh
    cmake .
    make
    make test

### Create RPM packages

    sh dist.sh VERSION && rpmbuild -ba ~/rpmbuild/SPECS/vespa-VERSION.spec

## Running Vespa on a local machine

* OS X : See [node-admin/README_MAC.md](node-admin/README_MAC.md)
* Linux : See [node-admin/README_LINUX.md](node-admin/README_LINUX.md)

Code licensed under the Apache 2.0 license. See LICENSE file for terms.

## Documenting your features

See [README-documentation.md](README-documentation.md).
