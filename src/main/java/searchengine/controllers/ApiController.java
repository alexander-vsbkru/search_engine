package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.index.IndexResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;

/** Контроллер для работы с API */
@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController<StopIndexingService> {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    /** Получение статистики */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    /** Запуск общей индексации */
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexResponse> startIndexing() {

        IndexResponse response = null;
        try {
            response = indexingService.startIndexing();
        } catch (IOException e) {
            log.error(e.toString());
        }
        if (response != null && response.getResult()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /** Остановка индексации */
    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexResponse> stopIndexing() {

        IndexResponse response = indexingService.stopIndexing();
        if (response.getResult()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
        }

    /** Индексация одной страницы */
    @PostMapping(value = "/indexPage")
    public ResponseEntity<IndexResponse> indexPage(@RequestParam String url) {

        IndexResponse response = null;
        try {
            response = indexingService.indexPage(url);
        } catch (IOException e) {
            log.error(e.toString());
        }
        if (response != null && response.getResult()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /** Поиск */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query,
                                                 @RequestParam(value = "site", required = false, defaultValue="") String site,
                                                 @RequestParam int offset,
                                                 @RequestParam int limit) {
        SearchResponse response = searchService.search(query, site, offset, limit);
        if (response.getResult()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.badRequest().body(response);
    }

}
