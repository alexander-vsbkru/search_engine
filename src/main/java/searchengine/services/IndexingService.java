package searchengine.services;

import org.json.simple.JSONObject;

public interface IndexingService {
    JSONObject startIndexing();
    JSONObject stopIndexing();

}
