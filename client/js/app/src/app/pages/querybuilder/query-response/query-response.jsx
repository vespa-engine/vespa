import React from 'react';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import CopyResponseButton from 'app/pages/querybuilder/query-response/CopyResponseButton';
import { DownloadJson } from 'app/components';

export function QueryResponse() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);
  return (
    <>
      <textarea readOnly cols="70" rows="25" value={response} />
      <CopyResponseButton />
      <DownloadJson response={response}>Download in Jeager format</DownloadJson>
    </>
  );
}
