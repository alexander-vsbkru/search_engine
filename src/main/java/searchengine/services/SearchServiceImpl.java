package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResponseError;
import searchengine.dto.search.SearchResponseOk;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexEntityRepository;
import searchengine.repositories.LemmaEntityRepository;
import searchengine.repositories.SiteEntityRepository;
import searchengine.utils.GetLemmasFromText;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Сервис поиска */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
    private final SiteEntityRepository siteEntityRepository;
    private final LemmaEntityRepository lemmaEntityRepository;
    private final IndexEntityRepository indexEntityRepository;

    /** Осуществление поиска по данным запроса
     * @param query {string} Принимает запрос в виде строки
     * @param site {string} Принимает параметр сайт, по которому необходимо произвести поиск, если
     *                     не указан, поиск ведется по всем сайтам
     * @param offset {int} Принимает параметр для постраничного вывода (по умолчанию равен 0)
     * @param limit {int} Принимает параметр количества необходимых для вывода результатов (по умолчанию 20)
     * @return {SearchResponse} Возвращает новый SearchResponse в соответствии с  полученным запросом
     */
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        SearchResponseOk response = new SearchResponseOk();
        List<SearchData> data = new ArrayList<>();
        if (query.isBlank()) {
           return new SearchResponseError("Задан пустой поисковый запрос");
        }
        try {
            List<SiteEntity> siteEntityList = new ArrayList<>();
            if (site.isBlank()) {
                siteEntityList.addAll(siteEntityRepository.findAll());
            } else {
                siteEntityList.addAll(siteEntityRepository.selectSiteIdByUrl(site));
            }
            GetLemmasFromText getLemmasFromText = new GetLemmasFromText();
            Map<List<String>, Integer> lemmaRequestMap = new HashMap<>(getLemmasFromText.getLemmaList(query));
            List<List<LemmaEntity>> lemmaEntityList = getLemmaListFromDB(lemmaRequestMap, siteEntityList);
            if (lemmaEntityList.isEmpty()) {
               response.setCount(0);
               response.setData(data);
               return response;
            }
            Map<PageEntity, Float> pageEntityMap = getSortPageMap(lemmaEntityList);
            response.setCount(pageEntityMap.size());
            Map<PageEntity, Float> subSetPageMap = getSubSet(pageEntityMap, offset, limit);
            response.setData(getDataForResponse(subSetPageMap, lemmaRequestMap));
        } catch (IOException e) {
            log.error(e.toString());
        }
        return response;
    }

    /** Возвращение фрагмента данной TreeMap в заданном диапазоне
     * @param pageEntityMap {TreeSet<Map.Entry<PageEntity, Float>>} Принимает параметр TreeMap
     * @param offset {int} Принимает параметр для постраничного вывода (по умолчанию равен 0)
     * @param limit {int} Принимает параметр количества необходимых для вывода результатов (по умолчанию 20)
     * @return {TreeSet<Map.Entry<PageEntity, Float>>} Возвращает TreeSet<Map.Entry<PageEntity, Float>>
     *     в соответствии с  полученным диапазоном
     */
    private Map<PageEntity, Float> getSubSet(Map<PageEntity, Float> pageEntityMap,
                                                        int offset, int limit) {
        if (limit == 0) limit = 20;
        if (pageEntityMap.size() < offset) {
            offset = 0;
        }
        TreeMap<PageEntity, Float> treeMap = new TreeMap<>();
        int i = 0;
        for (PageEntity key : pageEntityMap.keySet()) {
            if ((i >= offset)&&(i < offset + limit)) {
                treeMap.put(key, pageEntityMap.get(key));
            }
            i++;
            if (i == offset + limit) break;
        }
        return sortByValues(treeMap);
    }

    /** Получение данных о найденных страницах data для ответа
     * @param pageEntityMap {TreeSet<Map.Entry<PageEntity, Float>>} pageEntityMap Принимает параметр отсортированную TreeMap
     * с найденными страницами по результату поиска
     * @param lemmaRequestMap {Map<String, Integer>} Принимает параметр Map лемм,полученных из запроса
     * @return {List<SearchData>} Возвращает List<SearchData> в соответствии с  полученным запросом
     */
    private List<SearchData> getDataForResponse(Map<PageEntity, Float> pageEntityMap,
                                                Map<List<String>, Integer> lemmaRequestMap) throws IOException {
        List<SearchData> data = new ArrayList<>();
        for (PageEntity page : pageEntityMap.keySet()) {
            SearchData searchData = new SearchData();
            searchData.setSite(page.getSite().getUrl());
            searchData.setSiteName(page.getSite().getName());
            searchData.setUri(page.getPath());
            searchData.setTitle(getTitle(page.getContent()));
            searchData.setSnippet(getSnippet(page.getContent(), lemmaRequestMap));
            searchData.setRelevance(pageEntityMap.get(page));
            data.add(searchData);
        }
        return data;
    }

    /** Получение списка LemmaEntity из базы по леммам и сайтам
     * @param lemmaList {Map<String, Integer>} Принимает параметр Map список лемм
     * @param siteEntityList {List<SiteEntity>} Принимает параметр List список сайтов
     * @return {List<LemmaEntity>} Возвращает List<LemmaEntity> список записей лемм из базы
     */
    private List<List<LemmaEntity>> getLemmaListFromDB(Map<List<String>, Integer> lemmaList, List<SiteEntity> siteEntityList) {
        List<Integer> sites = new ArrayList<>();
        List<List<String>> lemmas = new ArrayList<>(lemmaList.keySet());
        List<List<LemmaEntity>> response = new ArrayList<>();

        for (SiteEntity siteEntity : siteEntityList) {
            sites.add(siteEntity.getId());
        }

        for (List<String> lemma : lemmas){
            response.add(lemmaEntityRepository.selectLemmaIdBySiteIdAndLemmaOrderByFreq(sites, lemma));
        }

        return response;
    }

    /** Получение отсортированной Map с результатами поиска страниц
     * @param lemmaList {List<LemmaEntity>} Принимает параметр список лем
     * @return {TreeSet<Map.Entry<PageEntity, Float>>} Возвращает TreeSet<Map.Entry<PageEntity, Float>>
          список страниц
     */
    private Map<PageEntity, Float> getSortPageMap(List<List<LemmaEntity>> lemmaList) {
        Map<PageEntity, Float> pageEntityMap = new HashMap<>();
        List<Integer> lemmaIds = getLemmasFromLemmaEntityList(lemmaList.get(0));
        List<PageEntity> pageEntityList = new ArrayList<>();
        List<IndexEntity> indexEntityList = indexEntityRepository.selectIndexIdByLemmaId(lemmaIds);
        for (IndexEntity indexEntity : indexEntityList) {
            if (!pageEntityList.contains(indexEntity.getPage())) {
                pageEntityList.add(indexEntity.getPage());
            }
        }
        for (PageEntity pageEntity : pageEntityList) {
            float rank = 0;
            for (List<LemmaEntity> lemmaEntityList : lemmaList) {
                List<IndexEntity> indexList = indexEntityRepository.selectIndexIdByPageIdAndLemmaId(pageEntity.getId(),
                        getLemmasFromLemmaEntityList(lemmaEntityList));
                rank = rank + indexList.get(0).getRank();
            }
            pageEntityMap.put(pageEntity, rank);
        }
        float max = Collections.max(pageEntityMap.values());
        pageEntityMap.replaceAll((p, v) -> pageEntityMap.get(p) / max);
        return sortByValues(pageEntityMap);
    }

    public static Map<PageEntity, Float> sortByValues(Map<PageEntity, Float> map) {
        List<Map.Entry<PageEntity, Float>> entryList = new LinkedList<>(map.entrySet());
        entryList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        Map<PageEntity, Float> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<PageEntity, Float> entry : entryList) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    /** Получение заголовка страницы
     * @param lemmaEntityList {List<LemmaEntity>}  получает параметр список LemmaEntity
     * @return {List<String>} возвращает список лемм
     */
    private List<Integer> getLemmasFromLemmaEntityList(List<LemmaEntity> lemmaEntityList) {
        List<Integer> lemmas = new ArrayList<>();
        for (LemmaEntity lemmaEntity : lemmaEntityList) {
            lemmas.add(lemmaEntity.getId());
        }
        return lemmas;
    }

    /** Получение заголовка страницы
     * @param html {String}  получает параметр html страницы
     * @return {String} возвращает title
     */
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

    /** Получение сниппета
     * @param text {String}  получает параметр текст страницы
     * @param getLemmaMap {Map<String, Integer>} получает параметр список лемм
     * @return {String} возвращает сниппет
     */
    private String getSnippet(String text, Map<List<String>, Integer> getLemmaMap) throws IOException {
        GetLemmasFromText getLemmasFromText = new GetLemmasFromText();
        String resultWithoutTags = getLemmasFromText.deleteTags(text);
        List<String> textInWordList = new ArrayList<>(List.of(resultWithoutTags.split("\\s+")));
        Map<List<String>, List<Integer>> lemmaIndexMap = new HashMap<>();
        for (int i = 0; i < textInWordList.size(); i++) {
            Pattern pattern = Pattern.compile("[А-яЁё]+");
            Matcher matcher = pattern.matcher(textInWordList.get(i));
            while (matcher.find()) {
                List<String> wordBase = getLemmasFromText.getWordBase(matcher, textInWordList.get(i));
                lemmaIndexMap.putAll(getWordIndexMap(wordBase, lemmaIndexMap, i));
            }
        }
        String response = "";
        List<Integer> responseIndexes = getFindIndexWordsForSnippet(lemmaIndexMap, getLemmaMap);

        if (!responseIndexes.isEmpty()) {
           response = getResponseSnippet(responseIndexes, textInWordList, 5);
        }
        return response;
    }

    /** Формирование спиннета для ответа
     * @param responseIndexes {List<Integer}  получает параметр список ииндексов
     * @param textInWordList {List<String>} получает параметр список слов из текста
     * @param delta {int} получает параметр диапазон текста для сниппета
     * @return {String} возвращает сниппет
     */
    private String getResponseSnippet(List<Integer> responseIndexes, List<String> textInWordList, int delta) {
        String response ="";
        int startString = Collections.min(responseIndexes) - delta;
        int endString = Collections.max(responseIndexes) + delta;
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
                response = response.concat("</" + "b>");
            }
        }
        return response;
    }

    /** Получение Map с порядковыми номерами слов в тексте для сниппета
     * @param wordBase {List<String}  получает параметр базовые формы слов
     * @param lemmaIndexList {Map<String, List<Integer>>} получает параметр список индексов лемм
     * @param index {int} получает параметр индекс слова в тексте
     * @return {Map<String, List<Integer>} возвращает Map слов с их порядковыми номерами в тексте
     */
    private Map<List<String>, List<Integer>> getWordIndexMap(List<String> wordBase, Map<List<String>, List<Integer>> lemmaIndexList, int index) {
        List<Integer> indexList = new ArrayList<>();
        if (lemmaIndexList.containsKey(wordBase)) {
            indexList.addAll(lemmaIndexList.get(wordBase));
        }
            indexList.add(index);
        lemmaIndexList.put(wordBase, indexList);
        return lemmaIndexList;
    }

    /** Поиск порядковых номеров наиболее близкорасположенных слов для сниппета
     * @param textLemmaMap {Map<String, List<Integer>}  получает параметр леммы из текста
     * @param requestLemmaMap {Map<String, List<Integer>>} получает параметр список лемм из запроса
     * @return {List<Integer>} возвращает список индексов слов для сниппета
     */
    private static List<Integer> getFindIndexWordsForSnippet(Map<List<String>, List<Integer>> textLemmaMap, Map<List<String>, Integer> requestLemmaMap) {
        List<List<Integer>> indexList = new ArrayList<>();
        for (List<String> lemma : requestLemmaMap.keySet()) {
            if (textLemmaMap.containsKey(lemma)) {
                indexList.add(textLemmaMap.get(lemma));
            }
        }
        List<Integer> resultIndexList = new ArrayList<>();
        for (int index : indexList.get(0)) {
            List<Integer> findIndexList = new ArrayList<>();
            findIndexList.add(index);
            if (indexList.size() == 1) {
                return findIndexList;
            }
            findIndexList.addAll(findMinDistanceIndexForSnippet(indexList, index));
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

    /** Нахлождение минимального расстояния между заданными словами в тексте для сниппета
     * @param indexList {List<List<Integer>>}  получает параметр список наор индесков слов
     * @param index {Map<String, List<Integer>>} получает параметр индеск слова
     * @return {List<Integer>} возвращает список индексов слов для сниппета
     */
    private static List<Integer> findMinDistanceIndexForSnippet(List<List<Integer>> indexList, int index) {
        List<Integer> findIndexList = new ArrayList<>();
        for (int i = 1; i < indexList.size(); i++) {
            int min = 1000;
            for (int j : indexList.get(i)) {
                if (Math.abs(min) > Math.abs(j - index)) {
                    min = j - index;
                }
            }
            findIndexList.add(min + index);
        }
        return findIndexList;
    }
}
