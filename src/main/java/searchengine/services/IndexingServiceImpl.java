package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.index.IndexResponse;
import searchengine.dto.index.IndexResponseError;
import searchengine.dto.index.IndexResponseOk;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.IndexEntityRepository;
import searchengine.repositories.LemmaEntityRepository;
import searchengine.repositories.PageEntityRepository;
import searchengine.repositories.SiteEntityRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        if (!isIndexing) {
            return new IndexResponseError("Индексация не запущена");
        }
        IndexResponseOk response = new IndexResponseOk();
        isIndexing = false;
        for (Thread thread : threadSiteIndexingList) {
            thread.interrupt();
        }
        threadSiteIndexingList.clear();
        ForkJoinPool pool = ForkJoinPool.commonPool();
        pool.shutdownNow();
        List<SiteEntity> indexingSiteList = siteEntityRepository.selectSiteIdByStatus("INDEXING");
        for (SiteEntity siteEntity : indexingSiteList) {
            siteEntity.setStatus(Status.FAILED);
            siteEntity.setLast_error("Индексация остановлена пользователем");
            siteEntityRepository.save(siteEntity);
        }
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
            repositoryMethods.addLemmaIndex(pageEntity);
        }
        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setLast_error("null");
        siteEntity.setStatus_time(LocalDateTime.now());
        siteEntityRepository.save(siteEntity);
        return true;
    }

    /** Индексация по заданному сайту
     * @param site {Site} принимает в качестве параметра адрес страницы
     */
    @SneakyThrows
    private void indexing(Site site) {
        siteEntityRepository.deleteAll(siteEntityRepository.selectSiteIdByUrl(site.getUrl()));
        Connection.Response response = Jsoup.connect(site.getUrl()).followRedirects(false).execute();
        int code = response.statusCode();
        String lastError = "";
        if (code >= 400) {
            lastError = "Ошибка индексации: главная страница сайта не доступна";
        }
        SiteEntity newSite = siteEntityRepository.save(RepositoryMethods.createSiteEntity(site.getName(), site.getUrl(), lastError));
        if (code >= 400) return;
        String link = "";
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        List<String> duplicateLinks = new ArrayList<>();
        List<PageEntity> insertIntoPage = forkJoinPool.invoke(new LinksParser(new Page(newSite, link, duplicateLinks)));
        if (isIndexing) {
            List<PageEntity> pageEntityList = pageEntityRepository.saveAll(insertIntoPage);
            for (PageEntity pageEntity : pageEntityList) {
                repositoryMethods.addLemmaIndex(pageEntity);
                newSite.setStatus_time(LocalDateTime.now());
                siteEntityRepository.save(newSite);
                    }
            newSite.setStatus(Status.INDEXED);
            newSite.setStatus_time(LocalDateTime.now());
            newSite.setLast_error(null);
            siteEntityRepository.save(newSite);
        }
    }

    private Thread threadIndexing(Site site) {
        return new Thread(() -> {
            while (!Thread.interrupted()) {
                indexing(site);
            }
        });

    }
}

