package com.github.cheesesoftware.PowerfulPerms.common;

import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;

public abstract class SchedulerBase implements IScheduler {

    private PowerfulPermsPlugin plugin;

    public SchedulerBase(PowerfulPermsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable runnable, boolean sameThread) {
        if (sameThread)
            plugin.debug("Running async task on CURRENT thread");
        else
            plugin.debug("Running async task on NEW thread");
    }

    @Override
    public void runSync(Runnable runnable, boolean sameThread) {
        if (sameThread)
            plugin.debug("Running sync task on CURRENT thread");
        else
            plugin.debug("Running sync task on MAIN thread");
    }

}
