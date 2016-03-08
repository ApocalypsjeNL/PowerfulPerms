package com.github.cheesesoftware.PowerfulPerms.command;

import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;
import com.github.cheesesoftware.PowerfulPermsAPI.ResponseRunnable;

public class GroupAddPermissionCommand extends SubCommand {

    public GroupAddPermissionCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);
        usage.add("/pp group <group> add <permission> (server) (world)");
    }

    @Override
    public CommandResult execute(final ICommand invoker, final String sender, String[] args) {
        if (hasBasicPerms(invoker, sender, "powerfulperms.group.add")) {
            if (args != null && args.length >= 2 && args[1].equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    sendSender(invoker, sender, getUsage());
                    return CommandResult.success;
                }
                final String groupName = args[0];

                final ResponseRunnable response = new ResponseRunnable() {
                    @Override
                    public void run() {
                        sendSender(invoker, sender, response);
                    }
                };

                String permission = args[2];
                String world = "";
                String server = "";
                if (args.length >= 4)
                    server = args[3];
                if (args.length >= 5)
                    world = args[4];
                if (server.equalsIgnoreCase("all"))
                    server = "";
                if (world.equalsIgnoreCase("all"))
                    world = "";
                permissionManager.addGroupPermission(groupName, permission, world, server, response);
                return CommandResult.success;
            } else
                return CommandResult.noMatch;
        } else
            return CommandResult.noPermission;
    }
}
