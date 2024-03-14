package searchengine.config;

import lombok.Getter;
import lombok.Setter;

/** Класс для сайта для работами с сайтами из файла настроек */
@Setter
@Getter
public class Site {
    private String url;
    private String name;
}
