# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

from typing import Dict, List
from vespa.query import VespaResult


class EvalMetric(object):
    def __init__(self) -> None:
        pass

    def evaluate_query(
        self, query_results, relevant_docs, id_field, default_score
    ) -> Dict:
        raise NotImplementedError


class MatchRatio(EvalMetric):
    def __init__(self) -> None:
        """
        Computes the ratio of documents retrieved by the match phase.
        """
        super().__init__()
        self.name = "match_ratio"

    def evaluate_query(
        self,
        query_results: VespaResult,
        relevant_docs: List[Dict],
        id_field: str,
        default_score: int,
    ) -> Dict:
        """
        Evaluate query results.

        :param query_results: Raw query results returned by Vespa.
        :param relevant_docs: A list with dicts where each dict contains a doc id a optionally a doc score.
        :param id_field: The Vespa field representing the document id.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :return: Dict containing the number of retrieved docs (_retrieved_docs), the number of docs available in
            the corpus (_docs_available) and the match ratio (_value).
        """
        retrieved_docs = query_results.number_documents_retrieved
        docs_available = query_results.number_documents_indexed
        value = 0
        if docs_available > 0:
            value = retrieved_docs / docs_available
        return {
            str(self.name) + "_retrieved_docs": retrieved_docs,
            str(self.name) + "_docs_available": docs_available,
            str(self.name) + "_value": value,
        }


class Recall(EvalMetric):
    def __init__(self, at: int) -> None:
        """
        Compute the recall at position `at`

        :param at: Maximum position on the resulting list to look for relevant docs.
        """
        super().__init__()
        self.name = "recall_" + str(at)
        self.at = at

    def evaluate_query(
        self,
        query_results: VespaResult,
        relevant_docs: List[Dict],
        id_field: str,
        default_score: int,
    ) -> Dict:
        """
        Evaluate query results.

        :param query_results: Raw query results returned by Vespa.
        :param relevant_docs: A list with dicts where each dict contains a doc id a optionally a doc score.
        :param id_field: The Vespa field representing the document id.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :return: Dict containing the recall value (_value).
        """

        relevant_ids = {str(doc["id"]) for doc in relevant_docs}
        try:
            retrieved_ids = {
                str(hit["fields"][id_field]) for hit in query_results.hits[: self.at]
            }
        except KeyError:
            retrieved_ids = set()

        return {
            str(self.name)
            + "_value": len(relevant_ids & retrieved_ids) / len(relevant_ids)
        }


class ReciprocalRank(EvalMetric):
    def __init__(self, at: int):
        """
        Compute the reciprocal rank at position `at`

        :param at: Maximum position on the resulting list to look for relevant docs.
        """
        super().__init__()
        self.name = "reciprocal_rank_" + str(at)
        self.at = at

    def evaluate_query(
        self,
        query_results: VespaResult,
        relevant_docs: List[Dict],
        id_field: str,
        default_score: int,
    ) -> Dict:
        """
        Evaluate query results.

        :param query_results: Raw query results returned by Vespa.
        :param relevant_docs: A list with dicts where each dict contains a doc id a optionally a doc score.
        :param id_field: The Vespa field representing the document id.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :return: Dict containing the reciprocal rank value (_value).
        """

        relevant_ids = {str(doc["id"]) for doc in relevant_docs}
        rr = 0
        hits = query_results.hits[: self.at]
        for index, hit in enumerate(hits):
            if hit["fields"][id_field] in relevant_ids:
                rr = 1 / (index + 1)
                break

        return {str(self.name) + "_value": rr}
