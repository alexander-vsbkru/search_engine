package searchengine.dto.statistics;

import lombok.Data;

/** Класс общей статистики */
@Data
public class TotalStatistics {
    private int sites;
    private int pages;
    private int lemmas;
    private boolean indexing;
}
