import os
from time import sleep
from typing import List, Mapping, Optional
from pathlib import Path

from jinja2 import Environment, PackageLoader, select_autoescape
import docker

from vespa.json_serialization import ToJson, FromJson


class Field(ToJson, FromJson["Field"]):
    def __init__(
        self,
        name: str,
        type: str,
        indexing: Optional[List[str]] = None,
        index: Optional[str] = None,
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

    @property
    def indexing_to_text(self) -> Optional[str]:
        if self.indexing is not None:
            return " | ".join(self.indexing)

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
        return "{0}({1}, {2}, {3}, {4})".format(
            self.__class__.__name__,
            repr(self.name),
            repr(self.type),
            repr(self.indexing),
            repr(self.index),
        )


class Document(ToJson, FromJson["Document"]):
    def __init__(self, fields: Optional[List[Field]] = None) -> None:
        """
        Object representing a Vespa document.

        """
        self.fields = [] if not fields else fields

    def add_fields(self, *fields: Field):
        """
        Add Fields to the document.

        :param fields: fields to be added
        :return:
        """
        self.fields.extend(fields)

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
        return "{0}({1})".format(
            self.__class__.__name__, repr(self.fields) if self.fields else None
        )


class FieldSet(ToJson, FromJson["FieldSet"]):
    def __init__(self, name: str, fields: List[str]) -> None:
        """
        A fieldset groups fields together for searching.

        :param name: Name of the fieldset
        :param fields: Field names to be included in the fieldset.
        """
        self.name = name
        self.fields = fields

    @property
    def fields_to_text(self):
        if self.fields is not None:
            return ", ".join(self.fields)

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
        return "{0}({1}, {2})".format(
            self.__class__.__name__, repr(self.name), repr(self.fields)
        )


class RankProfile(ToJson, FromJson["RankProfile"]):
    def __init__(
        self, name: str, first_phase: str, inherits: Optional[str] = None
    ) -> None:
        """
        Define a Vespa rank profile

        :param name: Rank profile name.
        :param first_phase: First phase ranking expression.
        """
        self.name = name
        self.first_phase = first_phase
        self.inherits = inherits

    @staticmethod
    def from_dict(mapping: Mapping) -> "RankProfile":
        return RankProfile(
            name=mapping["name"],
            first_phase=mapping["first_phase"],
            inherits=mapping.get("inherits", None),
        )

    @property
    def to_dict(self) -> Mapping:
        map = {"name": self.name, "first_phase": self.first_phase}
        if self.inherits is not None:
            map.update({"inherits": self.inherits})
        return map

    def __eq__(self, other):
        if not isinstance(other, self.__class__):
            return False
        return (
            self.name == other.name
            and self.first_phase == other.first_phase
            and self.inherits == other.inherits
        )

    def __repr__(self):
        return "{0}({1}, {2}, {3})".format(
            self.__class__.__name__,
            repr(self.name),
            repr(self.first_phase),
            repr(self.inherits),
        )


class Schema(ToJson, FromJson["Schema"]):
    def __init__(
        self,
        name: str,
        document: Document,
        fieldsets: Optional[List[FieldSet]] = None,
        rank_profiles: Optional[List[RankProfile]] = None,
    ) -> None:
        """
        Create a Vespa Schema.

        :param name: Schema name.
        :param document: Vespa document associated with the Schema.
        :param fieldsets: A list of `FieldSet` associated with the Schema.
        :param rank_profiles: A list of `RankProfile` associated with the Schema.
        """
        self.name = name
        self.document = document

        self.fieldsets = {}
        if fieldsets is not None:
            self.fieldsets = {fieldset.name: fieldset for fieldset in fieldsets}

        self.rank_profiles = {}
        if rank_profiles is not None:
            self.rank_profiles = {
                rank_profile.name: rank_profile for rank_profile in rank_profiles
            }

    def add_rank_profile(self, rank_profile: RankProfile) -> None:
        """
        Add a `RankProfile` to the `Schema`.
        :param rank_profile: `RankProfile` to be added.
        :return: None.
        """
        self.rank_profiles[rank_profile.name] = rank_profile

    @staticmethod
    def from_dict(mapping: Mapping) -> "Schema":
        return Schema(
            name=mapping["name"],
            document=FromJson.map(mapping["document"]),
            fieldsets=[FromJson.map(fieldset) for fieldset in mapping["fieldsets"]],
            rank_profiles=[
                FromJson.map(rank_profile) for rank_profile in mapping["rank_profiles"]
            ],
        )

    @property
    def to_dict(self) -> Mapping:
        map = {
            "name": self.name,
            "document": self.document.to_envelope,
            "fieldsets": [
                self.fieldsets[name].to_envelope for name in self.fieldsets.keys()
            ],
            "rank_profiles": [
                self.rank_profiles[name].to_envelope
                for name in self.rank_profiles.keys()
            ],
        }
        return map

    def __eq__(self, other):
        if not isinstance(other, self.__class__):
            return False
        return (
            self.name == other.name
            and self.document == other.document
            and self.fieldsets == other.fieldsets
            and self.rank_profiles == other.rank_profiles
        )

    def __repr__(self):
        return "{0}({1}, {2}, {3}, {4})".format(
            self.__class__.__name__,
            repr(self.name),
            repr(self.document),
            repr(
                [field for field in self.fieldsets.values()] if self.fieldsets else None
            ),
            repr(
                [rank_profile for rank_profile in self.rank_profiles.values()]
                if self.rank_profiles
                else None
            ),
        )


class ApplicationPackage(ToJson, FromJson["ApplicationPackage"]):
    def __init__(self, name: str, schema: Schema) -> None:
        """
        Vespa Application Package.

        :param name: Application name.
        :param schema: Schema of the application.
        """
        self.name = name
        self.schema = schema

    @property
    def schema_to_text(self):
        env = Environment(
            loader=PackageLoader("vespa", "templates"),
            autoescape=select_autoescape(
                disabled_extensions=("txt",), default_for_string=True, default=True,
            ),
        )
        env.trim_blocks = True
        env.lstrip_blocks = True
        schema_template = env.get_template("schema.txt")
        return schema_template.render(
            schema_name=self.schema.name,
            document_name=self.schema.name,
            fields=self.schema.document.fields,
            fieldsets=self.schema.fieldsets,
            rank_profiles=self.schema.rank_profiles,
        )

    @property
    def hosts_to_text(self):
        env = Environment(
            loader=PackageLoader("vespa", "templates"),
            autoescape=select_autoescape(
                disabled_extensions=("txt",), default_for_string=True, default=True,
            ),
        )
        env.trim_blocks = True
        env.lstrip_blocks = True
        schema_template = env.get_template("hosts.xml")
        return schema_template.render()

    @property
    def services_to_text(self):
        env = Environment(
            loader=PackageLoader("vespa", "templates"),
            autoescape=select_autoescape(
                disabled_extensions=("txt",), default_for_string=True, default=True,
            ),
        )
        env.trim_blocks = True
        env.lstrip_blocks = True
        schema_template = env.get_template("services.xml")
        return schema_template.render(
            application_name=self.name, document_name=self.schema.name,
        )

    def create_application_package_files(self, dir_path):
        Path(os.path.join(dir_path, "application/schemas")).mkdir(
            parents=True, exist_ok=True
        )
        with open(
            os.path.join(
                dir_path, "application/schemas/{}.sd".format(self.schema.name)
            ),
            "w",
        ) as f:
            f.write(self.schema_to_text)
        with open(os.path.join(dir_path, "application/hosts.xml"), "w") as f:
            f.write(self.hosts_to_text)
        with open(os.path.join(dir_path, "application/services.xml"), "w") as f:
            f.write(self.services_to_text)

    @staticmethod
    def from_dict(mapping: Mapping) -> "ApplicationPackage":
        schema = mapping.get("schema", None)
        if schema is not None:
            schema = FromJson.map(schema)
        return ApplicationPackage(name=mapping["name"], schema=schema)

    @property
    def to_dict(self) -> Mapping:
        map = {"name": self.name}
        if self.schema is not None:
            map.update({"schema": self.schema.to_envelope})
        return map

    def __eq__(self, other):
        if not isinstance(other, self.__class__):
            return False
        return self.name == other.name and self.schema == other.schema

    def __repr__(self):
        return "{0}({1}, {2})".format(
            self.__class__.__name__, repr(self.name), repr(self.schema)
        )


class VespaDocker(object):
    def __init__(self, application_package: ApplicationPackage) -> None:
        """
        Deploy application to a Vespa container

        :param application_package: ApplicationPackage to be deployed.
        """
        self.application_package = application_package
        self.container = None

    def run_vespa_engine_container(self, disk_folder: str, container_memory: str):
        """
        Run a vespa container.

        :param disk_folder: Folder containing the application files.
        :param container_memory: Memory limit of the container
        :return:
        """
        client = docker.from_env()
        if self.container is None:
            try:
                self.container = client.containers.get(self.application_package.name)
            except docker.errors.NotFound:
                self.container = client.containers.run(
                    "vespaengine/vespa",
                    detach=True,
                    mem_limit=container_memory,
                    name=self.application_package.name,
                    hostname=self.application_package.name,
                    privileged=True,
                    volumes={disk_folder: {"bind": "/app", "mode": "rw"}},
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

    def deploy(self, disk_folder: str, container_memory: str = "4G"):

        self.application_package.create_application_package_files(dir_path=disk_folder)

        self.run_vespa_engine_container(
            disk_folder=disk_folder, container_memory=container_memory
        )

        while not self.check_configuration_server():
            print("Waiting for configuration server.")
            sleep(5)

        deployment = self.container.exec_run(
            "bash -c '/opt/vespa/bin/vespa-deploy prepare /app/application && /opt/vespa/bin/vespa-deploy activate'"
        )
        return deployment.output.decode("utf-8").split("\n")
