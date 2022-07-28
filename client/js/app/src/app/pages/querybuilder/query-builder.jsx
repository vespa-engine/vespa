import React from 'react';
import QueryInput from './Components/Text/QueryInput';
import TextBox from './Components/Text/TextBox';
import AddQueryInput from './Components/Buttons/AddQueryInputButton';
import { QueryInputProvider } from './Components/Contexts/QueryInputContext';
import SendQuery from './Components/Text/SendQuery';
import { ResponseProvider } from './Components/Contexts/ResponseContext';
import ResponseBox from './Components/Text/ResponseBox';
import ShowQueryButton from './Components/Buttons/ShowQueryButton';
import { QueryProvider } from './Components/Contexts/QueryContext';
import PasteJSONButton from './Components/Buttons/PasteJSONButton';
import CopyResponseButton from './Components/Buttons/CopyResponseButton';
import DownloadJSONButton from './Components/Buttons/DownloadJSONButton';

import '../../styles/agency.css';
import '../../styles/vespa.css';

export function QueryBuilder() {
  return (
    <>
      <header>
        <div className="intro container">
          <TextBox className={'intro-lead-in'}>Vespa Search Engine</TextBox>
          <TextBox className={'intro-long'}>
            Select the method for sending a request and construct a query.
          </TextBox>
          <ResponseProvider>
            <QueryProvider>
              <QueryInputProvider>
                <SendQuery />
                <br />
                <div id="request">
                  <QueryInput />
                </div>
                <br />
                <AddQueryInput />
                <br />
                <PasteJSONButton />
              </QueryInputProvider>
              <ShowQueryButton />
            </QueryProvider>
            <TextBox className="response">Response</TextBox>
            <ResponseBox />
            <CopyResponseButton />
            <DownloadJSONButton />
          </ResponseProvider>
          <br />
          <br />
        </div>
      </header>
    </>
  );
}
