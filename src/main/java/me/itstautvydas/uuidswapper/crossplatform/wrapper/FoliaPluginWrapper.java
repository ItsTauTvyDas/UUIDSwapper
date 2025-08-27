package me.itstautvydas.uuidswapper.crossplatform.wrapper;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.itstautvydas.uuidswapper.crossplatform.PluginTaskWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class FoliaPluginWrapper extends PaperPluginWrapper {
    @Override
    public PluginTaskWrapper scheduleTask(Runnable run, @Nullable Long repeatInSeconds, long delayInSeconds) {
        AsyncScheduler asyncScheduler = server.getAsyncScheduler();
        ScheduledTask task;
        if (delayInSeconds == 0 && repeatInSeconds == null) {
            task = asyncScheduler.runNow(handle, (x) -> run.run());
        } else if (repeatInSeconds == null) {
            task = asyncScheduler.runDelayed(handle, (x) -> run.run(), delayInSeconds, TimeUnit.SECONDS);
        } else {
            task = asyncScheduler.runAtFixedRate(handle, (x) -> run.run(), delayInSeconds, repeatInSeconds, TimeUnit.SECONDS);
        }
        return new PluginTaskWrapper(task) {
            @Override
            public void cancel() {
                ((ScheduledTask)handle).cancel();
            }
        };
    }
}
