package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaList;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexEntityRepository;
import searchengine.repositories.LemmaEntityRepository;
import searchengine.repositories.PageEntityRepository;
import searchengine.repositories.SiteEntityRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private boolean isIndexing = false;
    SiteEntityRepository siteEntityRepository;
    PageEntityRepository pageEntityRepository;
    LemmaEntityRepository lemmaEntityRepository;
    IndexEntityRepository indexEntityRepository;

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
    }

    @Override
    public JSONObject startIndexing() throws IOException {

        JSONObject response = new JSONObject();

        if (isIndexing) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return response;
        }

        isIndexing = true;
        deleteSitesNotInSetting();

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

    @Override
    public JSONObject indexPage(String page) throws IOException {

        JSONObject response = new JSONObject();

        if (indexPageAdd(page)) {
            response.put("result", true);
        } else {
            response.put("result", false);
            response.put("error", "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            return response;
        }
        return response;
    }

    @Override
    public JSONObject search(String query, String site, int offset, int limit) {
        JSONObject response = new JSONObject();
        try {
            List<SiteEntity> siteEntityList = new ArrayList<>();
            if (site.isBlank()) {
                siteEntityList.addAll(siteEntityRepository.findAll());
            } else {
                siteEntityList.addAll(siteEntityRepository.selectSiteIdByUrl(site));
            }

            if (query.isBlank()) {
                response.put("result", false);
                response.put("error", "Задан пустой поисковый запрос");
                return response;
            }

            if (siteEntityList.isEmpty()) {
                response.put("result", false);
                response.put("error", "Совпадений не найдено");
                return response;
            }

            LemmaList lemmaList = new LemmaList();
            Map<String, Integer> lemmaRequestMap = new HashMap<>(lemmaList.getLemmaList(query));
            List<LemmaEntity> lemmaEntityList = getLemmaListFromDB(lemmaRequestMap, siteEntityList);

            TreeSet<Map.Entry<PageEntity, Float>> pageEntityMap = getPageMap(lemmaEntityList);
            int count = pageEntityMap.size();
            response.put("result", true);
            response.put("count", count);

            JSONArray data = new JSONArray();
            for (Map.Entry<PageEntity, Float> page : pageEntityMap) {
                JSONObject dataAdd = new JSONObject();
                dataAdd.put("site", page.getKey().getSite().getUrl());
                dataAdd.put("siteName", page.getKey().getSite().getName());
                dataAdd.put("url", page.getKey().getPath());
                dataAdd.put("title", getTitle(page.getKey().getContent()));
                dataAdd.put("snippet", getSnippet(page.getKey().getContent(), lemmaRequestMap));
                dataAdd.put("relevance", page.getValue());

                data.add(dataAdd);
            }
            response.put("data", data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    private String getSnippet(String text, Map<String, Integer> getLemmaMap) throws IOException {
        LemmaList lemmaList = new LemmaList();
        String resultWithoutTags = lemmaList.deleteTags(text);
        List<String> textInWordList = new ArrayList<>(List.of(resultWithoutTags.split("\\s+")));
        Map<String, List<Integer>> lemmaIndexMap = new HashMap<>();

        for (int i = 0; i < textInWordList.size(); i++) {
            Pattern pattern = Pattern.compile("[А-яЁё]+");
            Matcher matcher = pattern.matcher(textInWordList.get(i));

            if (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                LuceneMorphology luceneMorph = new RussianLuceneMorphology();
                List<String> wordBase = luceneMorph.getNormalForms(textInWordList.get(i).substring(start, end).toLowerCase(Locale.ROOT));

                for (String word : wordBase) {
                    List<Integer> indexList = new ArrayList<>();
                    if (lemmaIndexMap.containsKey(word)) {
                        indexList.addAll(lemmaIndexMap.get(word));
                    }
                    indexList.add(i);
                    lemmaIndexMap.put(word, indexList);
                }
            }
        }

        String response = "";
        List<Integer> responseIndexes = getFindIndexText(lemmaIndexMap, getLemmaMap);

        if (!responseIndexes.isEmpty()) {
            int startString = Collections.min(responseIndexes) - 5;
            int endString = Collections.max(responseIndexes) + 5;
            for (int i = startString; i <= endString; i++) {
                if (i < 0) {
                    continue;
                }
                if (response.length() > 0) {
                    response = response.concat(" ");
                }
                if (responseIndexes.contains(i)) {
                    response = response.concat("<b>");
                }

                Pattern pattern = Pattern.compile("[-().,А-яЁё]+");
                Matcher matcher = pattern.matcher(textInWordList.get(i));

                if (matcher.find()) {
                    response = response.concat(textInWordList.get(i));
                }

                if (responseIndexes.contains(i)) {
                    response = response.concat("</" +
                            "b>");
                }

            }

        }

       return response;
}

    private static List<Integer> getFindIndexText(Map<String, List<Integer>> textLemmaMap, Map<String, Integer> requestLemmaMap) {
        List<List<Integer>> indexList = new ArrayList<>();
        for (String lemma : requestLemmaMap.keySet()) {
            if (textLemmaMap.containsKey(lemma)) {
                indexList.add(new ArrayList<>(textLemmaMap.get(lemma)));
            } else {
                return new ArrayList<>();
            }
        }

        List<Integer> resultIndexList = new ArrayList<>();

        for (int index : indexList.get(0)) {
            List<Integer> findIndexList = new ArrayList<>();
            findIndexList.add(index);
            if (indexList.size() == 1) {
                return findIndexList;
            }
            for (int i = 1; i < indexList.size(); i++) {
                int min = 1000;
                for (int j : indexList.get(i)) {
                    if (Math.abs(min) > Math.abs(j - index))
                        {
                            min = j - index;
                        }
                }
                findIndexList.add(min + index);
            }
            if (resultIndexList.isEmpty()) {
                resultIndexList.addAll(findIndexList);
            }

            int distanceBetweenWordsResult = Collections.max(resultIndexList) - Collections.min(resultIndexList);
            int distanceBetweenWordsFind = Collections.max(findIndexList) - Collections.min(findIndexList);
            if (distanceBetweenWordsResult > distanceBetweenWordsFind) {
                resultIndexList.clear();
                resultIndexList.addAll(findIndexList);
            }
        }
        return resultIndexList;
    }


    private TreeSet<Map.Entry<PageEntity, Float>> getPageMap(List<LemmaEntity> lemmaList) throws IOException {
        Map<PageEntity, Float> pageEntityMap = new HashMap<>();
        String lemma = lemmaList.get(0).getLemma();
        List<PageEntity> pageEntityList = new ArrayList<>();
        List<IndexEntity> indexEntityList = indexEntityRepository.selectIndexIdByLemmaId(lemmaIds(lemma));
        for (IndexEntity indexEntity : indexEntityList) {
            if (!pageEntityList.contains(indexEntity.getPage())) {
                pageEntityList.add(indexEntity.getPage());
            }
        }

        List<PageEntity> pagesToDelete = new ArrayList<>();
        if (lemmaList.size() > 1) {
            for (int i = 1; i < lemmaList.size(); i++) {
                for (PageEntity pageEntity : pageEntityList) {
                    if (indexEntityRepository.selectIndexIdByPageIdAndLemmaId(pageEntity.getId(),
                            lemmaIds(lemmaList.get(i).getLemma())).isEmpty()) {
                        pagesToDelete.add(pageEntity);
                    }
                }
            }
        }

        pageEntityList.removeAll(pagesToDelete);

        for (PageEntity pageEntity : pageEntityList) {
            float rank = 0;
            for (LemmaEntity lemmaEntity : lemmaList) {
                List<IndexEntity> indexList = indexEntityRepository.selectIndexIdByPageIdAndLemmaId(pageEntity.getId(),
                        lemmaIds(lemmaEntity.getLemma()));
                rank = rank + indexList.get(0).getRank();
            }
            pageEntityMap.put(pageEntity, rank);
        }
        float max = Collections.max(pageEntityMap.values());
        TreeSet<Map.Entry<PageEntity, Float>> treeSet = new TreeSet<>(Comparator.comparingDouble(Map.Entry<PageEntity, Float>::getValue).reversed());
        treeSet.addAll(pageEntityMap.entrySet());
        for (Map.Entry<PageEntity, Float> page : treeSet) {
            page.setValue(page.getValue()/max);
        }
        return treeSet;
    }

    private List<Integer> lemmaIds (String lemma) {
        return lemmaEntityRepository.selectLemmaIdByLemma(lemma);
    }
    private List<LemmaEntity> getLemmaListFromDB(Map<String, Integer> lemmaList, List<SiteEntity> siteEntityList) {
        List<Integer> sites = new ArrayList<>();

        List<String> lemmas = new ArrayList<>(lemmaList.keySet());
        for (SiteEntity siteEntity : siteEntityList) {
            sites.add(siteEntity.getId());
        }

        return lemmaEntityRepository.selectLemmaIdBySiteIdAndLemma(sites, lemmas);
    }
    private String getTitle(String html) {
        String title = "";
        String regex = "<title>[^>]+</title>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            title = html.substring(matcher.start() + 7, matcher.end() - 8);
        }
        return title;
    }

    private boolean indexPageAdd(String page) throws IOException {
        String regex = "http(s)?`aw://[A-z.:-_]+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(page);
        String siteUrl;
        String path;
        if (matcher.find())
        {
            siteUrl = page.substring(matcher.start(), matcher.end());
            path = page.substring(matcher.end() + 1);
        }
        else {
            return false;
        }

        SiteEntity siteEntity = siteInConfig(siteUrl);
        if (siteEntity.getUrl() == null) {
            return false;
        }
        else
        {
            deleteLemmaIndex(siteEntity, path);
            PageEntity pageEntity = pageEntityRepository.save(addOnePage(siteEntity, path));

            if (pageEntity.getCode() >= 400)
            {
                lemmaIndexAdd(pageEntity);
            }
            siteEntity.setStatus(Status.INDEXED);
            siteEntity.setLast_error("null");
            siteEntity.setStatus_time(LocalDateTime.now());
            siteEntityRepository.save(siteEntity);
            return true;
        }
    }

    private SiteEntity siteInConfig(String url) {

        SiteEntity siteEntity = new SiteEntity();

        for (Site site : sites.getSites()) {
            if (site.getUrl().equals(url)) {
                List<SiteEntity> siteEntityList = new ArrayList<>(siteEntityRepository.selectSiteIdByUrl(url));
                if (siteEntityList.isEmpty()) {
                    siteEntity.setUrl(url);
                    siteEntity.setStatus_time(LocalDateTime.now());
                    siteEntity.setStatus(Status.INDEXING);
                    siteEntity.setName(site.getName());
                    siteEntity = siteEntityRepository.save(siteEntity);
                } else {
                    siteEntity = siteEntityList.get(0);
                }
            }
        }
        return siteEntity;
    }

    private void lemmaIndexAdd(PageEntity pageEntity) throws IOException {
        LemmaList lemmaList = new LemmaList();
        List<Integer> sites = new ArrayList<>();
        sites.add(pageEntity.getSite().getId());
        HashMap<String, Integer> lemmaMap = new HashMap<>(lemmaList.getLemmaList(pageEntity.getContent()));
        for (String key : lemmaMap.keySet()) {
            int frequency = 1;
            LemmaEntity lemmaEntity = new LemmaEntity();
            List<String> lemmas = new ArrayList<>();
            lemmas.add(key);
            List<LemmaEntity> lemmaFromDB = lemmaEntityRepository.selectLemmaIdBySiteIdAndLemma(sites, lemmas);
            if (lemmaFromDB.isEmpty()) {
                lemmaEntity.setSite(pageEntity.getSite());
                lemmaEntity.setLemma(key);
            }
            else {
                lemmaEntity = lemmaFromDB.get(0);
                frequency = lemmaEntity.getFrequency() + 1;
            }
            lemmaEntity.setFrequency(frequency);
            String lemma = lemmaEntityRepository.save(lemmaEntity).getLemma();

            IndexEntity indexEntity = new IndexEntity();
            List<IndexEntity> indexFromDB = indexEntityRepository.selectIndexIdByPageIdAndLemmaId(
                    pageEntity.getId(), lemmaIds(lemma));
            if (indexFromDB.isEmpty())
            {
               indexEntity.setLemma(lemmaEntity);
               indexEntity.setPage(pageEntity);
            }
            else
            {
                indexEntity = indexFromDB.get(0);
            }
            indexEntity.setRank(lemmaMap.get(key));
            indexEntityRepository.save(indexEntity);
        }

    }

    private void deleteLemmaIndex(SiteEntity siteEntity, String path) {
        List<PageEntity> pageEntityList = pageEntityRepository.selectPageIdBySiteIdAndPath(String.valueOf(siteEntity.getId()), path);
        if (!pageEntityList.isEmpty()) {
            List<IndexEntity> indexEntityList = indexEntityRepository.selectIndexIdByPageId(String.valueOf(pageEntityList.get(0).getId()));
            List<LemmaEntity> lemmaEntityList = new ArrayList<>();
            for (IndexEntity indexEntity : indexEntityList) {
                lemmaEntityList.add(indexEntity.getLemma());
            }
            for (LemmaEntity lemmaEntity : lemmaEntityList)
            {
               lemmaEntity.setFrequency(lemmaEntity.getFrequency() - 1);
            }

            lemmaEntityRepository.deleteByFrequency(0);
            pageEntityRepository.deleteAll(pageEntityList);
        }
    }

    private SiteEntity createSiteEntity(String name, String url, String lastError) {
        SiteEntity site = new SiteEntity();
        site.setName(name);
        if (lastError.equals("")) {
        site.setStatus(Status.INDEXING);}
        else {
            site.setStatus(Status.FAILED);
        }
        site.setLast_error(lastError);
        site.setStatus_time(LocalDateTime.now());
        site.setUrl(url);
        return site;
    }

    private void indexing(Site site) throws IOException {

        siteEntityRepository.deleteAll(siteEntityRepository.selectSiteIdByUrl(site.getUrl()));
        Connection.Response response = Jsoup.connect(site.getUrl()).followRedirects(false).execute();
        int code = response.statusCode();
        String lastError = "";

        if (code >= 400) {
            lastError = "Ошибка индексации: главная страница сайта не доступна";
        }
        SiteEntity newSite = siteEntityRepository.save(createSiteEntity(site.getName(), site.getUrl(), lastError));

        if (code >= 400) {

            return;
        }

        String link = "";
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        List<String> duplicateLinks = new ArrayList<>();
        List<PageEntity> insertIntoPage = forkJoinPool.invoke(new Links(new Page(newSite, link, duplicateLinks)));//, siteEntityRepository, pageEntityRepository)

        if (isIndexing) {
            List<PageEntity> pageEntityList = pageEntityRepository.saveAll(insertIntoPage);
            for (PageEntity pageEntity : pageEntityList) {
                lemmaIndexAdd(pageEntity);
                newSite.setStatus_time(LocalDateTime.now());
                siteEntityRepository.save(newSite);

            }
            newSite.setStatus(Status.INDEXED);
            newSite.setStatus_time(LocalDateTime.now());
            newSite.setLast_error(null);
            siteEntityRepository.save(newSite);
        }
    }

    public static PageEntity addOnePage(SiteEntity siteEntity, String path) throws IOException {

        String url = siteEntity.getUrl() + path;
        Connection.Response response = Jsoup.connect(url).followRedirects(false).execute();
        int code = response.statusCode();
        String content;
        if (code < 400) {
            content = response.body();
        }
        else {
            content = "";
        }
        PageEntity newPage = new PageEntity();
        newPage.setCode(code);
        newPage.setContent(content);
        newPage.setPath(path);
        newPage.setSite(siteEntity);

        return newPage;
    }

    private void deleteSitesNotInSetting() {
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
 }

