package searchengine.services;

import searchengine.model.PageEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

/** Класс задачи по обходу страниц сайта */
public class LinksParser extends RecursiveTask<List<PageEntity>>
{
    private final Page page;

    public LinksParser(Page page) {
        this.page = page;
    }

    @Override
    protected List<PageEntity> compute(){
        List<PageEntity> links = new ArrayList<>();

        List<LinksParser> linksParserList = new ArrayList<>();
        try {
            Thread.sleep(5000);
            for (Page child : page.getChildren())
            {
                links.add(RepositoryMethods.addOnePageToDB(child.getSiteParent(), child.getLink()));
                LinksParser task = new LinksParser(child);
                task.fork();
                linksParserList.add(task);
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        for (LinksParser task : linksParserList) {
           links.addAll(task.join());
        }

       return links;
    }

}
