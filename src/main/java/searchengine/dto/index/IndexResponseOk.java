package searchengine.dto.index;

import lombok.Data;

/** Класс ответа индексации с ошибкой */
@Data
 public class IndexResponseOk implements IndexResponse{
    boolean result = true;

    @Override
    public boolean getResult() {
        return true;
    }
}
