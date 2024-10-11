<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://assets.vespa.ai/logos/Vespa-logo-green-RGB.svg">
  <source media="(prefers-color-scheme: light)" srcset="https://assets.vespa.ai/logos/Vespa-logo-dark-RGB.svg">
  <img alt="#Vespa" width="200" src="https://assets.vespa.ai/logos/Vespa-logo-dark-RGB.svg" style="margin-bottom: 25px;">
</picture>
<br/><br/>

[![Build status](https://badge.buildkite.com/34f7cb35b91da4f929794c5fd7aa722fc15ca0224ad240270b.svg)](https://buildkite.com/vespaai/vespa-engine-vespa)
![GitHub License](https://img.shields.io/github/license/vespa-engine/vespa)
![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fyahoo%2Fvespa%2Fparent%2Fmaven-metadata.xml)



Search, make inferences in and organize vectors, tensors, text and structured data, at serving time and any scale.

This repository contains all the code required to build and run all of Vespa yourself,
and where you can see all development as it happens.
All the content in this repository is licensed under the Apache 2.0 license.

A new release of Vespa is made from this repository's master branch every morning CET Monday through Thursday.

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

This is hard to do, especially with large data sets that need to be distributed over multiple nodes and evaluated in
parallel. Vespa is a platform that performs these operations for you with high availability and performance.
It has been in development for many years and is used on several large internet services and apps which serve
hundreds of thousands of queries from Vespa per second.

## Install

Deploy your Vespa applications to the cloud service: [https://cloud.vespa.ai](https://cloud.vespa.ai),
or run your own Vespa instance: [https://docs.vespa.ai/en/getting-started.html](https://docs.vespa.ai/en/getting-started.html)

## Usage

- The application created in the getting started guides linked above is fully functional and production-ready, but you may want to [add more nodes](https://docs.vespa.ai/en/multinode-systems.html) for redundancy.
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
The Java source can also be built on any platform having Java 17 and Maven 3.8+ installed.
Use the following guide to set up a complete development environment using Docker
for building Vespa, running unit tests and running system tests:
[Vespa development on AlmaLinux 8](https://github.com/vespa-engine/docker-image-dev#vespa-development-on-almalinux-8).

#### Java environment for Mac
1. Install [JDK17](https://openjdk.org/projects/jdk/17/), 
   [Maven Version Manager](https://bitbucket.org/mjensen/mvnvm/src/master/) and [jEnv](https://www.jenv.be)
   through [Homebrew](https://brew.sh/).
```sh
brew install jenv mvnvm openjdk@17
```

2. For the system Java wrappers to find this JDK, symlink it with
```sh
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

3. Follow "Configure your shell" in https://www.jenv.be. Configuration is shell specific. For `zsh` use the below commands:
```sh
echo 'export PATH="$HOME/.jenv/bin:$PATH"' >> ~/.zshrc
echo 'eval "$(jenv init -)"' >> ~/.zshrc
eval "$(jenv init -)"
jenv enable-plugin export
exec $SHELL -l
```

4. Add JDK17 to jEnv
```sh
jenv add $(/usr/libexec/java_home -v 17)
```

5. Verify configuration with Maven by executing below command in the root of the source code.
   Output should refer to the JDK and Maven version specified in the [.java-version](.java-version) and [mvnvm.properties](mvnvm.properties).
```sh
mvn -v
```

### Build Java modules

    export MAVEN_OPTS="-Xms128m -Xmx1024m"
    ./bootstrap.sh java
    mvn install --threads 1C

Use this if you only need to build the Java modules, otherwise follow the complete development guide above.

## License

Code licensed under the Apache 2.0 license. See [LICENSE](LICENSE) for terms.
