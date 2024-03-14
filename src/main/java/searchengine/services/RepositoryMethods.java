package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import searchengine.model.*;
import searchengine.repositories.IndexEntityRepository;
import searchengine.repositories.LemmaEntityRepository;
import searchengine.repositories.PageEntityRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Класс методов для работы с репозиторием */
public class RepositoryMethods {
    LemmaEntityRepository lemmaEntityRepository;
    IndexEntityRepository indexEntityRepository;
    PageEntityRepository pageEntityRepository;

    public RepositoryMethods(LemmaEntityRepository lemmaEntityRepository,
                             IndexEntityRepository indexEntityRepository,
                             PageEntityRepository pageEntityRepository) {
        this.lemmaEntityRepository = lemmaEntityRepository;
        this.indexEntityRepository =  indexEntityRepository;
        this.pageEntityRepository = pageEntityRepository;
    }

    /** Добавление лемм и индексов для страницы
     * @param pageEntity {PageEntity} Принимает адрес страницы page
     */
    public void addLemmaIndex(PageEntity pageEntity) throws IOException {
        GetLemmasFromText getLemmasFromText = new GetLemmasFromText();
        HashMap<String, Integer> lemmaMap = new HashMap<>(getLemmasFromText.getLemmaList(pageEntity.getContent()));
        for (String keyLemma : lemmaMap.keySet()) {
            LemmaEntity lemmaEntity = getLemmaEntity(pageEntity, keyLemma);
            String lemma = lemmaEntityRepository.save(lemmaEntity).getLemma();
            List<IndexEntity> indexFromDB = indexEntityRepository.selectIndexIdByPageIdAndLemmaId(
                    pageEntity.getId(), lemmaEntityRepository.selectLemmaIdByLemma(lemma));
            IndexEntity indexEntity = getIndexEntityFromDB(indexFromDB, lemmaEntity, pageEntity);
            indexEntity.setRank(lemmaMap.get(keyLemma));
            indexEntityRepository.save(indexEntity);
        }
    }

    /** Получение записи LemmaEntity по странице и лемме
     * @param pageEntity {PageEntity} Принимает страницу из базы в качестве параметра
     * @param lemma {String} Принимает лемму в качестве параметра
     * @return {LemmaEntity} Возвращает LemmaEntity в соответствии с параметрами
     */
    private LemmaEntity getLemmaEntity(PageEntity pageEntity, String lemma) {
        List<Integer> siteIds = new ArrayList<>();
        siteIds.add(pageEntity.getSite().getId());
        List<String> lemmas = new ArrayList<>();
        lemmas.add(lemma);
        List<LemmaEntity> lemmaFromDB = lemmaEntityRepository.selectLemmaIdBySiteIdAndLemmaOrderByFreq(siteIds, lemmas);
        LemmaEntity lemmaEntity = new LemmaEntity();
        int frequency = 1;
        if (lemmaFromDB.isEmpty()) {
            lemmaEntity.setSite(pageEntity.getSite());
            lemmaEntity.setLemma(lemma);
        }
        else {
            lemmaEntity = lemmaFromDB.get(0);
            frequency = lemmaEntity.getFrequency() + 1;
        }
        lemmaEntity.setFrequency(frequency);
        return lemmaEntity;
    }

    /** получение IndexEntity по списку индексов, лемме и  странице
     * если в списке есть запись об индексе, возвращается имеющаяся запись,
     * иначе возвращается новая IndexEntity
     * @param indexFromDB {List<IndexEntity>} Принимает индекс в качестве параметра
     * @param lemmaEntity {LemmaEntity} Принимает лемму в качестве параметра
     * @param pageEntity {PageEntity} Принимает страницу из базы в качестве параметр
     * @return {IndexEntity} Возвращает IndexEntity в соответствии с параметрами
     */
    private IndexEntity getIndexEntityFromDB(List<IndexEntity> indexFromDB, LemmaEntity lemmaEntity, PageEntity pageEntity) {
        IndexEntity indexEntity = new IndexEntity();

        if (indexFromDB.isEmpty()) {
            indexEntity.setLemma(lemmaEntity);
            indexEntity.setPage(pageEntity);
        }
        else {
            indexEntity = indexFromDB.get(0);
        }
        return indexEntity;
    }

    /**  Удаление записей по странице с корректировкой данных по леммам
      * @param siteEntity {SiteEntity} Принимает сайт в качестве параметра
      * @param path {String} Принимает адрес страницы в качестве параметра
     */
    public void deletePage(SiteEntity siteEntity, String path) {
        List<PageEntity> pageEntityList = pageEntityRepository.selectPageIdBySiteIdAndPath(String.valueOf(siteEntity.getId()), path);
        if (!pageEntityList.isEmpty()) {
            List<IndexEntity> indexEntityList = indexEntityRepository.selectIndexIdByPageId(String.valueOf(pageEntityList.get(0).getId()));
            List<LemmaEntity> lemmaEntityList = new ArrayList<>();
            for (IndexEntity indexEntity : indexEntityList) {
                lemmaEntityList.add(indexEntity.getLemma());
            }
            for (LemmaEntity lemmaEntity : lemmaEntityList) {
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() - 1);
            }
            lemmaEntityRepository.deleteAllById(lemmaEntityRepository.selectByFrequency(0));
            pageEntityRepository.deleteAll(pageEntityList);
        }
    }

    /** Создание новой SiteEntity по имени сайта и url,
     * c возможностью добавления последней ошибки по сайту
     * @param name {String} Принимает имя сайта в качестве параметра
     * @param url {String} Принимает url сайта в качестве параметра
     * @param lastError {String} Принимает ошибку индексации сайта в качестве параметра
     * @return {SiteEntity} создает новый SiteEntity по заданным параметрам
     */
    public static SiteEntity createSiteEntity(String name, String url, String lastError) {
        SiteEntity site = new SiteEntity();
        site.setName(name);
        if (lastError.equals("")) {
            site.setStatus(Status.INDEXING);
        } else {
            site.setStatus(Status.FAILED);
        }
        site.setLast_error(lastError);
        site.setStatus_time(LocalDateTime.now());
        site.setUrl(url);
        return site;
    }

    /** Создание новой PageEntity по сайту и адресу страницы
     * @param siteEntity {SiteEntity} Принимает сайт в качестве параметра
     * @param path {String} Принимает путь к страницы в качестве параметра
     * @return {PageEntity} создает новую PageEntity с указаннам адресом
     */
    public static PageEntity addOnePageToDB(SiteEntity siteEntity, String path) throws IOException {
        String url = siteEntity.getUrl() + path;
        Connection.Response response = Jsoup.connect(url).followRedirects(false).execute();
        int code = response.statusCode();
        String content;
        if (code < 400) {
            content = response.body();
        } else {
            content = "";
        }
        PageEntity newPage = new PageEntity();
        newPage.setCode(code);
        newPage.setContent(content);
        newPage.setPath(path);
        newPage.setSite(siteEntity);
        return newPage;
    }

}
