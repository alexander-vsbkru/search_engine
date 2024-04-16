package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Класс методов для получения лемм из текста */
public class GetLemmasFromText {

    private static final String WORD_TYPE_REGEX = "[А-яЁё]+"; //"\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    /** Плучение списка лемм из текста с количеством каждой леммы в тексте
     * @param text {string} получает параметр текст
     * @return {HashMap<String, Integer>} возвращает HashMap<String, Integer> со списком лемм с их количеством в тексте
     */
    public HashMap<List<String>, Integer> getLemmaList(String text) throws IOException {
        HashMap<List<String>, Integer> resultMap = new HashMap<>();
        String resultWithoutTags = deleteTags(text);
        Pattern pattern = Pattern.compile(WORD_TYPE_REGEX);
        Matcher matcher = pattern.matcher(resultWithoutTags);

        while (matcher.find()) {
            List<String> wordBase = getWordBase(matcher, resultWithoutTags);
            int count = 0;
            List<String> lemmaList = new ArrayList<>();
            for (String word : wordBase) {
                LuceneMorphology luceneMorph = new RussianLuceneMorphology();
                List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
                if (hasParticleProperty(wordBaseForms)) {
                    break;
                }
                lemmaList.add(word);
            }
            if (resultMap.containsKey(lemmaList)) {
                count = resultMap.get(lemmaList);
            }
            count++;
            resultMap.put(lemmaList, count);
        }
        return resultMap;
    }

    /** Получение базовых форм слова
     * @param matcher {Matcher} получает параметр matcher слово, для которого нужно выделить базовые формы
     * @param text {String} получает параметр текст
     * @return {List<String>} возвращает HashMap<String, Integer> со списком лемм с их количеством в тексте
     */
    public List<String> getWordBase(Matcher matcher, String text) throws IOException {
        int start = matcher.start();
        int end = matcher.end();
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();
        return luceneMorph.getNormalForms(text.substring(start, end).toLowerCase(Locale.ROOT));
    }

    /** Проверка является ли слово служебной частью речи
     * @param wordBaseForms {List<String} получает параметр список базовых форм слова
     * @return {boolean}
     */
    private boolean hasParticleProperty(List<String> wordBaseForms) {
        for (String wordForm : wordBaseForms) {
            for (String property : particlesNames) {
                if (wordForm.toUpperCase().contains(property)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Удаление тегов
     * @param text {String} принимает параметр текст
     * return {String} возвращает текст без тегов
     */
    public String deleteTags(String text) {

        String tag_regex = "<[^>]+>";

        Pattern pattern = Pattern.compile(tag_regex);
        Matcher matcher = pattern.matcher(text);

        if (text.isBlank()) {
            return text;
        }
        return matcher.replaceAll("");
    }
}