import React from 'react';
import QueryInput from './Components/Text/QueryInput';
import SendQuery from './Components/Text/SendQuery';
import PasteJSONButton from './Components/Buttons/PasteJSONButton';
import CopyResponseButton from './Components/Buttons/CopyResponseButton';
import DownloadJSONButton from './Components/Buttons/DownloadJSONButton';
import {
  QueryBuilderProvider,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

import '../../styles/agency.css';
import '../../styles/vespa.css';

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
    <header>
      <div className="intro container">
        <p className="intro-lead-in">Vespa Search Engine</p>
        <p className="intro-long">
          Select the method for sending a request and construct a query.
        </p>
        <QueryBuilderProvider>
          <SendQuery />
          <br />
          <div id="request">
            <QueryInput />
          </div>
          <br />
          <PasteJSONButton />
          <QueryBox />
          <p className="response">Response</p>
          <ResponseBox />
        </QueryBuilderProvider>
        <br />
        <br />
      </div>
    </header>
  );
}
