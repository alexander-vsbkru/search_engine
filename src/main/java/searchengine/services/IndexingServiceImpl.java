package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.index.IndexResponse;
import searchengine.dto.index.IndexResponseError;
import searchengine.dto.index.IndexResponseOk;
import searchengine.model.*;
import searchengine.repositories.*;
import searchengine.utils.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/** Сервис индексации включает методы <b>StartIndexing</b>, <b>StopIndexing</b>, <b>indexPage</b> */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {
    /** Список сайтов из application.properties */
    private final SitesList sites;
    /** Поле, определяющее запущена ли индексация сайтов */
    private boolean isIndexing = false;
    /** Репозиторий для таблицы site */
    private final SiteEntityRepository siteEntityRepository;
    /** Репозиторий для таблицы page */
    private final PageEntityRepository pageEntityRepository;
    /** Репозиторий для таблицы lemma */
    private final LemmaEntityRepository lemmaEntityRepository;
    /** Репозиторий для таблицы index */
    private final IndexEntityRepository indexEntityRepository;
    /** Список потоков для индексации сайтов */
    private List<Thread> threadSiteIndexingList = new ArrayList<>();

    /** Запуск индексации сайтов */
    @Override
    public IndexResponse startIndexing() {
        isIndexing = !threadSiteIndexingList.isEmpty();
        if (isIndexing) {
            return new IndexResponseError("Индексация уже запущена");
        }
        IndexResponseOk response = new IndexResponseOk();
        isIndexing = true;
        SitesMethods sitesMethods = new SitesMethods(sites, siteEntityRepository);
        sitesMethods.deleteSitesNotInSetting();

        for (Site site : sites.getSites()) {
            threadSiteIndexingList.add(threadIndexing(site));
        }
        for (Thread thread : threadSiteIndexingList) {
            thread.start();
        }
        return response;
    }

    /** Остановка индексации сайтов */
    @Override
    public IndexResponse stopIndexing() {
        isIndexing = !threadSiteIndexingList.isEmpty();
        if (!isIndexing) {
            return new IndexResponseError("Индексация не запущена");
        }
        IndexResponseOk response = new IndexResponseOk();
        isIndexing = false;
        for (Thread thread : threadSiteIndexingList) {
            thread.interrupt();
        }
        threadSiteIndexingList.clear();
        return response;
    }

    /** Индексация одной страницы */
    @Override
    public IndexResponse indexPage(String page) {
        if (addIndexPage(page)) {
            return new IndexResponseOk();
        } else {
            return new IndexResponseError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
    }

    /** Индексирование и добавление одной страницы
     * @param page {String} принимает в качестве параметра адрес страницы
     * @return {boolean}
     */
    private boolean addIndexPage(String page) {
        PagePath pagePath = SitesMethods.getUrlAndPath(page);
        if (pagePath == null) return false;
        SitesMethods sitesMethods = new SitesMethods(sites, siteEntityRepository);
        if (!sitesMethods.siteInSetting(pagePath.getSiteUrl())) return  false;
        SiteEntity siteEntity = sitesMethods.getSiteEntityFromDB(pagePath.getSiteUrl());
        deletePage(siteEntity, pagePath.getPath());
        PageEntity pageEntity;
        pageEntity = pageEntityRepository.save(addOnePageToDB(siteEntity, pagePath.getPath()));
        if (pageEntity.getCode() >= 400) {
            addLemmaIndex(pageEntity);
        }
      //  siteEntity.setStatus(Status.INDEXED);
        siteEntity.setLast_error(null);
        siteEntity.setStatus_time(LocalDateTime.now());
        siteEntityRepository.save(siteEntity);
        return true;
    }

    /** Добавление лемм и индексов для страницы
     * @param pageEntity {PageEntity} Принимает адрес страницы page
     */
    public void addLemmaIndex(PageEntity pageEntity) {
        GetLemmasFromText getLemmasFromText = new GetLemmasFromText();
        HashMap<String, Integer> lemmaMap = new HashMap<>();
        HashMap<List<String>, Integer> lemmaMapByWordBase = null;
        try {
            lemmaMapByWordBase = new HashMap<>(getLemmasFromText.getLemmaList(pageEntity.getContent()));
        } catch (IOException e) {
            log.error(e.toString());
        }
        if (lemmaMapByWordBase != null) {
            for (List<String> lemmaList : lemmaMapByWordBase.keySet()) {
                for (String lemma : lemmaList) {
                    lemmaMap.put(lemma, lemmaMapByWordBase.get(lemmaList));
                }
            }
        }
        for (String keyLemma : lemmaMap.keySet()) {
            if (!isIndexing) break;
            LemmaEntity lemmaEntity = getLemmaEntity(pageEntity, keyLemma);
            lemmaEntityRepository.save(lemmaEntity);
            List<String> keyLemmaList = new ArrayList<>();
            keyLemmaList.add(keyLemma);
            List<IndexEntity> indexFromDB = indexEntityRepository.selectIndexIdByPageIdAndLemmaId(
                    pageEntity.getId(), lemmaEntityRepository.selectLemmaIdByLemma(keyLemmaList));
            IndexEntity indexEntity = getIndexEntityFromDB(indexFromDB, lemmaEntity, pageEntity);
            indexEntity.setRank(lemmaMap.get(keyLemma));
            indexEntityRepository.save(indexEntity);
        }
    }

    /** Индексация по заданному сайту
     * @param site {Site} принимает в качестве параметра адрес страницы
     */
    private void indexing(Site site,  ForkJoinPool forkJoinPool) {
        siteEntityRepository.deleteAll(siteEntityRepository.selectSiteIdByUrl(site.getUrl()));
        Connection.Response response = null;
        try {
            response = Jsoup.connect(site.getUrl()).followRedirects(false).execute();
        } catch (IOException e) {
            log.error(e.toString());
        }
        int code = 0;
        if (response != null) {
            code = response.statusCode();
        }
        if (code >= 400) {
            siteEntityRepository.save(createSiteEntity(site.getName(), site.getUrl(), "Ошибка индексации: главная страница сайта не доступна"));
            return;
        }
        SiteEntity newSite = siteEntityRepository.save(createSiteEntity(site.getName(), site.getUrl(), ""));
        String link = "";
        List<String> duplicateLinks = new ArrayList<>();
        List<Page> pageList = forkJoinPool.invoke(new LinksParser(new Page(newSite, link, duplicateLinks)));
        List<PageEntity> pageEntityList = new ArrayList<>();
        List<PageEntity> insertIntoPage = new ArrayList<>();
        for (Page page : pageList) {
            if (!isIndexing) break;
            insertIntoPage.add(addOnePageToDB(page.getSiteParent(), page.getLink()));
        }
        if (isIndexing) {
            pageEntityList = pageEntityRepository.saveAll(insertIntoPage);
        }
        for (PageEntity pageEntity : pageEntityList) {
            if (!isIndexing) break;
            addLemmaIndex(pageEntity);
            newSite.setStatus_time(LocalDateTime.now());
            siteEntityRepository.save(newSite);
        }
        if (isIndexing) {
            newSite.setStatus(Status.INDEXED);
            newSite.setLast_error(null);
        }
        else {
            newSite.setStatus(Status.FAILED);
            newSite.setLast_error("Индексация остановлена пользователем");
        }
        newSite.setStatus_time(LocalDateTime.now());
        siteEntityRepository.save(newSite);
    }

    private Thread threadIndexing(Site site) {
        return new Thread() {
            final ForkJoinPool forkJoinPool = new ForkJoinPool();
            @Override
            public void run() {
                indexing(site, forkJoinPool);
            }

            @Override
            public void interrupt() {
                forkJoinPool.shutdown();
            }
        };
    }
    /** Получение записи LemmaEntity по странице и лемме
     * @param pageEntity {PageEntity} Принимает страницу из базы в качестве параметра
     * @param lemma {String} Принимает лемму в качестве параметра
     * @return {LemmaEntity} Возвращает LemmaEntity в соответствии с параметрами
     */
    public LemmaEntity getLemmaEntity(PageEntity pageEntity, String lemma) {
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
    public IndexEntity getIndexEntityFromDB(List<IndexEntity> indexFromDB, LemmaEntity lemmaEntity, PageEntity pageEntity) {
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
        if (lastError.isBlank()) {
            site.setStatus(Status.INDEXING);
            site.setLast_error(null);
        } else {
            site.setStatus(Status.FAILED);
            site.setLast_error(lastError);
        }

        site.setStatus_time(LocalDateTime.now());
        site.setUrl(url);
        return site;
    }

    /** Создание новой PageEntity по сайту и адресу страницы
     * @param siteEntity {SiteEntity} Принимает сайт в качестве параметра
     * @param path {String} Принимает путь к страницы в качестве параметра
     * @return {PageEntity} создает новую PageEntity с указаннам адресом
     */
    public static PageEntity addOnePageToDB(SiteEntity siteEntity, String path) {
        String url = siteEntity.getUrl() + path;
        Connection.Response response = null;
        try {
            response = Jsoup.connect(url).followRedirects(false).execute();
        } catch (IOException e) {
            log.error(e.toString());
        }
        int code = 0;
        if (response != null) {
            code = response.statusCode();
        }
        String content = "";
        if (code < 400) {
            if (response != null) {
                content = response.body();
            }
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