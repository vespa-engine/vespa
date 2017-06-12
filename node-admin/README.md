# Node Admin

## Setup

Set up Docker on your machine according to the instructions in [Linux](README_LINUX.md) or [Mac](README_MAC.md), depending on your hardware.

You should have the docker daemon running and the following environment variables set:
```
VESPA_HOME
VESPA_WEB_SERVICE_PORT
```

To update `/etc/hosts` with the required hostnames for the local containers, run
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
    DockerImage dockerImage = new DockerImage("docker-registry.domain.tld:8080/vespa/ci:6.111.21");
    Path pathToVespaRoot = Paths.get("/home/valerijf/dev/vespa");
    Path pathToContainerStorage = Paths.get("/home/docker/container-storage");

    RunVespaLocal runVespaLocal = new RunVespaLocal(pathToVespaRoot);
    runVespaLocal.startLocalZoneWithNodes(dockerImage, 5);
    runVespaLocal.startNodeAdminAsContainer(dockerImage, pathToContainerStorage);
```

### Deploying a Local Application

Package and deploy vespa application by running:

```
    Path pathToApp = pathToVespaRoot.resolve("sample-apps/blog-search");
    runVespaLocal.deployApplication(pathToApp);
```
If the deployment is successful, the final log entry should be something like 
```
INFO: Endpoint http://cnode-1:4080/ is now ready
```
use this endpoint URL to feed or query your application.

You can delete application with

```
    runVespaLocal.deleteApplication();
```

### Feed and search
 1. **Feed** the data that is to be searched
 ```sh

 # Feeding two documents
 curl -X POST --data-binary  @music-data-1.json <endpoint url>/document/v1/music/music/docid/1 | python -m json.tool
 curl -X POST --data-binary  @music-data-2.json <endpoint url>/document/v1/music/music/docid/2 | python -m json.tool

  ```

 2. **Visit documents**

 Since we do not have many documents we can list them all
 ```sh

 # All documents
 curl <endpoint url>/document/v1/music/music/docid | python -m json.tool

 # Document with id 1
 curl <endpoint url>/document/v1/music/music/docid/1 | python -m json.tool

  ```

 3. **Search**
 We can also search for documents:
    ```sh

    curl '<endpoint url>/search/?query=bad' | python -m json.tool


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

