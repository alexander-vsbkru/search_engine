package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.PageEntityRepository;
import searchengine.repositories.SiteEntityRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{
    private final SitesList sites;
    private boolean isIndexing = false;
    SiteEntityRepository siteEntityRepository;
    PageEntityRepository pageEntityRepository;

    @Autowired
    public IndexingServiceImpl(SitesList sites,
                               SiteEntityRepository siteEntityRepository,
                               PageEntityRepository pageEntityRepository) {
        this.sites = sites;
        this.siteEntityRepository = siteEntityRepository;
        this.pageEntityRepository = pageEntityRepository;
    }

    @Override
    public JSONObject startIndexing() {

        JSONObject response = new JSONObject();

        if (isIndexing) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return response;
        }

        isIndexing = true;

        clearSitesFromDB();
        for (Site site : sites.getSites()) {
            indexing(site);
        }

        response.put("result", true);
        isIndexing = false;

        return response;
    }

    @Override
    public JSONObject stopIndexing() {
       JSONObject response = new JSONObject();
       if (!isIndexing) {
           response.put("result", false);
           response.put("error", "Индексация не запущена");
           return response;
       }

       isIndexing = false;
       ForkJoinPool pool = ForkJoinPool.commonPool();
       pool.shutdownNow();

       List<SiteEntity> indexingSiteList = siteEntityRepository.selectSiteIdByStatus("INDEXING");
       for (SiteEntity siteEntity : indexingSiteList) {
          siteEntity.setStatus(Status.FAILED);
          siteEntity.setLast_error("Индексация остановлена пользователем");
          siteEntityRepository.save(siteEntity);
       }

       response.put("result", true);
       return response;
    }

    private SiteEntity createSiteEntity(String name, String url) {
        SiteEntity site = new SiteEntity();
        site.setName(name);
        site.setStatus(Status.INDEXING);
        site.setStatus_time(LocalDateTime.now());
        site.setUrl(url);
        return site;
    }

    private void indexing(Site site) {

        SiteEntity newSite = siteEntityRepository.save(createSiteEntity(site.getName(), site.getUrl()));

        String link = "";
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        List<String> duplicateLinks = new ArrayList<>();
        List<PageEntity> insertIntoPage = forkJoinPool.invoke(new Links(new Page(newSite, link, duplicateLinks)));//, siteEntityRepository, pageEntityRepository));

        if (isIndexing) {

            pageEntityRepository.saveAll(insertIntoPage);

            newSite.setStatus(Status.INDEXED);
            siteEntityRepository.save(newSite);
        }
    }

    private void clearSitesFromDB() {
        for (Site site : sites.getSites()) {
            for (SiteEntity siteToDelete : siteEntityRepository.selectSiteIdByUrl(site.getUrl())) {
                siteEntityRepository.delete(siteToDelete);
            }

        }
    }
}
