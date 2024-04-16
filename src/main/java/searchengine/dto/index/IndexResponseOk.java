package searchengine.dto.index;

import lombok.Data;

/** Класс ответа индексации с ошибкой */
@Data
 public class IndexResponseOk implements IndexResponse{
    @Override
    public boolean getResult() {
        return true;
    }
}
