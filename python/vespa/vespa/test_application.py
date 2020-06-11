# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import unittest
from unittest.mock import Mock, call
from pandas import DataFrame
from pandas.testing import assert_frame_equal

from vespa.application import Vespa
from vespa.query import Query, OR, RankProfile, VespaResult


class TestVespa(unittest.TestCase):
    def test_end_point(self):
        self.assertEqual(
            Vespa(url="https://cord19.vespa.ai").end_point, "https://cord19.vespa.ai"
        )
        self.assertEqual(
            Vespa(url="http://localhost", port=8080).end_point, "http://localhost:8080"
        )
        self.assertEqual(
            Vespa(url="http://localhost/", port=8080).end_point, "http://localhost:8080"
        )


class TestVespaQuery(unittest.TestCase):
    def test_query(self):
        app = Vespa(url="http://localhost", port=8080)

        body = {"yql": "select * from sources * where test"}
        self.assertDictEqual(
            app.query(body=body, debug_request=True).request_body, body
        )

        self.assertDictEqual(
            app.query(
                query="this is a test",
                query_model=Query(match_phase=OR(), rank_profile=RankProfile()),
                debug_request=True,
                hits=10,
            ).request_body,
            {
                "yql": 'select * from sources * where ([{"grammar": "any"}]userInput("this is a test"));',
                "ranking": {"profile": "default", "listFeatures": "false"},
                "hits": 10,
            },
        )

        self.assertDictEqual(
            app.query(
                query="this is a test",
                query_model=Query(match_phase=OR(), rank_profile=RankProfile()),
                debug_request=True,
                hits=10,
                recall=("id", [1, 5]),
            ).request_body,
            {
                "yql": 'select * from sources * where ([{"grammar": "any"}]userInput("this is a test"));',
                "ranking": {"profile": "default", "listFeatures": "false"},
                "hits": 10,
                "recall": "+(id:1 id:5)",
            },
        )


class TestVespaCollectData(unittest.TestCase):
    def setUp(self) -> None:
        self.app = Vespa(url="http://localhost", port=8080)
        self.raw_vespa_result_recall = {
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
                        "id": "id:covid-19:doc::40215",
                        "relevance": 30.368213170494712,
                        "source": "content",
                        "fields": {
                            "vespa_id_field": "abc",
                            "sddocname": "doc",
                            "body_text": "this is a body",
                            "title": "this is a title",
                            "rankfeatures": {"a": 1, "b": 2},
                        },
                    }
                ],
            }
        }

        self.raw_vespa_result_additional = {
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
                            "vespa_id_field": "def",
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
                            "vespa_id_field": "ghi",
                            "sddocname": "doc",
                            "body_text": "this is a body 3",
                            "title": "this is a title 3",
                            "rankfeatures": {"a": 5, "b": 6},
                        },
                    },
                ],
            }
        }

    def test_disable_rank_features(self):
        with self.assertRaises(AssertionError):
            self.app.collect_training_data_point(
                query="this is a query",
                query_id="123",
                relevant_id="abc",
                id_field="vespa_id_field",
                query_model=Query(),
                number_additional_docs=2,
            )

    def test_collect_training_data_point(self):

        self.app.query = Mock(
            side_effect=[
                VespaResult(self.raw_vespa_result_recall),
                VespaResult(self.raw_vespa_result_additional),
            ]
        )
        query_model = Query(rank_profile=RankProfile(list_features=True))
        data = self.app.collect_training_data_point(
            query="this is a query",
            query_id="123",
            relevant_id="abc",
            id_field="vespa_id_field",
            query_model=query_model,
            number_additional_docs=2,
            timeout="15s",
        )

        self.assertEqual(self.app.query.call_count, 2)
        self.app.query.assert_has_calls(
            [
                call(
                    query="this is a query",
                    query_model=query_model,
                    recall=("vespa_id_field", ["abc"]),
                    timeout="15s",
                ),
                call(
                    query="this is a query",
                    query_model=query_model,
                    hits=2,
                    timeout="15s",
                ),
            ]
        )
        expected_data = [
            {"document_id": "abc", "query_id": "123", "relevant": 1, "a": 1, "b": 2},
            {"document_id": "def", "query_id": "123", "relevant": 0, "a": 3, "b": 4},
            {"document_id": "ghi", "query_id": "123", "relevant": 0, "a": 5, "b": 6},
        ]
        self.assertEqual(data, expected_data)

    def test_collect_training_data_point_0_recall_hits(self):

        self.raw_vespa_result_recall = {
            "root": {
                "id": "toplevel",
                "relevance": 1.0,
                "fields": {"totalCount": 0},
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
        self.app.query = Mock(
            side_effect=[
                VespaResult(self.raw_vespa_result_recall),
                VespaResult(self.raw_vespa_result_additional),
            ]
        )
        query_model = Query(rank_profile=RankProfile(list_features=True))
        data = self.app.collect_training_data_point(
            query="this is a query",
            query_id="123",
            relevant_id="abc",
            id_field="vespa_id_field",
            query_model=query_model,
            number_additional_docs=2,
            timeout="15s",
        )

        self.assertEqual(self.app.query.call_count, 1)
        self.app.query.assert_has_calls(
            [
                call(
                    query="this is a query",
                    query_model=query_model,
                    recall=("vespa_id_field", ["abc"]),
                    timeout="15s",
                ),
            ]
        )
        expected_data = []
        self.assertEqual(data, expected_data)

    def test_collect_training_data(self):

        mock_return_value = [
            {"document_id": "abc", "query_id": "123", "relevant": 1, "a": 1, "b": 2,},
            {"document_id": "def", "query_id": "123", "relevant": 0, "a": 3, "b": 4,},
            {"document_id": "ghi", "query_id": "123", "relevant": 0, "a": 5, "b": 6,},
        ]
        self.app.collect_training_data_point = Mock(return_value=mock_return_value)
        labelled_data = [
            {
                "query_id": 123,
                "query": "this is a query",
                "relevant_docs": [{"id": "abc", "score": 1}],
            }
        ]
        query_model = Query(rank_profile=RankProfile(list_features=True))
        data = self.app.collect_training_data(
            labelled_data=labelled_data,
            id_field="vespa_id_field",
            query_model=query_model,
            number_additional_docs=2,
            timeout="15s",
        )
        self.app.collect_training_data_point.assert_has_calls(
            [
                call(
                    query="this is a query",
                    query_id=123,
                    relevant_id="abc",
                    id_field="vespa_id_field",
                    query_model=query_model,
                    number_additional_docs=2,
                    relevant_score=1,
                    default_score=0,
                    timeout="15s",
                )
            ]
        )
        assert_frame_equal(data, DataFrame.from_records(mock_return_value))


class TestVespaEvaluate(unittest.TestCase):
    def setUp(self) -> None:
        self.app = Vespa(url="http://localhost", port=8080)

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

    def test_evaluate_query(self):
        self.app.query = Mock(return_value={})
        eval_metric = Mock()
        eval_metric.evaluate_query = Mock(return_value={"metric": 1})
        eval_metric2 = Mock()
        eval_metric2.evaluate_query = Mock(return_value={"metric_2": 2})
        query_model = Query()
        evaluation = self.app.evaluate_query(
            eval_metrics=[eval_metric, eval_metric2],
            query_model=query_model,
            query_id="0",
            query="this is a test",
            id_field="vespa_id_field",
            relevant_docs=self.labelled_data[0]["relevant_docs"],
            default_score=0,
            hits=10,
        )
        self.assertEqual(self.app.query.call_count, 1)
        self.app.query.assert_has_calls(
            [call(query="this is a test", query_model=query_model, hits=10),]
        )
        self.assertEqual(eval_metric.evaluate_query.call_count, 1)
        eval_metric.evaluate_query.assert_has_calls(
            [call({}, self.labelled_data[0]["relevant_docs"], "vespa_id_field", 0),]
        )
        self.assertDictEqual(evaluation, {"query_id": "0", "metric": 1, "metric_2": 2})

    def test_evaluate(self):
        self.app.evaluate_query = Mock(side_effect=[{"query_id": "0", "metric": 1},])
        evaluation = self.app.evaluate(
            labelled_data=self.labelled_data,
            eval_metrics=[Mock()],
            query_model=Mock(),
            id_field="mock",
            default_score=0,
        )
        assert_frame_equal(
            evaluation, DataFrame.from_records([{"query_id": "0", "metric": 1}])
        )
