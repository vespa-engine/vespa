import datetime
import json
import typing

T = typing.TypeVar("T")


class ToJson(object):
    """
    Utility mix-in class for serializing an object to JSON.  It does not really
    do any conversion on its own, but forces serialization into a standardized
    API.

    The serialized class is put into an envelope with some data to make it easier
    to understand what has happened.

        {
          "version": 1,
          "class": "Field",
          "serialized_at": "2018-10-24T12:55:32+00:00",
          "data": { ... }
        }

        * version: This value is hard-coded to 1.
        * class: The name of the class we serialized.  For debugging purposes.
        * serialized_at: The time we serialized the instance of the class.  For debugging purposes.
        * data: The actual data of the serialized class.

    All serialization is based on converting objects to a `dict` which is then converted
    to JSON using the standard Python json library.
    """

    @property
    def to_dict(self) -> typing.Mapping:
        raise NotImplementedError

    @property
    def to_envelope(self) -> typing.Mapping:
        return {
            "version": 1,
            "class": self.__class__.__name__,
            "serialized_at": datetime.datetime.utcnow().isoformat(),
            "data": self.to_dict,
        }

    @property
    def to_json(self) -> str:
        mapping = self.to_envelope
        return json.dumps(mapping)


class FromJson(typing.Generic[T]):
    """
    A mix-in class for deserializing from JSON to an object that implements this class.
    All JSON must have the same envelope as ToJson to be able to properly deserialize the
    contents of the mapping.
    """

    deserializers: typing.MutableMapping[str, "FromJson"] = {}

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)  # type: ignore
        FromJson.deserializers[cls.__name__] = cls

    @staticmethod
    def from_json(json_string: str) -> T:
        mapping = json.loads(json_string)
        return FromJson.map(mapping)

    @staticmethod
    def map(mapping: typing.Mapping) -> T:
        mapping_class = FromJson.deserializers[mapping["class"]]
        return mapping_class.from_dict(mapping["data"])

    @staticmethod
    def from_dict(mapping: typing.Mapping) -> T:
        raise NotImplementedError
