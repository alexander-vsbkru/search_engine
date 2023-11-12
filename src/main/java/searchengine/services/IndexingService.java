package searchengine.services;

import org.json.simple.JSONObject;

import java.io.IOException;

public interface IndexingService {
    JSONObject startIndexing() throws IOException;
    JSONObject stopIndexing();
    JSONObject indexPage(String page) throws IOException;
    JSONObject search(String query, String site, int offset, int limit);
}
