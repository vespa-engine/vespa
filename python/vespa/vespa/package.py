from typing import List, Mapping, Optional

import docker

from vespa.json_serialization import ToJson, FromJson


class Field(ToJson, FromJson["Field"]):
    def __init__(
        self,
        name: str,
        type: str,
        indexing: Optional[List[str]] = None,
        index: Optional[List[str]] = None,
    ) -> None:
        """
        Object representing a Vespa document field.

        :param name: Field name.
        :param type: Field data type.
        :param indexing: Configures how to process data of a field during indexing.
        :param index: Sets index parameters. Content in fields with index are normalized and tokenized by default.
        """
        self.name = name
        self.type = type
        self.indexing = indexing
        self.index = index

    @staticmethod
    def from_dict(mapping: Mapping) -> "Field":
        return Field(
            name=mapping["name"],
            type=mapping["type"],
            indexing=mapping.get("indexing", None),
            index=mapping.get("index", None),
        )

    @property
    def to_dict(self) -> Mapping:
        map = {"name": self.name, "type": self.type}
        if self.indexing is not None:
            map.update(indexing=self.indexing)
        if self.index is not None:
            map.update(index=self.index)
        return map

    def __eq__(self, other):
        if not isinstance(other, self.__class__):
            return False
        return (
            self.name == other.name
            and self.type == other.type
            and self.indexing == other.indexing
            and self.index == other.index
        )

    def __repr__(self):
        return "{0}\n{1}".format(self.__class__.__name__, str(self.to_dict))


class Document(ToJson, FromJson["Document"]):
    def __init__(self, fields: Optional[List[Field]] = None) -> None:
        """
        Object representing a Vespa document.

        """
        if not fields:
            fields = []

        self.fields = fields

    def add_fields(self, *fields: Field):
        """
        Add Fields to the document.

        :param fields: fields to be added
        :return:
        """
        self.fields.extend(list(fields))

    @staticmethod
    def from_dict(mapping: Mapping) -> "Document":
        return Document(fields=[FromJson.map(field) for field in mapping.get("fields")])

    @property
    def to_dict(self) -> Mapping:
        map = {"fields": [field.to_envelope for field in self.fields]}
        return map

    def __eq__(self, other):
        if not isinstance(other, self.__class__):
            return False
        return self.fields == other.fields

    def __repr__(self):
        return "{0}\n{1}".format(self.__class__.__name__, str(self.to_dict))


class FieldSet(ToJson, FromJson["FieldSet"]):
    def __init__(self, name: str, fields: List[str]) -> None:
        """
        A fieldset groups fields together for searching.

        :param name: Name of the fieldset
        :param fields: Field names to be included in the fieldset.
        """
        self.name = name
        self.fields = fields

    @staticmethod
    def from_dict(mapping: Mapping) -> "FieldSet":
        return FieldSet(name=mapping["name"], fields=mapping["fields"])

    @property
    def to_dict(self) -> Mapping:
        map = {"name": self.name, "fields": self.fields}
        return map

    def __eq__(self, other):
        if not isinstance(other, self.__class__):
            return False
        return self.name == other.name and self.fields == other.fields

    def __repr__(self):
        return "{0}\n{1}".format(self.__class__.__name__, str(self.to_dict))


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
        if self.container is None:
            try:
                self.container = client.containers.get(self.name)
            except docker.errors.NotFound:
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
            self.container is not None
            and self.container.exec_run(
                "bash -c 'curl -s --head http://localhost:19071/ApplicationStatus'"
            )
            .output.decode("utf-8")
            .split("\r\n")[0]
            == "HTTP/1.1 200 OK"
        )

    def deploy_locally(self):
        deployment = self.container.exec_run(
            "bash -c '/opt/vespa/bin/vespa-deploy prepare /app/application && /opt/vespa/bin/vespa-deploy activate'"
        )
        return deployment.output.decode("utf-8").split("\n")
