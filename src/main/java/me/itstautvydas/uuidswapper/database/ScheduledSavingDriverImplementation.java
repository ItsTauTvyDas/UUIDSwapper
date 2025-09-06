package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import lombok.Setter;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.multiplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.processor.ReadMeCallSuperClass;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Getter
@ReadMeCallSuperClass()
public abstract class ScheduledSavingDriverImplementation<T> extends DriverImplementation {
    private transient long ticked;
    @Setter
    private transient boolean paused;
    private transient PluginTaskWrapper timer;
    private transient long lastSavedAt;
    @ReadMeDescription("Interval in minutes between automatic saves to file or database")
    private long saveInterval;
    private final transient BlockingQueue<Queueable> queue = new LinkedBlockingQueue<>();
    @ReadMeDescription("The maximum number of data saved to the database in one go before moving on to the next batch/group")
    private int maxBatchSize;

    public final void initTimer() {
        destroyTimer();
        timer = MultiPlatform.get().scheduleTask(() -> {
            if (paused)
                return;
            ticked++;
            onPeriodicTick();
            if (ticked == saveInterval * 60) {
                lastSavedAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                MultiPlatform.get().scheduleTaskAsync(() -> {
                    try {
                        saveAsync();
                    } catch (Exception ex) {
                        error("Failed to save data to the database!", ex);
                    }
                });
                resetTicked();
            }
        }, saveInterval * 60, 0);
    }

    public void saveAsync() throws Exception {
        if (queue.isEmpty()) return;
        var arg = onBatchStart();
        var batch = new ArrayList<Queueable>(maxBatchSize);
        while (!queue.isEmpty()) {
            batch.clear();
            queue.drainTo(batch, maxBatchSize);
            onBatchCommit(batch, arg);
        }
        onBatchEnd(arg);
    }

    public final void destroyTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        resetTicked();
    }

    public final void resetTicked() {
        ticked = 0;
    }

    public final long getElapsedMillisSinceLastSave() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - lastSavedAt;
    }

    public void flushAll() throws Exception {
        if (queue.isEmpty()) return;
        var flush = new ArrayList<Queueable>();
        queue.drainTo(flush);
        var arg = onBatchStart();
        onBatchCommit(flush, arg);
        onBatchEnd(arg);
    }

    public void onPeriodicTick() {}

    @Override
    public void storeRandomPlayerCache(PlayerData player) {
        if (player != null)
            queue.offer(player);
    }

    @Override
    public void storeOnlinePlayerCache(OnlinePlayerData player) {
        if (player != null)
            queue.offer(player);
    }

    public abstract T onBatchStart() throws Exception;
    public abstract void onBatchCommit(List<Queueable> batch, T arg) throws Exception;
    public abstract void onBatchEnd(T arg) throws Exception;
}
