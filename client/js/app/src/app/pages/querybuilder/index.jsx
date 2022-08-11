import React from 'react';
import QueryInput from './Components/Text/QueryInput';
import SendQuery from './Components/Text/SendQuery';
import PasteJSONButton from './Components/Buttons/PasteJSONButton';
import CopyResponseButton from './Components/Buttons/CopyResponseButton';
import DownloadJSONButton from './Components/Buttons/DownloadJSONButton';
import {
  QueryBuilderProvider,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';

function QueryBox() {
  const query = useQueryBuilderContext((ctx) => ctx.query.input);
  return <textarea readOnly cols="70" rows="15" value={query}></textarea>;
}

function ResponseBox() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);
  return (
    <>
      <textarea readOnly cols="70" rows="25" value={response} />
      <CopyResponseButton />
      <DownloadJSONButton response={response}>
        Download in Jeager format
      </DownloadJSONButton>
    </>
  );
}

export function QueryBuilder() {
  return (
    <QueryBuilderProvider>
      <SendQuery />
      <QueryInput />
      <PasteJSONButton />
      <QueryBox />
      <ResponseBox />
    </QueryBuilderProvider>
  );
}
