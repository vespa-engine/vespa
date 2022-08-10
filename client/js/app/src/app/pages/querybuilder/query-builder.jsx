import React from 'react';
import QueryInput from './Components/Text/QueryInput';
import SendQuery from './Components/Text/SendQuery';
import ResponseBox from './Components/Text/ResponseBox';
import ShowQueryButton from './Components/Buttons/ShowQueryButton';
import PasteJSONButton from './Components/Buttons/PasteJSONButton';
import CopyResponseButton from './Components/Buttons/CopyResponseButton';
import DownloadJSONButton from './Components/Buttons/DownloadJSONButton';
import { QueryBuilderProvider } from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

import '../../styles/agency.css';
import '../../styles/vespa.css';

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
          <ShowQueryButton />
          <p className="response">Response</p>
          <ResponseBox />
          <CopyResponseButton />
          <DownloadJSONButton>Download response as JSON</DownloadJSONButton>
        </QueryBuilderProvider>
        <br />
        <br />
      </div>
    </header>
  );
}
