import React from 'react';
import SimpleButton from './Components/Buttons/SimpleButton';
import QueryInput from './Components/Text/QueryInput';
import TextBox from './Components/Text/TextBox';
import ImageButton from './Components/Buttons/ImageButton';
import OverlayImageButton from './Components/Buttons/OverlayImageButton';
import AddQueryInput from './Components/Buttons/AddQueryInputButton';
import { QueryInputProvider } from './Components/Contexts/QueryInputContext';
import SendQuery from './Components/Text/SendQuery';
import { ResponseProvider } from './Components/Contexts/ResponseContext';
import ResponseBox from './Components/Text/ResponseBox';

import pasteImage from './assets/img/paste.svg';
import copyImage from './assets/img/copy.svg';

import '../../styles/agency.css';
import '../../styles/vespa.css';

//import 'bootstrap/dist/css/bootstrap.min.css'; //TODO: Find out how to get this css

export function QueryBuilder() {
  const messageMethodArray = ['POST', 'GET'];

  return (
    <>
      <header>
        <div className="intro container">
          <TextBox className={'intro-lead-in'}>Vespa Search Engine</TextBox>
          <TextBox className={'intro-long'}>
            Select the method for sending a request and construct a query.
          </TextBox>
          <ResponseProvider>
            <QueryInputProvider>
              <SendQuery />
              <br />
              <div id="request">
                <QueryInput />
              </div>
              <br />
              <AddQueryInput />
            </QueryInputProvider>
            <br />
            <ImageButton
              id="pasteJSON"
              className="pasteJSON"
              showImage={true}
              image={pasteImage}
              style={{ marginTop: '-2px', marginRight: '3px' }}
            >
              Paste JSON
            </ImageButton>
            <SimpleButton className="showJSON">Show query JSON</SimpleButton>
            <TextBox className="response">Response</TextBox>
            <ResponseBox />
          </ResponseProvider>
          <OverlayImageButton
            className="intro-copy"
            image={copyImage}
            height="30"
            width="30"
            tooltip="Copy"
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
