# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

from typing import Callable, List, Optional, Dict


#
# Match phase
#
class MatchFilter(object):
    """
    Abstract class for match filters.
    """

    def create_match_filter(self, query: str) -> str:
        """
        Create part of the YQL expression related to the filter.

        :param query: Query input.
        :return: Part of the YQL expression related to the filter.
        """
        raise NotImplementedError

    def get_query_properties(self, query: Optional[str] = None) -> Dict:
        """
        Relevant request properties associated with the filter.

        :param query: Query input.
        :return: dict containing the relevant request properties associated with the filter.
        """
        raise NotImplementedError


class AND(MatchFilter):
    def __init__(self) -> None:
        """
        Filter that match document containing all the query terms.
        """
        super().__init__()

    def create_match_filter(self, query: str) -> str:
        return '(userInput("{}"))'.format(query)

    def get_query_properties(self, query: Optional[str] = None) -> Dict:
        return {}


class OR(MatchFilter):
    def __init__(self) -> None:
        """
        Filter that match any document containing at least one query term.
        """
        super().__init__()

    def create_match_filter(self, query: str) -> str:
        return '([{{"grammar": "any"}}]userInput("{}"))'.format(query)

    def get_query_properties(self, query: Optional[str] = None) -> Dict:
        return {}


class WeakAnd(MatchFilter):
    def __init__(self, hits: int, field: str = "default") -> None:
        """
        Match documents according to the weakAND algorithm.

        Reference: https://docs.vespa.ai/documentation/using-wand-with-vespa.html

        :param hits: Lower bound on the number of hits to be retrieved.
        :param field: Which Vespa field to search.
        """
        super().__init__()
        self.hits = hits
        self.field = field

    def create_match_filter(self, query: str) -> str:
        query_tokens = query.split(" ")
        terms = ", ".join(
            ['{} contains "{}"'.format(self.field, token) for token in query_tokens]
        )
        return '([{{"targetNumHits": {}}}]weakAnd({}))'.format(self.hits, terms)

    def get_query_properties(self, query: Optional[str] = None) -> Dict:
        return {}


class ANN(MatchFilter):
    def __init__(
        self,
        doc_vector: str,
        query_vector: str,
        embedding_model: Callable[[str], List[float]],
        hits: int,
        label: str,
    ) -> None:
        """
        Match documents according to the nearest neighbor operator.

        Reference: https://docs.vespa.ai/documentation/reference/query-language-reference.html#nearestneighbor

        :param doc_vector: Name of the document field to be used in the distance calculation.
        :param query_vector: Name of the query field to be used in the distance calculation.
        :param embedding_model: Model that takes query str as input and return list of floats as output.
        :param hits: Lower bound on the number of hits to return.
        :param label: A label to identify this specific operator instance.
        """
        super().__init__()
        self.doc_vector = doc_vector
        self.query_vector = query_vector
        self.embedding_model = embedding_model
        self.hits = hits
        self.label = label

    def create_match_filter(self, query: str) -> str:
        return '([{{"targetNumHits": {}, "label": "{}"}}]nearestNeighbor({}, {}))'.format(
            self.hits, self.label, self.doc_vector, self.query_vector
        )

    def get_query_properties(self, query: Optional[str] = None) -> Dict[str, str]:
        embedding_vector = self.embedding_model(query)
        return {
            "ranking.features.query({})".format(self.query_vector): str(
                embedding_vector
            )
        }


class Union(MatchFilter):
    def __init__(self, *args: MatchFilter) -> None:
        """
        Match documents that belongs to the union of many match filters.

        :param args: Match filters to be taken the union of.
        """
        super().__init__()
        self.operators = args

    def create_match_filter(self, query: str) -> str:
        match_filters = []
        for operator in self.operators:
            match_filter = operator.create_match_filter(query=query)
            if match_filter is not None:
                match_filters.append(match_filter)
        return " or ".join(match_filters)

    def get_query_properties(self, query: Optional[str] = None) -> Dict[str, str]:
        query_properties = {}
        for operator in self.operators:
            query_properties.update(operator.get_query_properties(query=query))
        return query_properties


#
# Ranking phase
#
class RankProfile(object):
    def __init__(self, name: str = "default", list_features: bool = False) -> None:
        """
        Define a rank profile.

        :param name: Name of the rank profile as defined in a Vespa search definition.
        :param list_features: Should the ranking features be returned. Either 'true' or 'false'.
        """
        self.name = name
        self.list_features = "false"
        if list_features:
            self.list_features = "true"


class Query(object):
    def __init__(
        self,
        match_phase: MatchFilter = AND(),
        rank_profile: RankProfile = RankProfile(),
    ) -> None:
        """
        Define a query model.

        :param match_phase: Define the match criteria. One of the MatchFilter options available.
        :param rank_profile: Define the rank criteria.
        """
        self.match_phase = match_phase
        self.rank_profile = rank_profile

    def create_body(self, query: str) -> Dict[str, str]:
        """
        Create the appropriate request body to be sent to Vespa.

        :param query: Query input.
        :return: dict representing the request body.
        """

        match_filter = self.match_phase.create_match_filter(query=query)
        query_properties = self.match_phase.get_query_properties(query=query)

        body = {
            "yql": "select * from sources * where {};".format(match_filter),
            "ranking": {
                "profile": self.rank_profile.name,
                "listFeatures": self.rank_profile.list_features,
            },
        }
        body.update(query_properties)
        return body


class VespaResult(object):
    def __init__(self, vespa_result, request_body=None):
        self._vespa_result = vespa_result
        self._request_body = request_body

    @property
    def request_body(self) -> Optional[Dict]:
        return self._request_body

    @property
    def json(self) -> Dict:
        return self._vespa_result

    @property
    def hits(self) -> List:
        return self._vespa_result.get("root", {}).get("children", [])

    @property
    def number_documents_retrieved(self) -> int:
        return self._vespa_result.get("root", {}).get("fields", {}).get("totalCount", 0)

    @property
    def number_documents_indexed(self) -> int:
        return self._vespa_result.get("root", {}).get("coverage", {}).get("documents", 0)
