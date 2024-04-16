package searchengine.utils;

/** Класс хранения адреса страницы, разделенного на адрес сайта и адрес самой страницы */
public record PagePath(String siteUrl, String path) {

    public String getSiteUrl() {
        return siteUrl;
    }

    public String getPath() {
        return path;
    }

}
