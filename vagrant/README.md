
# Create C++ dev environment on CentOS using VirtualBox and Vagrant

## Prerequisites
* [Install VirtualBox](https://www.virtualbox.org/wiki/Downloads)
* [Install Vagrant](https://www.vagrantup.com/downloads.html)

## Create dev environment

### Change working directory to <vespa-source>/vagrant
    cd <vespa-source>/vagrant

### Start and provision the environment
    vagrant up

### Connect to machine via SSH
SSH agent forwarding is enabled to ensure easy interaction with GitHub inside the machine.

    vagrant ssh

### Checkout vespa source inside machine
This is needed in order to compile and run tests fast on the local file system inside the machine.

    git clone git@github.com:vespa-engine/vespa.git


## Build C++ modules
Please follow the instructions described [here](../README.md).
