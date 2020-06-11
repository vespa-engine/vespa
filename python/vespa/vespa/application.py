# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

from typing import Optional, Dict, Tuple, List
from requests import post
from pandas import DataFrame

from vespa.query import Query, VespaResult
from vespa.evaluation import EvalMetric


class Vespa(object):
    def __init__(self, url: str, port: Optional[int] = None) -> None:
        """
        Establish a connection with a Vespa application.

        :param url: URL
        :param port: Port

            >>> Vespa(url = "https://cord19.vespa.ai")
            >>> Vespa(url = "http://localhost", port = 8080)

        """
        self.url = url
        self.port = port

        if port is None:
            self.end_point = self.url
        else:
            self.end_point = str(url).rstrip("/") + ":" + str(port)
        self.search_end_point = self.end_point + "/search/"

    def query(
        self,
        body: Optional[Dict] = None,
        query: Optional[str] = None,
        query_model: Optional[Query] = None,
        debug_request: bool = False,
        recall: Optional[Tuple] = None,
        **kwargs
    ) -> VespaResult:
        """
        Send a query request to the Vespa application.

        Either send 'body' containing all the request parameters or specify 'query' and 'query_model'.

        :param body: Dict containing all the request parameters.
        :param query: Query string
        :param query_model: Query model
        :param debug_request: return request body for debugging instead of sending the request.
        :param recall: Tuple of size 2 where the first element is the name of the field to use to recall and the
            second element is a list of the values to be recalled.
        :param kwargs: Additional parameters to be sent along the request.
        :return: Either the request body if debug_request is True or the result from the Vespa application
        """

        if body is None:
            assert query is not None, "No 'query' specified."
            assert query_model is not None, "No 'query_model' specified."
            body = query_model.create_body(query=query)
            if recall is not None:
                body.update(
                    {
                        "recall": "+("
                        + " ".join(
                            ["{}:{}".format(recall[0], str(doc)) for doc in recall[1]]
                        )
                        + ")"
                    }
                )

            body.update(kwargs)

        if debug_request:
            return VespaResult(vespa_result={}, request_body=body)
        else:
            r = post(self.search_end_point, json=body)
            return VespaResult(vespa_result=r.json())

    def collect_training_data_point(
        self,
        query: str,
        query_id: str,
        relevant_id: str,
        id_field: str,
        query_model: Query,
        number_additional_docs: int,
        relevant_score: int = 1,
        default_score: int = 0,
        **kwargs
    ) -> List[Dict]:
        """
        Collect training data based on a single query

        :param query: Query string.
        :param query_id: Query id represented as str.
        :param relevant_id: Relevant id represented as a str.
        :param id_field: The Vespa field representing the document id.
        :param query_model: Query model.
        :param number_additional_docs: Number of additional documents to retrieve for each relevant document.
        :param relevant_score: Score to assign to relevant documents. Default to 1.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :param kwargs: Extra keyword arguments to be included in the Vespa Query.
        :return: List of dicts containing the document id (document_id), query id (query_id), scores (relevant)
            and vespa rank features returned by the Query model RankProfile used.
        """

        assert (
            query_model.rank_profile.list_features == "true"
        ), "Enable rank features via RankProfile is necessary."

        relevant_id_result = self.query(
            query=query,
            query_model=query_model,
            recall=(id_field, [relevant_id]),
            **kwargs
        )
        hits = relevant_id_result.hits
        features = []
        if len(hits) == 1 and hits[0]["fields"][id_field] == relevant_id:
            random_hits_result = self.query(
                query=query,
                query_model=query_model,
                hits=number_additional_docs,
                **kwargs
            )
            hits.extend(random_hits_result.hits)

            features = annotate_data(
                hits=hits,
                query_id=query_id,
                id_field=id_field,
                relevant_id=relevant_id,
                relevant_score=relevant_score,
                default_score=default_score,
            )
        return features

    def collect_training_data(
        self,
        labelled_data: List[Dict],
        id_field: str,
        query_model: Query,
        number_additional_docs: int,
        relevant_score: int = 1,
        default_score: int = 0,
        **kwargs
    ) -> DataFrame:
        """
        Collect training data based on a set of labelled data.

        :param labelled_data: Labelled data containing query, query_id and relevant ids.
        :param id_field: The Vespa field representing the document id.
        :param query_model: Query model.
        :param number_additional_docs: Number of additional documents to retrieve for each relevant document.
        :param relevant_score: Score to assign to relevant documents. Default to 1.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :param kwargs: Extra keyword arguments to be included in the Vespa Query.
        :return: DataFrame containing document id (document_id), query id (query_id), scores (relevant)
            and vespa rank features returned by the Query model RankProfile used.
        """

        training_data = []
        for query_data in labelled_data:
            for doc_data in query_data["relevant_docs"]:
                training_data_point = self.collect_training_data_point(
                    query=query_data["query"],
                    query_id=query_data["query_id"],
                    relevant_id=doc_data["id"],
                    id_field=id_field,
                    query_model=query_model,
                    number_additional_docs=number_additional_docs,
                    relevant_score=doc_data.get("score", relevant_score),
                    default_score=default_score,
                    **kwargs
                )
                training_data.extend(training_data_point)
        training_data = DataFrame.from_records(training_data)
        return training_data

    def evaluate_query(
        self,
        eval_metrics: List[EvalMetric],
        query_model: Query,
        query_id: str,
        query: str,
        id_field: str,
        relevant_docs: List[Dict],
        default_score: int = 0,
        **kwargs
    ) -> Dict:
        """
        Evaluate a query according to evaluation metrics

        :param eval_metrics: A list of evaluation metrics.
        :param query_model: Query model.
        :param query_id: Query id represented as str.
        :param query: Query string.
        :param id_field: The Vespa field representing the document id.
        :param relevant_docs: A list with dicts where each dict contains a doc id a optionally a doc score.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :param kwargs: Extra keyword arguments to be included in the Vespa Query.
        :return: Dict containing query_id and metrics according to the selected evaluation metrics.
        """

        query_results = self.query(query=query, query_model=query_model, **kwargs)
        evaluation = {"query_id": query_id}
        for evaluator in eval_metrics:
            evaluation.update(
                evaluator.evaluate_query(
                    query_results, relevant_docs, id_field, default_score
                )
            )
        return evaluation

    def evaluate(
        self,
        labelled_data: List[Dict],
        eval_metrics: List[EvalMetric],
        query_model: Query,
        id_field: str,
        default_score: int = 0,
        **kwargs
    ) -> DataFrame:
        """

        :param labelled_data: Labelled data containing query, query_id and relevant ids.
        :param eval_metrics: A list of evaluation metrics.
        :param query_model: Query model.
        :param id_field: The Vespa field representing the document id.
        :param default_score: Score to assign to the additional documents that are not relevant. Default to 0.
        :param kwargs: Extra keyword arguments to be included in the Vespa Query.
        :return: DataFrame containing query_id and metrics according to the selected evaluation metrics.
        """
        evaluation = []
        for query_data in labelled_data:
            evaluation_query = self.evaluate_query(
                eval_metrics=eval_metrics,
                query_model=query_model,
                query_id=query_data["query_id"],
                query=query_data["query"],
                id_field=id_field,
                relevant_docs=query_data["relevant_docs"],
                default_score=default_score,
                **kwargs
            )
            evaluation.append(evaluation_query)
        evaluation = DataFrame.from_records(evaluation)
        return evaluation


# todo: a better pattern for labelled data would be (query_id, query, doc_id, score) with the possibility od
#  assigning a specific default value for those docs not mentioned
def annotate_data(hits, query_id, id_field, relevant_id, relevant_score, default_score):
    data = []
    for h in hits:
        rank_features = h["fields"]["rankfeatures"]
        rank_features.update({"document_id": h["fields"][id_field]})
        rank_features.update({"query_id": query_id})
        rank_features.update(
            {
                "relevant": relevant_score
                if h["fields"][id_field] == relevant_id
                else default_score
            }
        )
        data.append(rank_features)
    return data
