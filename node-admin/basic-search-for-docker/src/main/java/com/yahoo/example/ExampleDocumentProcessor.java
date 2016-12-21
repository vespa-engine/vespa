package com.yahoo.example;

import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;

import java.net.*;
import java.io.*;

import com.yahoo.document.datatypes.StringFieldValue;
import org.json.*;
import java.util.logging.Logger;

/**
 * A document processor
 *
 * @author Joe Developer
 */


public class ExampleDocumentProcessor extends DocumentProcessor {

  private static final String artistField = "artist";
  private static final String artistIdField = "artistId";
  private static final String spotifyUrl = "https://api.spotify.com/v1/search?type=artist&limit=1&q=";
  private static final Logger log = Logger.getLogger(ExampleDocumentProcessor.class.getName());

  public Progress process(Processing processing) {
    for (DocumentOperation op : processing.getDocumentOperations()) {
      if (op instanceof DocumentPut) {
        Document document = ((DocumentPut) op).getDocument();
        addArtistId(document);
      } else if (op instanceof DocumentUpdate) {
        DocumentUpdate update = (DocumentUpdate) op;
        //TODO do something to 'update' here
      } else if (op instanceof DocumentRemove) {
        DocumentRemove remove = (DocumentRemove) op;
        //TODO do something to 'remove' here
      }
    }
    return Progress.DONE;
  }

  /**
   * Query Spotify API, parse JSON and set Artist ID
   *
   * @param document a Vespa Document
   */
  public void addArtistId(Document document) {
    StringFieldValue artistString = (StringFieldValue) document.getFieldValue(artistField);
    HttpURLConnection conn = null;
    try {
      String url = spotifyUrl + java.net.URLEncoder.encode(artistString.getString(), "UTF-8");
      conn = getConnection(url);
      String artistId = parseSpotifyResponse(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      if (artistId == null) {
        return;
      }
      document.setFieldValue(artistIdField, new StringFieldValue(artistId));

    } catch (Exception e) {
      log.warning("Error: " );
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  /**
   * @param streamReader the response to read from
   * @return artist id from spotify or null if not found
   * @throws IOException
   */

  private String parseSpotifyResponse(InputStreamReader streamReader) throws IOException {
    // Read JSON data from  API
    BufferedReader reader = new BufferedReader(streamReader);
    StringBuilder builder = new StringBuilder();
    for (String line; (line = reader.readLine()) != null; ) {
      builder.append(line).append("\n");
    }

    // Parse the JSON to find the first artist item returned or null if not found
    try {
      JSONObject json = new JSONObject(builder.toString());
      JSONObject artists = json.getJSONObject("artists");
      JSONArray items = artists.getJSONArray("items");
      JSONObject artist = items.getJSONObject(0);
      return artist.getString("id");
    } catch (JSONException e) {
      return null;
    }

  }

  /**
   * Establishes an HTTP Connection
   *
   * @param inputUrl a String giving the URL to connect to
   * @return an HttpURLConnection
   * @throws java.io.IOException when connection to inputUrl failed
   */
  private HttpURLConnection getConnection(String inputUrl) throws IOException {
    URL url = new URL(inputUrl);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestProperty("User-Agent", "Vespa Tutorial DocProc");
    conn.setReadTimeout(10000);
    conn.setConnectTimeout(5000);
    conn.connect();

    return conn;
  }
}

