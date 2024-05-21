<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://vespa.ai/assets/vespa-ai-logo-heather.svg">
  <source media="(prefers-color-scheme: light)" srcset="https://vespa.ai/assets/vespa-ai-logo-rock.svg">
  <img alt="#Vespa" width="200" src="https://vespa.ai/assets/vespa-ai-logo-rock.svg" style="margin-bottom: 25px;">
</picture>
<br/><br/>

Search, make inferences in, and organize vectors, tensors, text and structured data, at serving time and any scale.

This repository contains all the code required to build and run all of Vespa yourself,
and where you can see all development as it is happening.
All the content in this repository is licensed under the Apache 2.0 license.

A new release of Vespa is made from this repository's master branch every morning CET Monday through Thursday.
Build status: [![Vespa Build Status](https://api.screwdriver.cd/v4/pipelines/6386/build-vespa/badge)](https://cd.screwdriver.cd/pipelines/6386)

- Home page: [https://vespa.ai](https://vespa.ai)
- Documentation: [https://docs.vespa.ai](https://docs.vespa.ai)
- Continuous build: [https://factory.vespa.oath.cloud](https://factory.vespa.oath.cloud)
- Run applications in the cloud for free: [https://cloud.vespa.ai](https://cloud.vespa.ai)

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

This is hard to do, especially with large data sets that needs to be distributed over multiple nodes and evaluated in
parallel. Vespa is a platform which performs these operations for you with high availability and performance.
It has been in development for many years and is used on a number of large internet services and apps which serve
hundreds of thousands of queries from Vespa per second.

## Install

Deploy your Vespa applications to the cloud service: [https://cloud.vespa.ai](https://cloud.vespa.ai),
or run your own Vespa instance: [https://docs.vespa.ai/en/getting-started.html](https://docs.vespa.ai/en/getting-started.html)

## Usage

- The application created in the getting started guides linked above are fully functional and production ready, but you may want to [add more nodes](https://docs.vespa.ai/en/multinode-systems.html) for redundancy.
- See [developing applications](https://docs.vespa.ai/en/developer-guide.html) on adding your own Java components to your Vespa application.
- [Vespa APIs](https://docs.vespa.ai/en/api.html) is useful to understand how to interface with Vespa
- Explore the [sample applications](https://github.com/vespa-engine/sample-apps/tree/master)
- Follow the [Vespa Blog](https://blog.vespa.ai/) for feature updates / use cases

Full documentation is at [https://docs.vespa.ai](https://docs.vespa.ai).

## Contribute

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) to learn how to contribute.

If you want to contribute to the documentation, see
[https://github.com/vespa-engine/documentation](https://github.com/vespa-engine/documentation)

## Building

You do not need to build Vespa to use it, but if you want to contribute you need to be able to build the code.
This section explains how to build and test Vespa. To understand where to make changes, see [Code-map.md](Code-map.md).
Some suggested improvements with pointers to code are in [TODO.md](TODO.md).

### Development environment

C++ and Java building is supported on AlmaLinux 8.
The Java source can also be built on any platform having Java 17 and Maven installed.
Use the following guide to set up a complete development environment using Docker
for building Vespa, running unit tests and running system tests:
[Vespa development on AlmaLinux 8](https://github.com/vespa-engine/docker-image-dev#vespa-development-on-almalinux-8).

### Build Java modules

    export MAVEN_OPTS="-Xms128m -Xmx1024m"
    ./bootstrap.sh java
    mvn install --threads 1C

Use this if you only need to build the Java modules, otherwise follow the complete development guide above.

## License

Code licensed under the Apache 2.0 license. See [LICENSE](LICENSE) for terms.

