<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Create C++ dev environment on CentOS using VirtualBox and Vagrant

## Prerequisites
* [Install VirtualBox](https://www.virtualbox.org/wiki/Downloads)
* [Install Vagrant](https://www.vagrantup.com/downloads.html)

## Create dev environment

#### 1. Change working directory to &lt;vespa-source&gt;/vagrant

    cd <vespa-source>/vagrant

#### 2. Install Vagrant VirtualBox Guest Additions plugin
This is required for mounting shared folders and get mouse pointer integration and seamless windows in the virtual CentOS desktop.

    vagrant plugin install vagrant-vbguest

#### 3. Start and provision the environment

    vagrant up

#### 4. Connect to machine via SSH
SSH agent forwarding is enabled to ensure easy interaction with GitHub inside the machine.

    vagrant ssh

#### 5. Checkout vespa source inside virtual machine
This is needed in order to compile and run tests fast on the local file system inside the virtual machine.

    git clone git@github.com:vespa-engine/vespa.git

## Build C++ modules
Please follow the build instructions described [here](../README.md#build-c-modules).
Skip these steps if doing development with CLion.


## Build and Develop using CLion
CLion is installed as part of the environment and is recommended for C++ development.

#### 1. Bootstrap C++ building
cd to the vespa/ directory created by git clone and execute:

    ./bootstrap.sh java
    ./bootstrap-cpp.sh . .

#### 2. Start CLion
Open a terminal inside the virtual CentOS desktop (password is "vagrant") and run:

    clion

When promoted, configure c and cpp compilers to 

    /opt/rh/devtoolset-7/root/usr/bin/cc
    /opt/rh/devtoolset-7/root/usr/bin/c++    

#### 3. Open the Vespa Project
Go to *File* -> *Open* and choose &lt;vespa-source>&gt;/CMakeLists.txt.

#### 4. Set compiler threads
Go to *File* -> *Settings* -> *Build, Execution, Deployment* -> *CMake*.
Under *Build Options* specify "-j 4" and click *Apply*.

#### 5. Run bootstrap again

    ./bootstrap-cpp.sh . .

(Some of the changes made by it are undone by clion on the first startup.)

#### 5. Build all modules
Choose target **all_modules** from the set of build targets at the top right and click build.

