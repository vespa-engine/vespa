# Node Admin

## Setup

Set up Docker on your machine according to the instructions in [LINUX](README_LINUX.md) or [Mac](README_MAC.md), depending on your hardware.

You should have the docker daemon running and the following environment variables set:
```
VESPA_HOME
VESPA_WEB_SERVICE_PORT
```

To add update `/etc/hosts` with the required hostnames for the local containers, run
```
sudo ./scripts/etc-hosts.sh
```

## Developing

We will describe how you can build a Docker image for Vespa which will be used
to set up a local Docker container with the Node Admin, and a local container
with the Config Server.

Then, we'll show how you bring up this local zone. And finally, how you can
deploy a local Vespa application to this zone.

[RunVespaLocal.java](src/test/java/com/yahoo/vespa/hosted/node/admin/docker/RunVespaLocal.java) 
implements all of the basic methods you need to get started.

### Starting a Local Zone

To start a local zone, simply run:
```
    DockerImage vespaDockerBase = new DockerImage("docker-registry.ops.yahoo.com:4443/vespa/ci:6.53.134");
    Path pathToContainerStorage = Paths.get("/home/docker/container-storage");
    
    RunVespaLocal runVespaLocal = new RunVespaLocal();
    runVespaLocal.buildVespaLocalImage(vespaDockerBase);
    runVespaLocal.startLocalZoneWithNodes(5);
    runVespaLocal.startNodeAdminAsContainer(pathToContainerStorage);
```

### Deploying a Local Application

To deploy an application, check out `vespa/basic-search-for-docker` to `~`, and 
package it with ```mvn package```, then deploy it with:

```
    Path pathToApp = Paths.get("/home/<username>/basic-search-for-docker/target/application.zip");
    runVespaLocal.deployApplication(pathToApp);
```

You can delete application with

```
    runVespaLocal.deleteApplication();
```



## Using

Trigger the incredibly rich and complex `node-admin` REST API(s)
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

