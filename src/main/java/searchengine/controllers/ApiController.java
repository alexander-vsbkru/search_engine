package searchengine.controllers;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController<StopIndexingService> {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @Autowired
    private SitesList sites;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<JSONObject> startIndexing() throws IOException {

        JSONObject response = indexingService.startIndexing();
        if (response.get("result").equals(true)) {
            return ResponseEntity.ok(response);
        }
        else {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<JSONObject> stopIndexing() {

        JSONObject response = indexingService.stopIndexing();
        if (response.get("result").equals(true)) {
            return ResponseEntity.ok(response);
        }
        else {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
        }

    }

    @PostMapping(value = "/indexPage")
    public ResponseEntity<JSONObject> indexPage(@RequestParam String url) throws IOException {

        JSONObject response = indexingService.indexPage(url);
        if (response.get("result").equals(true)) {
            return ResponseEntity.ok(response);
        }
        else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

    }

    @GetMapping("/search")
    public ResponseEntity<JSONObject> search(@RequestParam String query,
                                             @RequestParam(value = "site", required = false, defaultValue="") String site,
                                             @RequestParam int offset,
                                             @RequestParam int limit) {

        JSONObject response = indexingService.search(query, site, offset, limit);
        if (response.get("result").equals(true)) {
            return ResponseEntity.ok(response);
        }
        else {
            return ResponseEntity.badRequest().body(response);
        }

    }


}
