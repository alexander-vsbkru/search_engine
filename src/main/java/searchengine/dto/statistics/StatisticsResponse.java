package searchengine.dto.statistics;

import lombok.Data;

/**  Класс ответа статистики */
@Data
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
