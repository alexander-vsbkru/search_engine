package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.index.IndexResponse;
import searchengine.dto.index.IndexResponseError;
import searchengine.dto.index.IndexResponseOk;
import searchengine.model.*;
import searchengine.repositories.IndexEntityRepository;
import searchengine.repositories.LemmaEntityRepository;
import searchengine.repositories.PageEntityRepository;
import searchengine.repositories.SiteEntityRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/** Сервис индексации включает методы <b>StartIndexing</b>, <b>StopIndexing</b>, <b>indexPage</b> */
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    /** Список сайтов из application.properties */
    private final SitesList sites;
    /** Поле, определяющее запущена ли индексация сайтов */
    private boolean isIndexing = false;
    /** Репозиторий для таблицы site */
    SiteEntityRepository siteEntityRepository;
    /** Репозиторий для таблицы page */
    PageEntityRepository pageEntityRepository;
    /** Репозиторий для таблицы lemma */
    LemmaEntityRepository lemmaEntityRepository;
    /** Репозиторий для таблицы index */
    IndexEntityRepository indexEntityRepository;
    /** Класс методов для работы с репозиториями */
    RepositoryMethods repositoryMethods;
    List<Thread> threadSiteIndexingList = new ArrayList<>();

    /** Конструтор - создание нового объекта */
    @Autowired
    public IndexingServiceImpl(SitesList sites,
                               SiteEntityRepository siteEntityRepository,
                               PageEntityRepository pageEntityRepository,
                               LemmaEntityRepository lemmaEntityRepository,
                               IndexEntityRepository indexEntityRepository) {
        this.sites = sites;
        this.siteEntityRepository = siteEntityRepository;
        this.pageEntityRepository = pageEntityRepository;
        this.lemmaEntityRepository = lemmaEntityRepository;
        this.indexEntityRepository = indexEntityRepository;
        this.repositoryMethods = new RepositoryMethods(lemmaEntityRepository, indexEntityRepository,pageEntityRepository);
    }

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
    public IndexResponse indexPage(String page) throws IOException {
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
    private boolean addIndexPage(String page) throws IOException {
        PagePath pagePath = SitesMethods.getUrlAndPath(page);
        if (pagePath == null) return false;
        SitesMethods sitesMethods = new SitesMethods(sites, siteEntityRepository);
        if (!sitesMethods.siteInSetting(pagePath.getSiteUrl())) return  false;
        SiteEntity siteEntity = sitesMethods.getSiteEntityFromDB(pagePath.getSiteUrl());
        repositoryMethods.deletePage(siteEntity, pagePath.getPath());
        PageEntity pageEntity = pageEntityRepository.save(RepositoryMethods.addOnePageToDB(siteEntity, pagePath.getPath()));
        if (pageEntity.getCode() >= 400) {
            addLemmaIndex(pageEntity);
        }
        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setLast_error(null);
        siteEntity.setStatus_time(LocalDateTime.now());
        siteEntityRepository.save(siteEntity);
        return true;
    }

    /** Добавление лемм и индексов для страницы
     * @param pageEntity {PageEntity} Принимает адрес страницы page
     */
    public void addLemmaIndex(PageEntity pageEntity) throws IOException {
        GetLemmasFromText getLemmasFromText = new GetLemmasFromText();
        HashMap<String, Integer> lemmaMap = new HashMap<>();
        HashMap<List<String>, Integer> lemmaMapByWordBase = new HashMap<>(getLemmasFromText.getLemmaList(pageEntity.getContent()));
        for (List<String> lemmaList : lemmaMapByWordBase.keySet()) {
            for (String lemma : lemmaList) {
                lemmaMap.put(lemma, lemmaMapByWordBase.get(lemmaList));
            }
        }
        for (String keyLemma : lemmaMap.keySet()) {
            if (!isIndexing) break;
            LemmaEntity lemmaEntity = repositoryMethods.getLemmaEntity(pageEntity, keyLemma);
            lemmaEntityRepository.save(lemmaEntity);
            List<String> keyLemmaList = new ArrayList<>();
            keyLemmaList.add(keyLemma);
            List<IndexEntity> indexFromDB = indexEntityRepository.selectIndexIdByPageIdAndLemmaId(
                    pageEntity.getId(), lemmaEntityRepository.selectLemmaIdByLemma(keyLemmaList));
            IndexEntity indexEntity = repositoryMethods.getIndexEntityFromDB(indexFromDB, lemmaEntity, pageEntity);
            indexEntity.setRank(lemmaMap.get(keyLemma));
            indexEntityRepository.save(indexEntity);
        }
    }

    /** Индексация по заданному сайту
     * @param site {Site} принимает в качестве параметра адрес страницы
     */
    private void indexing(Site site,  ForkJoinPool forkJoinPool) throws IOException {
        siteEntityRepository.deleteAll(siteEntityRepository.selectSiteIdByUrl(site.getUrl()));
        Connection.Response response = Jsoup.connect(site.getUrl()).followRedirects(false).execute();
        int code = response.statusCode();
        if (code >= 400) {
            siteEntityRepository.save(RepositoryMethods.createSiteEntity(site.getName(), site.getUrl(), "Ошибка индексации: главная страница сайта не доступна"));
            return;
        }
        SiteEntity newSite = siteEntityRepository.save(RepositoryMethods.createSiteEntity(site.getName(), site.getUrl(), "null"));
        String link = "";
        List<String> duplicateLinks = new ArrayList<>();
        List<PageEntity> insertIntoPage = forkJoinPool.invoke(new LinksParser(new Page(newSite, link, duplicateLinks)));
        List<PageEntity> pageEntityList = new ArrayList<>();
        if (isIndexing) pageEntityList = pageEntityRepository.saveAll(insertIntoPage);
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
                try {
                    indexing(site, forkJoinPool);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void interrupt() {
                forkJoinPool.shutdown();
            }
        };
    }
}