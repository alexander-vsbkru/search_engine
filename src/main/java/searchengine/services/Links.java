package searchengine.services;

import searchengine.model.PageEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class Links  extends RecursiveTask<List<PageEntity>>
{
    private final Page page;

    public Links(Page page) {
        this.page = page;
    }

    @Override
    protected List<PageEntity> compute(){
        List<PageEntity> links = new ArrayList<>();

        List<Links> linksList = new ArrayList<>();
        try {
            Thread.sleep(5000);
            for (Page child : page.getChildren())
            {
                links.add(IndexingServiceImpl.addOnePage(child.getSiteParent(), child.getLink()));
                Links task = new Links(child);
                task.fork();
                linksList.add(task);
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        for (Links task : linksList) {
           links.addAll(task.join());
        }

       return links;
    }

}
