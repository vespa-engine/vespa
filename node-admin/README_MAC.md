# Setting up Docker on OS X
Install Docker Toolbox according to the procedure on [https://docs.docker.com/mac/step_one](https://docs.docker.com/mac/step_one).

# Running Vespa on OS X 

## Starting the VM
On OS X the docker daemon is running inside a VM called boot2docker. This VM is running using the VirtualBox virtualization software. To setup and start the VM, execute the following script:

```
scripts/vm.sh
```
You should now have a Docker machine up and running. This can be verified with:

```
docker-machine ls
```
Which should list the running vespa machine.

## Building the Vespa Docker image
Building node-admin requires that the vespa Docker machine is up and running. This is because the building of the Docker image actually happens inside the VM. 

First we need to make sure that some environment variables are set so that the ```docker``` command knows how to communicate with the VM:

```
eval $(docker-machine env vespa)
```

To build the image, follow the instructions in [README.md](README.md).

The Vespa Docker image will be persisted inside the VM and it is not necessary to rebuild the image if you stop and restart the VM. However, if you remove the VM with ```docker-machine rm vespa```, the image must be rebuilt.

## Running Vespa with the node-admin scripts
The scripts that are used for starting local zones and deploying applications in Linux can be used in OS X by prefixing them with ```scripts/vm.sh ```. 

Follow the instructions in [README.md](README.md) for starting local zones and deploying applications.

## Accessing Vespa directly from OS X 
The ```scripts/vm.sh``` script does some of the network setup inside the VM that is required for this to work. However, it is necessary set up routing and the ```/etc/hosts``` file to get this working. To automatically do this, execute the script:

```
scripts/setup-route-and-hosts-osx.sh
```
The script will prompt you to continue as this will alter your routing table and /etc/hosts file. If your local zone is up and running, the config server should respond to this:

```
curl config-server:4080
```

If you don't want your `/etc/hosts` file to be changed, the
`scripts/route-osx.sh` script can be used instead. This means that you must
inspect `/etc/hosts` inside the VM to find the IP address of each container:
`docker-machine ssh cat /etc/hosts`

## Useful Docker commands 
Obtain the values for the required environment variables with:

```
eval $(docker-machine env vespa)
```

How to log onto the Docker base host:

```
docker-machine ssh vespa
```

Regular ```docker``` commands works as in Linux when you have the environment variables set. 
Look in [README.md](README.md) for useful docker commands.

## Issues
* Accessing Vespa from OS X while on a Cisco VPN connection does not work. This is because the VPN client will protect the routing table on OS X.
    * Workaround is to use ```docker-machine ssh vespa``` and then execute everything from inside the VM.

