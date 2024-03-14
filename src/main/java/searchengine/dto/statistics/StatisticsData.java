package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

/** Класс данных статистики */
@Data
public class StatisticsData {
    private TotalStatistics total;
    private List<DetailedStatisticsItem> detailed;
}
