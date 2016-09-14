package com.github.cheesesoftware.PowerfulPerms.command;

import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPerms.common.PermissionManagerBase;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;

public class TestDatabaseCommand extends SubCommand {

    public TestDatabaseCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);
        usage.add("/pp test database");
    }

    @Override
    public CommandResult execute(final ICommand invoker, final String sender, String[] args) {
        if (hasBasicPerms(invoker, sender, "powerfulperms.test.database")) {
            if (args != null && args.length >= 1 && args[0].equalsIgnoreCase("database")) {
                final PermissionManagerBase base = (PermissionManagerBase) permissionManager;
                plugin.runTaskAsynchronously(new Runnable() {
                    public void run() {
                        boolean success = base.getDatabase().ping();
                        if (success)
                            sendSender(invoker, sender, "Your database connection is fine.");
                        else
                            sendSender(invoker, sender, "Could not open a connection to your database. Check the console for any exceptions.");
                    }
                });
                return CommandResult.success;
            } else
                return CommandResult.noMatch;
        } else
            return CommandResult.noPermission;
    }

}
