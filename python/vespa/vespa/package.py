import docker


class ApplicationPackage(object):
    def __init__(self, name: str, disk_folder: str) -> None:
        """
        Vespa Application Package.

        :param name: Application name.
        :param disk_folder: Absolute path of the folder containing the application files.
        """
        self.name = name
        self.disk_folder = disk_folder
        self.container = None

    def run_vespa_engine_container(self, container_memory: str = "4G"):
        """
        Run a vespa container.

        :param container_memory: Memory limit of the container
        :return:
        """
        client = docker.from_env()
        self.container = client.containers.run(
            "vespaengine/vespa",
            detach=True,
            mem_limit=container_memory,
            name=self.name,
            hostname=self.name,
            privileged=True,
            volumes={self.disk_folder: {"bind": "/app", "mode": "rw"}},
            ports={8080: 8080, 19112: 19112},
        )

    def check_configuration_server(self) -> bool:
        """
        Check if configuration server is running and ready for deployment

        :return: True if configuration server is running.
        """
        return (
            self.container.exec_run(
                "bash -c 'curl -s --head http://localhost:19071/ApplicationStatus'"
            )
            .output.decode("utf-8")
            .split("\r\n")[0]
            == "HTTP/1.1 200 OK"
        )

    def deploy_application(self):
        return self.container.exec_run(
            "bash -c '/opt/vespa/bin/vespa-deploy prepare /app/application && /opt/vespa/bin/vespa-deploy activate'"
        )
