package searchengine.services;

import org.json.simple.JSONObject;
import searchengine.dto.index.IndexResponse;

import java.io.IOException;

public interface IndexingService {
    IndexResponse startIndexing() throws IOException;
    IndexResponse stopIndexing();
    IndexResponse indexPage(String page) throws IOException;
}
