package com.github.cheesesoftware.PowerfulPerms.command;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;
import com.google.common.util.concurrent.ListenableFuture;

public class UserHasPermissionCommand extends SubCommand {

    public UserHasPermissionCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);
        usage.add("/pp user <user> haspermission <permission> (server) (world)");
    }

    @Override
    public CommandResult execute(final ICommand invoker, final String sender, String[] args) throws InterruptedException, ExecutionException {
        if (hasBasicPerms(invoker, sender, "powerfulperms.user.haspermission") || (args != null && args.length >= 3 && hasPermission(invoker, sender, "powerfulperms.user.haspermission." + args[2]))) {
            if (args != null && args.length >= 2 && args[1].equalsIgnoreCase("haspermission")) {
                if (args.length < 3) {
                    sendSender(invoker, sender, getUsage());
                    return CommandResult.success;
                }
                final String playerName = args[0];
                final String permission = args[2];
                String server = "";
                if (args.length >= 4)
                    server = args[3];
                String world = "";
                if (args.length >= 5)
                    world = args[4];

                ListenableFuture<UUID> first = permissionManager.getConvertUUID(playerName);
                final UUID uuid = first.get();
                if (uuid == null) {
                    sendSender(invoker, sender, "Could not find player UUID.");
                } else {
                    ListenableFuture<Boolean> second = permissionManager.playerHasPermission(uuid, permission, world, server);
                    Boolean has = second.get();
                    if (has != null) {
                        if (has)
                            sendSender(invoker, sender, "The player has the permission \"" + permission + "\".");
                        else
                            sendSender(invoker, sender, "The player doesn't have the permission \"" + permission + "\".");
                    } else
                        sendSender(invoker, sender, "The permission \"" + permission + "\" is not set.");
                }
                return CommandResult.success;
            } else
                return CommandResult.noMatch;
        } else
            return CommandResult.noPermission;
    }
}
