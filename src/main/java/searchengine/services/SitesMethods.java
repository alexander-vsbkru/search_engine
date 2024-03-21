package searchengine.services;

import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.SiteEntityRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Класс методов работы с сайтами */
public class SitesMethods {
    SitesList sites;
    SiteEntityRepository siteEntityRepository;

    public SitesMethods(SitesList sites, SiteEntityRepository siteEntityRepository){
        this.sites = sites;
        this.siteEntityRepository = siteEntityRepository;
    }

    /** Получение сайта из базы
     * @param url {String} Принимает url в качестве параметра
     * @return {SiteEntity} Возвращает SiteEntity по переданному url из базы либо новый
     */
    public SiteEntity getSiteEntityFromDB(String url) {
        SiteEntity siteEntity = new SiteEntity();
        List<SiteEntity> siteEntityList = new ArrayList<>(siteEntityRepository.selectSiteIdByUrl(url));
        if (siteEntityList.isEmpty()) {
            siteEntity.setUrl(url);
            siteEntity.setStatus_time(LocalDateTime.now());
            siteEntity.setStatus(Status.INDEXING);
            Site site = getSite(url);
            siteEntity.setName(site.getName());
            siteEntity = siteEntityRepository.save(siteEntity);
        } else {
            siteEntity = siteEntityList.get(0);
        }
        return siteEntity;
    }

    /** Получение записи о сайте из  application.properties
     * @param url {String} Принимает url в качестве параметра
     * @return {Site} Возвращает Site из application.properties по переданному url
     */
    private Site getSite(String url) {
        for (Site site : sites.getSites()) {
            if (site.getUrl().equals(url))
                return site;
        }
        return new Site();
    }

    /** Удаление информации о сайтах, которых нет в application.yaml
    */
    public void deleteSitesNotInSetting() {
        List<SiteEntity> siteEntityList = siteEntityRepository.findAll();
        List<SiteEntity> siteEntityToRemove = new ArrayList<>();
        if (siteEntityList.isEmpty()) {
            return;
        }
        for (SiteEntity siteEntity : siteEntityList) {
            for (Site site : sites.getSites()) {
                if (siteEntity.getUrl().equals(site.getUrl())) {
                    siteEntityToRemove.add(siteEntity);
                }
            }
        }

        siteEntityList.removeAll(siteEntityToRemove);
        if (!siteEntityList.isEmpty()) {
            siteEntityRepository.deleteAll(siteEntityList);
        }
    }

    /** Проверка наличия сайта в application.yaml
     * @param url {String} Принимает url в качестве параметра
     * @return {boolean}
     */
    public boolean siteInSetting(String url) {
        for (Site site : sites.getSites()) {
            if (site.getUrl().equals(url)) {
                return true;
            }
        }
        return false;
    }

    /** Получение url сайта и путь до страницы
     * @param page {String} Принимает адрес страницы page
     * @return {PagePath} Возвращает новый PathPage с полями url (сайт) и path (путь до страницы)
     * по переданному адресу страницы
     */
    public static PagePath getUrlAndPath(String page) {
        String regex = "http(s)?://[A-z.:-_]+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(page);
        String siteUrl;
        String path;
        if (matcher.find()) {
            siteUrl = page.substring(matcher.start(), matcher.end());
            path = page.substring(matcher.end());
            return new PagePath(siteUrl, path);
        }
        return null;
    }
}
