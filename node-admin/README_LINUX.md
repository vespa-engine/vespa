# Setting up Docker on a linux machine

First, install Docker. With Fedora 21 you should follow
https://docs.docker.com/installation/fedora/, which describes how to install
Docker, start the Docker daemon, verify you can download images and run them,
and making your user run docker instances.

```
sudo systemctl enable docker
```

## Set up yahoo user

The Vespa docker containers will run images that need to access the host file
system as the user 'yahoo' with UID 1000, e.g. the Node Admin runs as this
user. If this UID is not already taken, you can create a yahoo user as follows:

```
sudo useradd -u 1000 -g 100 -s /dev/null yahoo
```

If the UID is already in use you should move the user to a new UID first.
Alternatively, it might be possible to reuse that user, but this is confusing
and may lead to errors later on (and has not been tested). In the following
example we move the 'landesk' user from UID 1000 to 1010, keeping its GID 1001.

```
sudo usermod -u 1010 landesk
sudo find / -user 1000 -exec chown -h 1010 {} \;
```

## Set up image storage (aka don't break your machine)
 
Docker will by default download (huge) images to a directory under /var. On our
Fedora machines, /var is part of the root filesystem, which does not have a lot
of free space. Since docker runs as root, it is allowed to completely fill up
the filesystem, and it will happily do so. Fedora works very poorly with a full
root filesystem. You won't even be able to log in and clean up the disk usage
once it's happened.

So you'll want to store images somewhere else. An obvious choice is /home,
which typically has a lot more room. Make Docker use directories in the docker
user's home directory. Run the following script to do this:

```
scripts/setup-docker.sh home
```

## Set up TLS

By default, the docker CLI communicates with the docker daemon via a unix
socket. This is fine in itself, but not suffficient for our use. Node Admin,
itself running in a container, will talk to the docker daemon to start
containers for vespa nodes. Node Admin uses a Java library for communication
with the docker daemon, and this library depends on JNI (native) code for unix
socket communication. We don't want that, so that dependency has been
excluded. Therefore, Node Admin uses TLS over IP to communicate with the docker
daemon. You must therefore set up docker with TLS. Mostly, you can follow the
instructions at https://docs.docker.com/articles/https/.

The commands can be run with

```
scripts/setup-docker.sh certs
```

Note the following:

 - You will be asked to generate a key, and will repeatedly be asked for it.
 - Use your fully qualified domain name for Common Name.

Now, you need to tell the docker daemon to use TLS. Edit the file ```/lib/systemd/system/docker.service``` and change
the ExecStart line so it includes the following arguments:
```
--tlsverify --tlscacert=/etc/dockercert_daemon/ca_cert.pem --tlscert=/etc/dockercert_daemon/server_cert.pem --tlskey=/etc/dockercert_daemon/server_key.pem -H=0.0.0.0:2376
```

Then restart docker:
```
sudo systemctl daemon-reload
sudo systemctl restart docker
```

Now tell the docker CLI how to communicate with the docker daemon:
```
export DOCKER_HOST=tcp://127.0.0.1:2376
export DOCKER_TLS_VERIFY=1
export DOCKER_CERT_PATH=/etc/dockercert_cli
```

(You might want to add this to your .bashrc file.)

Now, test that the docker cli can talk to the docker daemon:
```
docker version
docker run --rm hello-world
```

These should run without errors. Finally, to run Node Admin locally, it needs access to the certificate/key files.
```
export CONTAINER_CERT_PATH=/etc/dockercert_container
```

This environment variable will be used when starting the container, which is decribed in the platform-independent
README file.

While docker can and should be run as your user, it's nice to make it possible
to run docker under root too. To enable this you must make sure sudo doesn't
strip off the environment variables, otherwise certain docker commands may
hang. Add a file /etc/sudoers.d/passthrough-docker-env with the content:

```
Defaults    env_keep += "DOCKER_HOST DOCKER_TLS_VERIFY DOCKER_CERT_PATH CONTAINER_CERT_PATH"
```

You are now done with the linux-specific setup work.

## Other

For more information on how to configure the docker daemon, see https://docs.docker.com/articles/systemd/.

## Upgrade of Docker

When Docker upgrades it may overwrite /lib/systemd/system/docker.service. The
symptom is that any docker command will fail with the error message "Cannot
connect to the Docker daemon. Is the docker daemon running on this host?".

Once you have updated docker.service according to this document, and restarted
the Docker daemon, Docker should work again.
