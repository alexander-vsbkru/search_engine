package searchengine.dto.search;

import lombok.Data;

/** Класс данных по результатам поиска */
@Data
public class SearchData {
        private String site;
        private String siteName;
        private String url;
        private String title;
        private String snippet;
        private float relevance;

}
