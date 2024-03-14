package searchengine.dto.search;

import lombok.Data;

import java.util.List;

/** Класс ответа поиска без ошибки */

@Data
public class SearchResponseOk implements SearchResponse{
    private boolean result = true;
    private int count;
    private List<SearchData> data;

    @Override
    public boolean getResult() {
        return true;
    }
}
