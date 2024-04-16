package searchengine.dto.index;

import lombok.Data;

 /** Класс ответа индексации с ошибкой */
@Data
public class IndexResponseError implements IndexResponse{
    private final String error;
    public IndexResponseError(String error){
        this.error = error;
    }

    @Override
    public boolean getResult() {
        return false;
    }
}
