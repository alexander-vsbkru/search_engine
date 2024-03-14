package searchengine.dto.search;

import lombok.Data;

/** Класс ответа поиска с ошибкой */

@Data
public class SearchResponseError implements SearchResponse {
    private boolean result = false;
    private String error;

public SearchResponseError(String error) {
    this.error = error;
}

    @Override
    public boolean getResult() {
        return false;
    }
}
