# Setting up Docker on OS X
Install Docker Toolbox according to the procedure on [https://www.docker.com/products/docker-toolbox](https://www.docker.com/products/docker-toolbox).
Note: We need to use Docker Toolbox instead of Docker for Mac for running node-admin or system tests, since we need to 
configure networking per container.

# Running Vespa on OS X 

## Starting the VM
On OS X the docker daemon is running inside a VM called boot2docker. This VM is running using the 
VirtualBox virtualization software. To setup and start the VM for the first time, execute the
following script:

```
docker-machine create -d virtualbox default
```
You should now have a Docker machine up and running. This can be verified with:

```
docker-machine ls
```

which should list the running ```default``` machine.

Regular ```docker``` commands works as in Linux when you have the environment variables set. 
Look in [README.md](README.md) for useful docker commands.

## Running Vespa applications or system tests

Before running any applications you need to make containers visible for your Mac:
```
sudo route add 172.18.0.0/16 192.168.99.100
```

Follow the instructions in [README.md](README.md) for starting local zones and deploying applications.

## Issues
* Accessing Vespa from OS X while on a Cisco VPN connection does not work. This is because the VPN client will protect the routing table on OS X.
    * Workaround is to use ```docker-machine ssh vespa``` and then execute everything from inside the VM.

