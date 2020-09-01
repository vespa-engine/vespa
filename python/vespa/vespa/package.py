import sys
import http.client
import json
import os
import re
import zipfile
from base64 import standard_b64encode
from datetime import datetime, timedelta
from io import BytesIO
from pathlib import Path
from time import sleep, strftime, gmtime
from typing import List, Mapping, Optional, IO

import docker
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from jinja2 import Environment, PackageLoader, select_autoescape

from vespa.json_serialization import ToJson, FromJson
from vespa.application import Vespa


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
                disabled_extensions=("txt",),
                default_for_string=True,
                default=True,
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
                disabled_extensions=("txt",),
                default_for_string=True,
                default=True,
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
                disabled_extensions=("txt",),
                default_for_string=True,
                default=True,
            ),
        )
        env.trim_blocks = True
        env.lstrip_blocks = True
        schema_template = env.get_template("services.xml")
        return schema_template.render(
            application_name=self.name,
            document_name=self.schema.name,
        )

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
    def __init__(
        self,
        application_package: ApplicationPackage,
        output_file: IO = sys.stdout,
    ) -> None:
        """
        Deploy application to a Vespa container

        :param application_package: ApplicationPackage to be deployed.
        :param output_file: Output file to write output messages.
        """
        self.application_package = application_package
        self.container = None
        self.local_port = 8080
        self.output = output_file

    def _run_vespa_engine_container(self, disk_folder: str, container_memory: str):
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
                    ports={self.local_port: self.local_port, 19112: 19112},
                )

    def _check_configuration_server(self) -> bool:
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

    def _create_application_package_files(self, dir_path):
        Path(os.path.join(dir_path, "application/schemas")).mkdir(
            parents=True, exist_ok=True
        )
        with open(
            os.path.join(
                dir_path,
                "application/schemas/{}.sd".format(
                    self.application_package.schema.name
                ),
            ),
            "w",
        ) as f:
            f.write(self.application_package.schema_to_text)
        with open(os.path.join(dir_path, "application/hosts.xml"), "w") as f:
            f.write(self.application_package.hosts_to_text)
        with open(os.path.join(dir_path, "application/services.xml"), "w") as f:
            f.write(self.application_package.services_to_text)

    def deploy(self, disk_folder: str, container_memory: str = "4G"):
        """
        Deploy the application into a Vespa container.

        :param disk_folder: Disk folder to save the required Vespa config files.
        :param container_memory: Docker container memory available to the application.

        :return: a Vespa connection instance.
        """

        self._create_application_package_files(dir_path=disk_folder)

        self._run_vespa_engine_container(
            disk_folder=disk_folder, container_memory=container_memory
        )

        while not self._check_configuration_server():
            print("Waiting for configuration server.", file=self.output)
            sleep(5)

        deployment = self.container.exec_run(
            "bash -c '/opt/vespa/bin/vespa-deploy prepare /app/application && /opt/vespa/bin/vespa-deploy activate'"
        )

        deployment_message = deployment.output.decode("utf-8").split("\n")

        if not any(re.match("Generation: [0-9]+", line) for line in deployment_message):
            raise RuntimeError(deployment_message)

        return Vespa(
            url="http://localhost",
            port=self.local_port,
            deployment_message=deployment_message,
        )


class VespaCloud(object):
    def __init__(
        self,
        tenant: str,
        application: str,
        key_location: str,
        application_package: ApplicationPackage,
        output_file: IO = sys.stdout,
    ) -> None:
        """
        Deploy application to the Vespa Cloud (cloud.vespa.ai)

        :param tenant: Tenant name registered in the Vespa Cloud.
        :param application: Application name registered in the Vespa Cloud.
        :param key_location: Location of the private key used for signing HTTP requests to the Vespa Cloud.
        :param application_package: ApplicationPackage to be deployed.
        :param output_file: Output file to write output messages.
        """
        self.tenant = tenant
        self.application = application
        self.application_package = application_package
        self.api_key = self._read_private_key(key_location)
        self.api_public_key_bytes = standard_b64encode(
            self.api_key.public_key().public_bytes(
                serialization.Encoding.PEM,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            )
        )
        self.data_key, self.data_certificate = self._create_certificate_pair()
        self.private_cert_file_name = "private_cert.txt"
        self.connection = http.client.HTTPSConnection(
            "api.vespa-external.aws.oath.cloud", 4443
        )
        self.output = output_file

    @staticmethod
    def _read_private_key(key_location: str) -> ec.EllipticCurvePrivateKey:
        with open(key_location, "rb") as key_data:
            key = serialization.load_pem_private_key(
                key_data.read(), None, default_backend()
            )
            if not isinstance(key, ec.EllipticCurvePrivateKey):
                raise TypeError(
                    "Key at " + key_location + " must be an elliptic curve private key"
                )
            return key

    def _write_private_key_and_cert(
        self, key: ec.EllipticCurvePrivateKey, cert: x509.Certificate, disk_folder: str
    ) -> None:
        cert_file = os.path.join(disk_folder, self.private_cert_file_name)
        with open(cert_file, "w+") as file:
            file.write(
                key.private_bytes(
                    serialization.Encoding.PEM,
                    serialization.PrivateFormat.TraditionalOpenSSL,
                    serialization.NoEncryption(),
                ).decode("UTF-8")
            )
            file.write(cert.public_bytes(serialization.Encoding.PEM).decode("UTF-8"))

    @staticmethod
    def _create_certificate_pair() -> (ec.EllipticCurvePrivateKey, x509.Certificate):
        key = ec.generate_private_key(ec.SECP384R1, default_backend())
        name = x509.Name([x509.NameAttribute(x509.NameOID.COMMON_NAME, u"localhost")])
        certificate = (
            x509.CertificateBuilder()
            .subject_name(name)
            .issuer_name(name)
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.utcnow() - timedelta(minutes=1))
            .not_valid_after(datetime.utcnow() + timedelta(days=7))
            .public_key(key.public_key())
            .sign(key, hashes.SHA256(), default_backend())
        )
        return (key, certificate)

    def _request(
        self, method: str, path: str, body: BytesIO = BytesIO(), headers={}
    ) -> dict:
        digest = hashes.Hash(hashes.SHA256(), default_backend())
        body.seek(0)
        digest.update(body.read())
        content_hash = standard_b64encode(digest.finalize()).decode("UTF-8")
        timestamp = (
            datetime.utcnow().isoformat() + "Z"
        )  # Java's Instant.parse requires the neutral time zone appended
        url = "https://" + self.connection.host + ":" + str(self.connection.port) + path

        canonical_message = method + "\n" + url + "\n" + timestamp + "\n" + content_hash
        signature = self.api_key.sign(
            canonical_message.encode("UTF-8"), ec.ECDSA(hashes.SHA256())
        )

        headers = {
            "X-Timestamp": timestamp,
            "X-Content-Hash": content_hash,
            "X-Key-Id": self.tenant + ":" + self.application + ":" + "default",
            "X-Key": self.api_public_key_bytes,
            "X-Authorization": standard_b64encode(signature),
            **headers,
        }

        body.seek(0)
        self.connection.request(method, path, body, headers)
        with self.connection.getresponse() as response:
            parsed = json.load(response)
            if response.status != 200:
                raise RuntimeError(
                    "Status code "
                    + str(response.status)
                    + " doing "
                    + method
                    + " at "
                    + url
                    + ":\n"
                    + parsed["message"]
                )
            return parsed

    def _get_dev_region(self) -> str:
        return self._request("GET", "/zone/v1/environment/dev/default")["name"]

    def _get_endpoint(self, instance: str, region: str) -> str:
        endpoints = self._request(
            "GET",
            "/application/v4/tenant/{}/application/{}/instance/{}/environment/dev/region/{}".format(
                self.tenant, self.application, instance, region
            ),
        )["endpoints"]
        container_url = [
            endpoint["url"]
            for endpoint in endpoints
            if endpoint["cluster"]
            == "{}_container".format(self.application_package.name)
        ]
        if not container_url:
            raise RuntimeError("No endpoints found for container 'test_app_container'")
        return container_url[0]

    def _to_application_zip(self) -> BytesIO:
        buffer = BytesIO()
        with zipfile.ZipFile(buffer, "a") as zip_archive:
            zip_archive.writestr(
                "application/schemas/{}.sd".format(
                    self.application_package.schema.name
                ),
                self.application_package.schema_to_text,
            )
            zip_archive.writestr(
                "application/services.xml", self.application_package.services_to_text
            )
            zip_archive.writestr(
                "application/security/clients.pem",
                self.data_certificate.public_bytes(serialization.Encoding.PEM),
            )

        return buffer

    def _start_deployment(self, instance: str, job: str, disk_folder: str) -> int:
        deploy_path = (
            "/application/v4/tenant/{}/application/{}/instance/{}/deploy/{}".format(
                self.tenant, self.application, instance, job
            )
        )

        application_zip_bytes = self._to_application_zip()

        self._write_private_key_and_cert(
            self.data_key, self.data_certificate, disk_folder
        )

        response = self._request(
            "POST",
            deploy_path,
            application_zip_bytes,
            {"Content-Type": "application/zip"},
        )
        print(response["message"], file=self.output)
        return response["run"]

    def _get_deployment_status(
        self, instance: str, job: str, run: int, last: int
    ) -> (str, int):

        update = self._request(
            "GET",
            "/application/v4/tenant/{}/application/{}/instance/{}/job/{}/run/{}?after={}".format(
                self.tenant, self.application, instance, job, run, last
            ),
        )

        for step, entries in update["log"].items():
            for entry in entries:
                self._print_log_entry(step, entry)
        last = update.get("lastId", last)

        fail_status_message = {
            "error": "Unexpected error during deployment; see log for details",
            "aborted": "Deployment was aborted, probably by a newer deployment",
            "outOfCapacity": "No capacity left in zone; please contact the Vespa team",
            "deploymentFailed": "Deployment failed; see log for details",
            "installationFailed": "Installation failed; see Vespa log for details",
            "running": "Deployment not completed",
            "endpointCertificateTimeout": "Endpoint certificate not ready in time; please contact Vespa team",
            "testFailure": "Unexpected status; tests are not run for manual deployments",
        }

        if update["active"]:
            return "active", last
        else:
            status = update["status"]
            if status == "success":
                return "success", last
            elif status in fail_status_message.keys():
                raise RuntimeError(fail_status_message[status])
            else:
                raise RuntimeError("Unexpected status: {}".format(status))

    def _follow_deployment(self, instance: str, job: str, run: int) -> None:
        last = -1
        while True:
            try:
                status, last = self._get_deployment_status(instance, job, run, last)
            except RuntimeError:
                raise

            if status == "active":
                sleep(1)
            elif status == "success":
                return
            else:
                raise RuntimeError("Unexpected status: {}".format(status))

    def _print_log_entry(self, step: str, entry: dict):
        timestamp = strftime("%H:%M:%S", gmtime(entry["at"] / 1e3))
        message = entry["message"].replace("\n", "\n" + " " * 23)
        if step != "copyVespaLogs" or entry["type"] == "error":
            print(
                "{:<7} [{}]  {}".format(entry["type"].upper(), timestamp, message),
                file=self.output,
            )

    def deploy(self, instance: str, disk_folder: str) -> Vespa:
        """
        Deploy the given application package as the given instance in the Vespa Cloud dev environment.

        :param instance: Name of this instance of the application, in the Vespa Cloud.
        :param disk_folder: Disk folder to save the required Vespa config files.

        :return: a Vespa connection instance.
        """
        region = self._get_dev_region()
        job = "dev-" + region
        run = self._start_deployment(instance, job, disk_folder)
        self._follow_deployment(instance, job, run)
        endpoint_url = self._get_endpoint(instance=instance, region=region)
        return Vespa(
            url=endpoint_url,
            cert=os.path.join(disk_folder, self.private_cert_file_name),
        )

    def delete(self, instance: str):
        """
        Delete the specified instance from the dev environment in the Vespa Cloud.
        :param instance: Name of the instance to delete.
        :return:
        """
        print(
            self._request(
                "DELETE",
                "/application/v4/tenant/{}/application/{}/instance/{}/environment/dev/region/{}".format(
                    self.tenant, self.application, instance, self._get_dev_region()
                ),
            )["message"],
            file=self.output,
        )

    def close(self):
        self.connection.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
