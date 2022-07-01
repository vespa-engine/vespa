import React, { useContext } from 'react';
import SimpleButton from './Components/Buttons/SimpleButton';
import QueryInput from './Components/Text/QueryInput';
import TextBox from './Components/Text/TextBox';
import ImageButton from './Components/Buttons/ImageButton';
import OverlayImageButton from './Components/Buttons/OverlayImageButton';
import AddQueryInput from './Components/Buttons/AddQueryInputButton';
import { QueryInputProvider } from './Components/Contexts/QueryInputContext';
import SendQuery from './Components/Text/SendQuery';
import {
  ResponseContext,
  ResponseProvider,
} from './Components/Contexts/ResponseContext';
import ResponseBox from './Components/Text/ResponseBox';

import copyImage from './assets/img/copy.svg';

import '../../styles/agency.css';
import '../../styles/vespa.css';
import ShowQueryButton from './Components/Buttons/ShowQueryButton';
import { QueryProvider } from './Components/Contexts/QueryContext';
import PasteJSONButton from './Components/Buttons/PasteJSONButton';

//import 'bootstrap/dist/css/bootstrap.min.css'; //TODO: Find out how to get this css

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
          </ResponseProvider>
          <OverlayImageButton
            className="intro-copy"
            image={copyImage}
            height="30"
            width="30"
            tooltip="Copy"
            onClick={() => {
              alert('Button is non-functional');
            }}
          >
            Copy
          </OverlayImageButton>
          <br />
          <br />
        </div>
      </header>
    </>
  );
}
