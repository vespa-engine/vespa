# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import unittest

from vespa.query import VespaResult
from vespa.evaluation import MatchRatio, Recall, ReciprocalRank


class TestEvalMetric(unittest.TestCase):
    def setUp(self) -> None:
        self.labelled_data = [
            {
                "query_id": 0,
                "query": "Intrauterine virus infections and congenital heart disease",
                "relevant_docs": [{"id": "def", "score": 1}, {"id": "abc", "score": 1}],
            },
        ]

        self.query_results = {
            "root": {
                "id": "toplevel",
                "relevance": 1.0,
                "fields": {"totalCount": 1083},
                "coverage": {
                    "coverage": 100,
                    "documents": 62529,
                    "full": True,
                    "nodes": 2,
                    "results": 1,
                    "resultsFull": 1,
                },
                "children": [
                    {
                        "id": "id:covid-19:doc::40216",
                        "relevance": 10,
                        "source": "content",
                        "fields": {
                            "vespa_id_field": "ghi",
                            "sddocname": "doc",
                            "body_text": "this is a body 2",
                            "title": "this is a title 2",
                            "rankfeatures": {"a": 3, "b": 4},
                        },
                    },
                    {
                        "id": "id:covid-19:doc::40217",
                        "relevance": 8,
                        "source": "content",
                        "fields": {
                            "vespa_id_field": "def",
                            "sddocname": "doc",
                            "body_text": "this is a body 3",
                            "title": "this is a title 3",
                            "rankfeatures": {"a": 5, "b": 6},
                        },
                    },
                ],
            }
        }

    def test_match_ratio(self):
        metric = MatchRatio()

        evaluation = metric.evaluate_query(
            query_results=VespaResult(self.query_results),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )

        self.assertDictEqual(
            evaluation,
            {
                "match_ratio_retrieved_docs": 1083,
                "match_ratio_docs_available": 62529,
                "match_ratio_value": 1083 / 62529,
            },
        )

        evaluation = metric.evaluate_query(
            query_results=VespaResult(
                {
                    "root": {
                        "id": "toplevel",
                        "relevance": 1.0,
                        "coverage": {
                            "coverage": 100,
                            "documents": 62529,
                            "full": True,
                            "nodes": 2,
                            "results": 1,
                            "resultsFull": 1,
                        },
                    }
                }
            ),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )

        self.assertDictEqual(
            evaluation,
            {
                "match_ratio_retrieved_docs": 0,
                "match_ratio_docs_available": 62529,
                "match_ratio_value": 0 / 62529,
            },
        )

        evaluation = metric.evaluate_query(
            query_results=VespaResult(
                {
                    "root": {
                        "id": "toplevel",
                        "relevance": 1.0,
                        "fields": {"totalCount": 1083},
                        "coverage": {
                            "coverage": 100,
                            "full": True,
                            "nodes": 2,
                            "results": 1,
                            "resultsFull": 1,
                        },
                    }
                }
            ),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )

        self.assertDictEqual(
            evaluation,
            {
                "match_ratio_retrieved_docs": 1083,
                "match_ratio_docs_available": 0,
                "match_ratio_value": 0,
            },
        )

    def test_recall(self):
        metric = Recall(at=2)
        evaluation = metric.evaluate_query(
            query_results=VespaResult(self.query_results),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )
        self.assertDictEqual(
            evaluation, {"recall_2_value": 0.5,},
        )

        metric = Recall(at=1)
        evaluation = metric.evaluate_query(
            query_results=VespaResult(self.query_results),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )
        self.assertDictEqual(
            evaluation, {"recall_1_value": 0.0,},
        )

    def test_reciprocal_rank(self):
        metric = ReciprocalRank(at=2)
        evaluation = metric.evaluate_query(
            query_results=VespaResult(self.query_results),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )
        self.assertDictEqual(
            evaluation, {"reciprocal_rank_2_value": 0.5,},
        )

        metric = ReciprocalRank(at=1)
        evaluation = metric.evaluate_query(
            query_results=VespaResult(self.query_results),
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            id_field="vespa_id_field",
            default_score=0,
        )
        self.assertDictEqual(
            evaluation, {"reciprocal_rank_1_value": 0.0,},
        )
