package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.SiteEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

public class Page {

    private final String link;
    private final SiteEntity siteParent;
    private final List<String> duplicateLinks;

    public Page(SiteEntity siteParent, String link, List<String> dublicateLinks) {
        this.siteParent = siteParent;
        this.link = link;
        this.duplicateLinks = dublicateLinks;
    }

    public SiteEntity getSiteParent() {
        return siteParent;
    }

    public String getLink() {
        return link;
    }

    Collection<Page> getChildren() {
        Collection<Page> children = new ArrayList<>();

        try {
        Document doc = Jsoup.connect(siteParent.getUrl() + link).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6").referrer("http://www.google.com").get();

        Elements links = doc.select("a[href]");

            for (Element element : links) {
                String link = element.attr("href");
                if (duplicateLinks.contains(link)) {
                    continue;
                }
                if (link.equals("/")) {
                    continue;
                }
                if (link.equals(siteParent.getUrl())) {
                    continue;
                }

                if (link.charAt(0) == '/') {
                    if (link.lastIndexOf("?") > -1) {
                        continue;
                    }
                    duplicateLinks.add(link);
                    Logger.getLogger(Page.class.getName()).info("link =  " + link);
                //    link = link.substring(siteParent.getUrl().length());

                    children.add(new Page(siteParent, link, duplicateLinks));
                }
            }
        }  catch (Exception ex) {
            ex.printStackTrace();
        }
        return children;
    }
}
