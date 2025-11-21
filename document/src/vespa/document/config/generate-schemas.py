#!/usr/bin/env python3
"""
Generate Vespa .sd schema files from legacy documenttypes.cfg files.
Parses the legacy documenttype[] format and creates minimal .sd schemas.
"""

import re
import os
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from dataclasses import dataclass, field


# Primitive type ID mapping
PRIMITIVE_TYPES = {
    0: "int",
    1: "float",
    2: "string",
    3: "raw",
    4: "long",
    5: "double",
    6: "bool",
    7: "float16",
    8: "document",  # Special: base document type
    10: "uri",
    16: "byte",
    20: "predicate",
    21: "tensor",
}


@dataclass
class Field:
    """Represents a field in a struct or document."""
    name: str
    field_id: int
    datatype_id: int

    def __repr__(self):
        return f"Field({self.name}, id={self.field_id}, type={self.datatype_id})"


@dataclass
class StructType:
    """Represents a struct type definition."""
    struct_id: int
    name: str
    fields: List[Field] = field(default_factory=list)

    def __repr__(self):
        return f"Struct({self.name}, id={self.struct_id}, {len(self.fields)} fields)"


@dataclass
class ArrayType:
    """Represents an array type."""
    array_id: int
    element_id: int

    def __repr__(self):
        return f"Array(id={self.array_id}, element={self.element_id})"


@dataclass
class MapType:
    """Represents a map type."""
    map_id: int
    key_id: int
    value_id: int

    def __repr__(self):
        return f"Map(id={self.map_id}, key={self.key_id}, value={self.value_id})"


@dataclass
class WSetType:
    """Represents a weighted set type."""
    wset_id: int
    element_id: int

    def __repr__(self):
        return f"WSet(id={self.wset_id}, element={self.element_id})"


@dataclass
class DocumentType:
    """Represents a document type definition."""
    doc_id: int
    name: str
    headerstruct_id: int
    bodystruct_id: int
    inherits: List[int] = field(default_factory=list)

    def __repr__(self):
        return f"Document({self.name}, id={self.doc_id}, inherits={self.inherits})"


class ConfigParser:
    """Parses legacy documenttypes.cfg files."""

    def __init__(self):
        self.documents: Dict[int, DocumentType] = {}
        self.structs: Dict[int, StructType] = {}
        self.arrays: Dict[int, ArrayType] = {}
        self.maps: Dict[int, MapType] = {}
        self.wsets: Dict[int, WSetType] = {}
        self.config_lines: List[str] = []

    def parse_file(self, filepath: str):
        """Parse a legacy config file."""
        with open(filepath, 'r') as f:
            self.config_lines = [line.strip() for line in f.readlines()]

        # Parse document types
        num_docs = self._get_array_size("documenttype")
        for doc_idx in range(num_docs):
            self._parse_document(doc_idx)

    def _get_value(self, key: str) -> Optional[str]:
        """Get value for a config key."""
        for line in self.config_lines:
            if line.startswith(key + " "):
                parts = line.split(None, 1)
                if len(parts) == 2:
                    return parts[1].strip('"')
        return None

    def _get_array_size(self, array_name: str) -> int:
        """Get the size of an array."""
        pattern = f"^{re.escape(array_name)}\\[(\\d+)\\]$"
        for line in self.config_lines:
            match = re.match(pattern, line)
            if match:
                return int(match.group(1))
        return 0

    def _get_field_array_size(self, prefix: str) -> int:
        """Get the size of a field array."""
        pattern = f"^{re.escape(prefix)}\\.field\\[(\\d+)\\]$"
        for line in self.config_lines:
            match = re.match(pattern, line)
            if match:
                return int(match.group(1))
        return 0

    def _parse_document(self, doc_idx: int):
        """Parse a single document type."""
        prefix = f"documenttype[{doc_idx}]"

        # Get document properties
        doc_id_str = self._get_value(f"{prefix}.id")
        name = self._get_value(f"{prefix}.name")
        headerstruct_str = self._get_value(f"{prefix}.headerstruct")
        bodystruct_str = self._get_value(f"{prefix}.bodystruct")

        if not doc_id_str or not name or not headerstruct_str or not bodystruct_str:
            print(f"Warning: Incomplete document definition at {prefix}, skipping")
            return

        doc_id = int(doc_id_str)
        headerstruct = int(headerstruct_str)
        bodystruct = int(bodystruct_str)

        # Get inheritance
        inherits = []
        inherits_size = self._get_array_size(f"{prefix}.inherits")
        for i in range(inherits_size):
            parent_id_str = self._get_value(f"{prefix}.inherits[{i}].id")
            if parent_id_str:
                inherits.append(int(parent_id_str))

        doc = DocumentType(doc_id, name, headerstruct, bodystruct, inherits)
        self.documents[doc_id] = doc

        # Parse datatypes
        num_datatypes = self._get_array_size(f"{prefix}.datatype")
        for dt_idx in range(num_datatypes):
            self._parse_datatype(f"{prefix}.datatype[{dt_idx}]")

    def _parse_datatype(self, prefix: str):
        """Parse a single datatype definition."""
        dt_id_str = self._get_value(f"{prefix}.id")
        dt_type = self._get_value(f"{prefix}.type")

        if not dt_id_str or not dt_type:
            return

        dt_id = int(dt_id_str)

        if dt_type == "STRUCT":
            self._parse_struct(prefix, dt_id)
        elif dt_type == "ARRAY":
            element_id_str = self._get_value(f"{prefix}.array.element.id")
            if element_id_str:
                element_id = int(element_id_str)
                self.arrays[dt_id] = ArrayType(dt_id, element_id)
        elif dt_type == "MAP":
            key_id_str = self._get_value(f"{prefix}.map.key.id")
            value_id_str = self._get_value(f"{prefix}.map.value.id")
            if key_id_str and value_id_str:
                key_id = int(key_id_str)
                value_id = int(value_id_str)
                self.maps[dt_id] = MapType(dt_id, key_id, value_id)
        elif dt_type == "WSET":
            element_id_str = self._get_value(f"{prefix}.wset.key.id")
            if element_id_str:
                element_id = int(element_id_str)
                self.wsets[dt_id] = WSetType(dt_id, element_id)

    def _parse_struct(self, prefix: str, struct_id: int):
        """Parse a struct type."""
        name = self._get_value(f"{prefix}.sstruct.name")
        num_fields = self._get_field_array_size(f"{prefix}.sstruct")

        fields = []
        for field_idx in range(num_fields):
            field_prefix = f"{prefix}.sstruct.field[{field_idx}]"
            field_name = self._get_value(f"{field_prefix}.name")
            field_id_str = self._get_value(f"{field_prefix}.id")
            field_datatype_str = self._get_value(f"{field_prefix}.datatype")

            # Skip fields with missing data
            if not field_name or not field_id_str or not field_datatype_str:
                continue

            field_id = int(field_id_str)
            field_datatype = int(field_datatype_str)
            fields.append(Field(field_name, field_id, field_datatype))

        self.structs[struct_id] = StructType(struct_id, name, fields)


class SchemaGenerator:
    """Generates .sd schema files from parsed config data."""

    def __init__(self, parser: ConfigParser):
        self.parser = parser
        self.generated_structs: Set[str] = set()

    def generate_schema(self, doc: DocumentType) -> str:
        """Generate a complete .sd schema for a document type."""
        lines = []
        # Reset generated structs for each schema
        local_generated_structs = set()

        # Schema header
        lines.append(f"schema {doc.name} {{")

        # Document definition
        if doc.inherits and 8 in doc.inherits:
            # Filter out the base "document" type (id=8) from inherits
            parent_docs = [self.parser.documents[pid] for pid in doc.inherits if pid != 8 and pid in self.parser.documents]
        else:
            parent_docs = [self.parser.documents[pid] for pid in doc.inherits if pid in self.parser.documents]

        if parent_docs:
            parent_names = ", ".join(p.name for p in parent_docs)
            lines.append(f"    document {doc.name} inherits {parent_names} {{")
        else:
            lines.append(f"    document {doc.name} {{")

        # Get all fields from header and body structs
        all_fields = []
        user_structs = []

        # Collect fields from header
        if doc.headerstruct_id in self.parser.structs:
            header = self.parser.structs[doc.headerstruct_id]
            all_fields.extend(header.fields)

        # Collect fields from body
        if doc.bodystruct_id in self.parser.structs:
            body = self.parser.structs[doc.bodystruct_id]
            all_fields.extend(body.fields)

        # Find user-defined structs (not header/body structs)
        # We need to collect all structs referenced by fields recursively
        structs_to_check = set()
        for fld in all_fields:
            self._collect_referenced_structs(fld.datatype_id, structs_to_check)

        for struct_id in structs_to_check:
            if struct_id in self.parser.structs:
                struct = self.parser.structs[struct_id]
                if not (struct.name.endswith(".header") or struct.name.endswith(".body")):
                    user_structs.append(struct)

        # Generate struct definitions
        for struct in user_structs:
            if struct.name not in local_generated_structs:
                lines.extend(self._generate_struct(struct, indent=2))
                local_generated_structs.add(struct.name)

        # Generate field definitions
        for fld in all_fields:
            field_type = self._resolve_type(fld.datatype_id)
            lines.append(f"        field {fld.name} type {field_type} {{}}")

        lines.append("    }")  # Close document
        lines.append("}")  # Close schema

        return "\n".join(lines) + "\n"

    def _collect_referenced_structs(self, type_id: int, struct_ids: Set[int]):
        """Recursively collect all struct IDs referenced by a type."""
        # Check if this is a struct
        if type_id in self.parser.structs:
            struct_ids.add(type_id)
            # Recursively check struct fields
            struct = self.parser.structs[type_id]
            for fld in struct.fields:
                self._collect_referenced_structs(fld.datatype_id, struct_ids)
            return

        # Check collections
        if type_id in self.parser.arrays:
            arr = self.parser.arrays[type_id]
            self._collect_referenced_structs(arr.element_id, struct_ids)
        elif type_id in self.parser.maps:
            mp = self.parser.maps[type_id]
            self._collect_referenced_structs(mp.key_id, struct_ids)
            self._collect_referenced_structs(mp.value_id, struct_ids)
        elif type_id in self.parser.wsets:
            wset = self.parser.wsets[type_id]
            self._collect_referenced_structs(wset.element_id, struct_ids)

    def _generate_struct(self, struct: StructType, indent: int = 2) -> List[str]:
        """Generate struct definition lines."""
        lines = []
        indent_str = "    " * indent

        lines.append(f"{indent_str}struct {struct.name} {{")
        for fld in struct.fields:
            field_type = self._resolve_type(fld.datatype_id)
            lines.append(f"{indent_str}    field {fld.name} type {field_type} {{}}")
        lines.append(f"{indent_str}}}")
        lines.append("")

        return lines

    def _resolve_type(self, type_id: int) -> str:
        """Resolve a type ID to a schema type string."""
        # Check primitives
        if type_id in PRIMITIVE_TYPES:
            return PRIMITIVE_TYPES[type_id]

        # Check arrays
        if type_id in self.parser.arrays:
            arr = self.parser.arrays[type_id]
            element_type = self._resolve_type(arr.element_id)
            return f"array<{element_type}>"

        # Check maps
        if type_id in self.parser.maps:
            mp = self.parser.maps[type_id]
            key_type = self._resolve_type(mp.key_id)
            value_type = self._resolve_type(mp.value_id)
            return f"map<{key_type}, {value_type}>"

        # Check weighted sets
        if type_id in self.parser.wsets:
            wset = self.parser.wsets[type_id]
            element_type = self._resolve_type(wset.element_id)
            return f"weightedset<{element_type}>"

        # Check structs
        if type_id in self.parser.structs:
            struct = self.parser.structs[type_id]
            # Return struct name (without .header or .body suffix)
            return struct.name

        # Unknown type
        return f"UNKNOWN_{type_id}"


def main():
    """Main entry point."""
    # Detect the vespa root directory
    # This script is in document/src/vespa/document/config/, so go up 5 levels
    script_dir = Path(__file__).parent
    vespa_root = script_dir.parent.parent.parent.parent.parent

    # Read the list of config files
    config_list_file = vespa_root / "old-doctype-config"

    if not os.path.exists(config_list_file):
        print(f"Error: {config_list_file} not found")
        print(f"Looking in: {vespa_root}")
        return

    with open(config_list_file, 'r') as f:
        config_files = [line.strip() for line in f if line.strip()]

    print(f"Found {len(config_files)} config files to process\n")

    total_schemas = 0
    base_dir = vespa_root

    for config_file in config_files:
        config_path = base_dir / config_file

        if not config_path.exists():
            print(f"Warning: {config_path} does not exist, skipping")
            continue

        print(f"Processing: {config_file}")

        # Parse the config file
        parser = ConfigParser()
        parser.parse_file(str(config_path))

        print(f"  Found {len(parser.documents)} document types")

        # Generate schemas for each document type
        generator = SchemaGenerator(parser)

        for doc_id, doc in parser.documents.items():
            # Generate the schema
            schema_content = generator.generate_schema(doc)

            # Save to file in the same directory as the config
            schema_filename = f"{doc.name}.sd"
            schema_path = config_path.parent / schema_filename

            # Create parent directory if it doesn't exist
            schema_path.parent.mkdir(parents=True, exist_ok=True)

            with open(schema_path, 'w') as f:
                f.write(schema_content)

            print(f"    Generated: {schema_path}")
            total_schemas += 1

        print()

    print(f"✓ Successfully generated {total_schemas} .sd schema files")


if __name__ == "__main__":
    main()
