# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import unittest

from vespa.query import Query, OR, AND, WeakAnd, ANN, Union, RankProfile, VespaResult


class TestMatchFilter(unittest.TestCase):
    def setUp(self) -> None:
        self.query = "this is  a test"

    def test_and(self):
        match_filter = AND()
        self.assertEqual(
            match_filter.create_match_filter(query=self.query),
            '(userInput("this is  a test"))',
        )
        self.assertDictEqual(match_filter.get_query_properties(query=self.query), {})

    def test_or(self):
        match_filter = OR()
        self.assertEqual(
            match_filter.create_match_filter(query=self.query),
            '([{"grammar": "any"}]userInput("this is  a test"))',
        )
        self.assertDictEqual(match_filter.get_query_properties(query=self.query), {})

    def test_weak_and(self):
        match_filter = WeakAnd(hits=10, field="field_name")
        self.assertEqual(
            match_filter.create_match_filter(query=self.query),
            '([{"targetNumHits": 10}]weakAnd(field_name contains "this", field_name contains "is", field_name contains "", '
            'field_name contains "a", field_name contains "test"))',
        )
        self.assertDictEqual(match_filter.get_query_properties(query=self.query), {})

    def test_ann(self):
        match_filter = ANN(
            doc_vector="doc_vector",
            query_vector="query_vector",
            embedding_model=lambda x: [1, 2, 3],
            hits=10,
            label="label",
        )
        self.assertEqual(
            match_filter.create_match_filter(query=self.query),
            '([{"targetNumHits": 10, "label": "label"}]nearestNeighbor(doc_vector, query_vector))',
        )
        self.assertDictEqual(
            match_filter.get_query_properties(query=self.query),
            {"ranking.features.query(query_vector)": "[1, 2, 3]"},
        )

    def test_union(self):
        match_filter = Union(
            WeakAnd(hits=10, field="field_name"),
            ANN(
                doc_vector="doc_vector",
                query_vector="query_vector",
                embedding_model=lambda x: [1, 2, 3],
                hits=10,
                label="label",
            ),
        )
        self.assertEqual(
            match_filter.create_match_filter(query=self.query),
            '([{"targetNumHits": 10}]weakAnd(field_name contains "this", field_name contains "is", '
            'field_name contains "", '
            'field_name contains "a", field_name contains "test")) or '
            '([{"targetNumHits": 10, "label": "label"}]nearestNeighbor(doc_vector, query_vector))',
        )
        self.assertDictEqual(
            match_filter.get_query_properties(query=self.query),
            {"ranking.features.query(query_vector)": "[1, 2, 3]"},
        )


class TestRankProfile(unittest.TestCase):
    def test_rank_profile(self):
        rank_profile = RankProfile(name="rank_profile", list_features=True)
        self.assertEqual(rank_profile.name, "rank_profile")
        self.assertEqual(rank_profile.list_features, "true")


class TestQuery(unittest.TestCase):
    def setUp(self) -> None:
        self.query = "this is  a test"

    def test_default(self):
        query = Query()
        self.assertDictEqual(
            query.create_body(query=self.query),
            {
                "yql": 'select * from sources * where (userInput("this is  a test"));',
                "ranking": {"profile": "default", "listFeatures": "false"},
            },
        )

    def test_match_and_rank(self):
        query = Query(
            match_phase=ANN(
                doc_vector="doc_vector",
                query_vector="query_vector",
                embedding_model=lambda x: [1, 2, 3],
                hits=10,
                label="label",
            ),
            rank_profile=RankProfile(name="bm25", list_features=True),
        )
        self.assertDictEqual(
            query.create_body(query=self.query),
            {
                "yql": 'select * from sources * where ([{"targetNumHits": 10, "label": "label"}]nearestNeighbor(doc_vector, query_vector));',
                "ranking": {"profile": "bm25", "listFeatures": "true"},
                "ranking.features.query(query_vector)": "[1, 2, 3]",
            },
        )


class TestVespaResult(unittest.TestCase):
    def setUp(self) -> None:
        self.raw_vespa_result_empty_hits = {
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

        self.raw_vespa_result = {
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
                            "sddocname": "doc",
                            "body_text": "this is a body",
                            "title": "this is a title",
                        },
                    }
                ],
            }
        }

    def test_json(self):
        vespa_result = VespaResult(vespa_result=self.raw_vespa_result)
        self.assertDictEqual(vespa_result.json, self.raw_vespa_result)

    def test_hits(self):
        empty_hits_vespa_result = VespaResult(
            vespa_result=self.raw_vespa_result_empty_hits
        )
        self.assertEqual(empty_hits_vespa_result.hits, [])
        vespa_result = VespaResult(vespa_result=self.raw_vespa_result)
        self.assertEqual(
            vespa_result.hits,
            [
                {
                    "id": "id:covid-19:doc::40215",
                    "relevance": 30.368213170494712,
                    "source": "content",
                    "fields": {
                        "sddocname": "doc",
                        "body_text": "this is a body",
                        "title": "this is a title",
                    },
                }
            ],
        )
