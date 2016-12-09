# Setting up Docker on a linux machine

First, install Docker. With Fedora 21 you should follow
https://docs.docker.com/engine/installation/linux/fedora/, which describes how to install
Docker.

## Configuring Docker
 
Docker will by default download (huge) images to a directory under `/var`. On our
Fedora machines, `/var` is part of the root filesystem, which does not have a lot
of free space. Since docker runs as `root`, it is allowed to completely fill up
the filesystem, and it will happily do so. Fedora works very poorly with a full
root filesystem. You won't even be able to log in and clean up the disk usage
once it's happened.

So you'll want to store images somewhere else. An obvious choice is `/home`,
which typically has a lot more room. Make Docker use directories in the docker
user's home directory, set the `--graph` option by editing 
`/etc/systemd/system/docker.service.d/docker.conf` to:

```
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd \
        --debug \
        --graph=/home/docker/data \
        --host=127.0.0.1:2376 \
        --host=unix:///var/run/docker.sock \
        --selinux-enabled \
        --storage-driver=devicemapper \
        --storage-opt=dm.basesize=20G
```

Finally, start docker:
```
sudo systemctl start docker
```

## Set up yahoo user

The Vespa docker containers will run images that need to access the host file
system as the user `yahoo` with UID 1000, e.g. the Node Admin runs as this
user. If this UID is not already taken, you can create a `yahoo` user as follows:

```
sudo useradd -u 1000 -g 100 -s /dev/null yahoo
```

If the UID is already in use you should move the user to a new UID first.
Alternatively, it might be possible to reuse that user, but this is confusing
and may lead to errors later on (and has not been tested). In the following
example we move the `landesk` user from UID 1000 to 1010, keeping its GID 1001.

```
sudo usermod -u 1010 landesk
sudo find / -user 1000 -exec chown -h 1010 {} \;
```



## Other

For more information on how to configure the docker daemon, see https://docs.docker.com/engine/admin/systemd/.

## Upgrade of Docker

When Docker upgrades it may overwrite /lib/systemd/system/docker.service. The
symptom is that any docker command will fail with the error message "Cannot
connect to the Docker daemon. Is the docker daemon running on this host?".

Once you have updated docker.service according to this document, and restarted
the Docker daemon, Docker should work again.
