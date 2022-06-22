import "./css/agency.css";
import "./css/vespa.css"
import "bootstrap/dist/css/bootstrap.min.css";
import "./font-awesome/css/font-awesome.min.css";

import React from 'react';
import ReactDOM from 'react-dom/client';
import SimpleButton from 'Components/Buttons/SimpleButton';
import QueryInput from 'Components/Text/QueryInput';
import SimpleDropDownForm from 'Components/Text/SimpleDropDownForm';
import SimpleForm from 'Components/Text/SimpleForm';
import TextBox from 'Components/Text/TextBox';
import ImageButton from "Components/Buttons/ImageButton";
import OverlayImageButton from "Components/Buttons/OverlayImageButton";
import AddQueryInput from "Components/Buttons/AddQueryInputButton";
import { QueryInputProvider } from "Components/Contexts/QueryInputContext";



const root = ReactDOM.createRoot(document.getElementById('root'));
const messageMethodArray = ["POST", "GET"];

const pasteImage = require("./assets/img/paste.svg").default;
const copyImage = require("./assets/img/copy.svg").default;
const refreshImage = require("./assets/img/reload.svg").default;


root.render(
  <>
    <React.StrictMode>
      <header>
        <div className="intro container">
          <TextBox className={"intro-lead-in"}>Vespa Search Engine</TextBox>
          <TextBox className={"intro-long"}>Select the method for sending a request and construct a query.</TextBox>
          <SimpleDropDownForm choices={messageMethodArray} id="method" className='methodselector'></SimpleDropDownForm>
          <SimpleForm id="url" className='textbox' initial="http://localhost:8080/search/" size="30"></SimpleForm>
          <SimpleButton id="send" className="button" onClick={handleClick}>Send</SimpleButton>
          <br/>
          <QueryInputProvider>
            <div id="request">
              <QueryInput></QueryInput>
            </div>
            <br/>
            <AddQueryInput/>
          </QueryInputProvider>
          <br/>
          <ImageButton id="pasteJSON" className="pasteJSON" showImage={true} image={pasteImage} style={{marginTop:"-2px", marginRight: "3px"}}>Paste JSON</ImageButton>
          <SimpleButton className="showJSON">Show query JSON</SimpleButton>
          <TextBox className="response">Response</TextBox>
          <textarea className="responsebox" readOnly cols="70" rows="25"></textarea>
          <OverlayImageButton className="intro-copy" image={copyImage} height="30" width="30" tooltip="Copy">Copy</OverlayImageButton>
          <OverlayImageButton className="intro-refresh" image={refreshImage} height="30" width="30" tooltip="Refresh">Refresh</OverlayImageButton>
          <br/>
          <br/>
        </div>
      </header>
    </React.StrictMode>
  </>
);

function handleClick() {
  console.log("Click happened");
}
