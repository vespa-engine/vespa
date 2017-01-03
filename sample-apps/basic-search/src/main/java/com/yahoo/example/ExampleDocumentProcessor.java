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

import java.util.logging.Level;
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
            } 
            else if (op instanceof DocumentUpdate) {
                DocumentUpdate update = (DocumentUpdate) op;
                // Updates to existing documents can be modified here
            } 
            else if (op instanceof DocumentRemove) {
                DocumentRemove remove = (DocumentRemove) op;
                // Document removes can be modified here
            }
        }
        return Progress.DONE;
    }

    /** Queries the Spotify API, parses JSON and sets Artist ID */
    public void addArtistId(Document document) {
        StringFieldValue artistString = (StringFieldValue) document.getFieldValue(artistField);

        HttpURLConnection connection = null;
        try {
            connection = getConnection(spotifyUrl + java.net.URLEncoder.encode(artistString.getString(), "UTF-8"));
            String artistId = parseSpotifyResponse(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            if (artistId == null) return;

            document.setFieldValue(artistIdField, new StringFieldValue(artistId));
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Could not find artist id for '" + document.getId(), e);
        }
        finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    /** Returns an artist id from spotify or null if not found */
    private String parseSpotifyResponse(InputStreamReader streamReader) throws IOException, JSONException {
        // Read JSON data from  API
        BufferedReader reader = new BufferedReader(streamReader);
        StringBuilder builder = new StringBuilder();
        for (String line; (line = reader.readLine()) != null; ) {
            builder.append(line).append("\n");
        }

        // Parse the JSON to find the first artist item returned or null if not found
        JSONObject json = new JSONObject(builder.toString());
        JSONObject artists = json.getJSONObject("artists");
        JSONArray items = artists.getJSONArray("items");
        JSONObject artist = items.getJSONObject(0);
        return artist.getString("id");
    }

    /** Establishes an HTTP Connection */
    private HttpURLConnection getConnection(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestProperty("User-Agent", "Vespa Tutorial DocProc");
        connection.setReadTimeout(10000);
        connection.setConnectTimeout(5000);
        connection.connect();
        return connection;
    }

}

