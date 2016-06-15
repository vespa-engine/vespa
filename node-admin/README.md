# Node Admin

## Setup

Set up Docker on your machine according to the instructions in README_LINUX or README_MAC, depending on your hardware.

You should have the docker daemon running and the following environment variables set:
```
DOCKER_HOST
CONTAINER_CERT_PATH
```

## Building

Build Node Admin and include it (and other local modifications) in the Docker image ```vespa-local```:
```
mvn clean package
./build.sh
```

## Running

TODO: Outdated! Update this section with info on how to run everything locally.

Start the container for the config server (TODO). Set the CONFIG_SERVER_ADDRESS
variable to the hostname of the config server.

Start the container
```
docker run -t -i --privileged \
        -p 4080:4080 \
        -v $CONTAINER_CERT_PATH:/host/docker/certs \
        -e "DOCKER_HOST=$DOCKER_HOST" \
        -e "CONFIG_SERVER_ADDRESS=$CONFIG_SERVER_ADDRESS" \
        vespa-local:latest
```

This will map the client certificate/key files to the path where Node Admin looks for them (as configured in
services.xml), and enable Node Admin to talk to the docker daemon. You can invoke Node Admin's REST APIs on port 4080
from both inside the container and the outside host.

## Using

Trigger the incredibly rich and complex node-admin REST API(s)
```
curl localhost:4080/test/ping
```

## Troubleshooting

If the container doesn't start, it can be helpful to look at the jdisc log. First, find the container id:
```
docker ps -a
```

Then, find the log files:
```
docker diff <container id>| grep $VESPA_HOME/logs
```

View the log file (`-L` follows the symbolic link):
```
docker cp -L <container id>:$VESPA_HOME/logs/jdisc_core/jdisc_core.log - | less
```

## Developing

We will describe how you can build a Docker image for Vespa which will be used
to set up a local Docker container with the Node Admin, and a local container
with the Config Server.

Then, we'll show how you bring up this local zone. And finally, how you can
deploy a local Vespa application to this zone.

### Building Local Docker Image

A Dockerfile exists in the module's root directory. This Dockerfile is not used
in production or any pipelines, it is here for convenience so you can build
images and experiment locally. See build.sh for how to build.

The image created by the Dockerfile will be used to run Node Admin or a Config
Server.

### Starting a Local Zone

To start a local zone, ensure your operating system ignores ```config-server```
and ```node-admin``` for proxying. Then issue the following command:

```
scripts/zone.sh start
```

The Node Admin and Config Server now runs in the ```node-admin``` and
```config-server``` Docker containers. These containers have their own IP
addresses and hostnames (also ```node-admin``` and ```config-server```).

### Deploying a Local Application

To deploy an application, use ```scripts/app.sh```. Assuming you have checked
out ```vespa/basic-search-for-docker``` to ```~```, and packaged it with ```mvn
package```, you can deploy the application with:

```
scripts/app.sh deploy ~/vespa/basic-search-for-docker/target/application
```

You can undeploy it with

```
scripts/app.sh undeploy
```
