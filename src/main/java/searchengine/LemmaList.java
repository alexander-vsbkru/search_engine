package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LemmaList {

    private static final String WORD_TYPE_REGEX = "[А-яЁё]+"; //"\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public HashMap<String, Integer> getLemmaList(String text) throws IOException {
        HashMap<String, Integer> resultMap = new HashMap<>();
        String resultWithoutTags = deleteTags(text);
        Pattern pattern = Pattern.compile(WORD_TYPE_REGEX);
        Matcher matcher = pattern.matcher(resultWithoutTags);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> wordBase = luceneMorph.getNormalForms(resultWithoutTags.substring(start, end).toLowerCase(Locale.ROOT));

            for (String word : wordBase) {
                List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
                if (hasParticleProperty(wordBaseForms)) {
                    break;
                }
                int count = 0;
                if (resultMap.containsKey(word)) {
                    count = resultMap.get(word);
                }
                count++;
                resultMap.put(word, count);
            }
        }
        return resultMap;
    }
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

    public String deleteTags(String text) {

        String tag_regex = "<[^>]+>";

        Pattern pattern = Pattern.compile(tag_regex);
        Matcher matcher = pattern.matcher(text);

        if (text.length() == 0) {
            return text;
        }
        return matcher.replaceAll("");
    }
}

