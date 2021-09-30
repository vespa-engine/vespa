#!/usr/bin/env python3
# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import json
import argparse
import os, sys

# Parsing Elastic Search documents to Vespa documents
# Example of usage:  python ES_Vespa_parser.py my_index.json my_index_mapping.json
__author__ = 'henrhoi'


class ElasticSearchParser:
    document_file = None
    mapping_file = None
    application_name = None
    schemas = {}
    path = ""
    _all = True
    all_mappings = {}
    no_index = []
    types = []

    def __init__(self):
        parser = argparse.ArgumentParser()
        parser.add_argument("documents_path", help="location of file with documents to be parsed", type=str)
        parser.add_argument("mappings_path", help="location of file with mappings", type=str)
        parser.add_argument("--application_name", help="name of application", default="application_name", type=str)
        args = parser.parse_args()

        self.document_file = args.documents_path
        self.mapping_file = args.mappings_path
        self.application_name = args.application_name

    def main(self):
        self.path = os.getcwd() + "/" + self.application_name + "/"
        try:
            os.mkdir(self.path, 0o777)
            print(" > Created folder '" + self.path + "'")
        except OSError:
            print(" > Folder '" + self.path + "' already existed")

        try:
            os.makedirs(self.path + "schemas/", 0o777)
            print(" > Created folder '" + self.path + "schemas/" + "'")
        except OSError:
            print(" > Folder '" + self.path + "schemas/" + "' already existed")

        self.parse()
        self.createServices_xml()
        self.createHosts_xml()

    def getMapping(self, type):
        unparsed_mapping_file = open(self.mapping_file, "r")
        type_mapping = {}
        for line in unparsed_mapping_file:
            data = json.loads(line)
            index = list(data.keys())[0]
            mappings = data[index]["mappings"]["properties"]

            # Checking if some fields could be no-index
            try:
                _all_enabled = data[index]["mappings"]["_all"]["enabled"]
                if not _all_enabled:
                    self._all = False
                    print(" > Not all fields in the document type '" + type + "' are searchable. Edit " + self.path + "schemas/" + type + ".sd to control which fields are searchable")
            except KeyError:
                print(" > All fields in the document type '" + type + "' is searchable")

            self.walk(mappings, type_mapping, "properties")

        unparsed_mapping_file.close()
        if type not in self.schemas:
            self.schemas[type] = True
            self.types.append(type)
            self.createSchema(type, type_mapping)

        # Adding mapping to global map with mappings
        self.all_mappings[type] = type_mapping
        return type_mapping

    def parse(self):
        file_path = self.path + "documents" + ".json"
        unparsed_document_file = open(self.document_file, "r")
        vespa_docs = open(file_path, "w")

        for line in unparsed_document_file:
            data = json.loads(line)
            type = data["_type"]

            parsed_data = {
                "put": "id:"+self.application_name+":" + type + "::" + data["_id"],
                "fields": {}
            }

            # Checking for already existing mapping for a type, if not create a new
            if type in self.all_mappings:
                mapping = self.all_mappings[type]
            else:
                mapping = self.getMapping(type)

            for key, item in mapping.items():
                try:
                    parsed_data["fields"][key] = data["_source"][key]
                except KeyError:
                    continue

            json.dump(parsed_data, vespa_docs)
            vespa_docs.write("\n")

        vespa_docs.close()
        unparsed_document_file.close()
        print(" > Parsed all documents '" + ", ".join(self.types) + "' at '" + file_path + "'")

    def createSchema(self, type, type_mapping):
        file_path = self.path + "schemas/" + type + ".sd"
        new_sd = open(file_path, "w")
        new_sd.write("search " + type + " {\n")
        new_sd.write("    document " + type + " {\n")

        for key, item in type_mapping.items():
            type = self.get_type(item)
            if(type == "nested"):
                print(" > SKIPPING FIELD " + key + ", this tool is not yet able to convert nested fields")
                continue
            new_sd.write("        field " + key + " type " + self.get_type(item) + " {\n")
            new_sd.write("            indexing: " + self.get_indexing(key, self.get_type(item)) + "\n")
            new_sd.write("        }\n")

        new_sd.write("    }\n")
        new_sd.write("}\n")
        new_sd.close()
        print(" > Created schema for '" + type + "' at '" + file_path + "'")

    def createServices_xml(self):
        file_path = self.path + "services.xml"
        new_services = open(file_path, "w")
        template = ("<?xml version='1.0' encoding='UTF-8'?>"
                    "<services version='1.0'>\n\n"
                    "  <container id='default' version='1.0'>\n"
                    "    <search/>\n"
                    "    <document-api/>\n"
                    "    <nodes>\n"
                    "      <node hostalias='node1'/>\n"
                    "    </nodes>\n"
                    "  </container>\n\n"
                    "  <content id='content' version='1.0'>\n"
                    "    <redundancy>1</redundancy>\n"
                    "    <search>\n"
                    "      <visibility-delay>1.0</visibility-delay>\n"
                    "    </search>\n"
                    "    <documents>\n")

        for i in range(0, len(self.types)):
            template += "      <document mode='index' type='" + self.types[i] + "'/>\n"

        template += ("    </documents>\n"
                     "    <nodes>\n"
                     "      <node hostalias='node1' distribution-key=\"0\"/>\n"
                     "    </nodes>\n"
                     "    <engine>\n"
                     "      <proton>\n"
                     "        <searchable-copies>1</searchable-copies>\n"
                     "      </proton>\n"
                     "    </engine>\n"
                     "  </content>\n\n"
                     "</services>")

        new_services.write(template)
        new_services.close()
        print(" > Created services.xml at '" + file_path + "'")

    def createHosts_xml(self):
        file_path = self.path + "hosts.xml"
        new_hosts = open(file_path, "w")
        template = ("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                    "<hosts>\n"
                    "  <host name=\"localhost\">\n"
                    "    <alias>node1</alias>\n"
                    "  </host>\n"
                    "</hosts>")

        new_hosts.write(template)
        new_hosts.close()
        print(" > Created hosts.xml at '" + file_path + "'")

    def get_type(self, type):
        return {
            "integer": "int",
            "string": "string", # for compatability with older ES versions
            "text": "string",
            "keyword": "string",
            "date": "string",
            "long": "long",
            "double": "double",
            "boolean": "string",
            "ip": "text",
            "byte": "byte",
            "float": "float",
            "nested": "nested"

        }[type]

    def get_indexing(self, key, key_type):
        if not self._all:
            return "summary"

        if key not in self.no_index:
            if key_type == "string":
                return "summary | index"
            else:
                return "summary | attribute"

        return "summary"

    def walk(self, node, mapping, parent):
        for key, item in node.items():
            if isinstance(item, dict):
                self.walk(item, mapping, key)
            elif key == "type":
                mapping[parent] = item
            elif key == "include_in_all":
                if not item:  # Field should not be searchable
                    self.no_index.append(parent)
            elif key == "index" and parent != "properties":
                if item == "no":  # Field should not be searchable
                    self.no_index.append(parent)

if __name__ == '__main__':
    ElasticSearchParser().main()
