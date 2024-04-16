package searchengine.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

/** Класс задачи по обходу страниц сайта */
@Slf4j
public class LinksParser extends RecursiveTask<List<Page>>
{
    private final Page page;

    public LinksParser(Page page) {
        this.page = page;
    }

    @Override
    protected List<Page> compute(){
        List<Page> links = new ArrayList<>();
        List<LinksParser> linksParserList = new ArrayList<>();
        try {
            Thread.sleep(5000);
            for (Page child : page.getChildren())
            {
                links.add(new Page(child.getSiteParent(), child.getLink(), null));
                LinksParser task = new LinksParser(child);
                task.fork();
                linksParserList.add(task);
            }
        } catch (InterruptedException e) {
            log.error(e.toString());
        }

        for (LinksParser task : linksParserList) {
           links.addAll(task.join());
        }

       return links;
    }

}
