package com.github.cheesesoftware.PowerfulPerms.command;

import java.util.concurrent.ExecutionException;

import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.Group;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;
import com.github.cheesesoftware.PowerfulPermsAPI.Response;
import com.google.common.util.concurrent.ListenableFuture;

public class GroupSetRankCommand extends SubCommand {

    public GroupSetRankCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);
        usage.add("/pp group <group> setrank <rank>");
    }

    @Override
    public CommandResult execute(final ICommand invoker, final String sender, String[] args) throws InterruptedException, ExecutionException {
        if (hasBasicPerms(invoker, sender, "powerfulperms.group.setrank")) {
            if (args != null && args.length >= 2 && args[1].equalsIgnoreCase("setrank")) {
                if (args.length == 2) {
                    sendSender(invoker, sender, getUsage());
                    return CommandResult.success;
                }
                final String groupName = args[0];
                final Group group = permissionManager.getGroup(groupName);
                if (group == null) {
                    sendSender(invoker, sender, "Group does not exist.");
                    return CommandResult.success;
                }
                int groupId = group.getId();

                try {
                    int rank = Integer.parseInt(args[2]);
                    ListenableFuture<Response> first = permissionManager.setGroupRank(groupId, rank);
                    sendSender(invoker, sender, first.get().getResponse());
                } catch (NumberFormatException e) {
                    sendSender(invoker, sender, "Rank must be a number.");
                }
                return CommandResult.success;
            } else
                return CommandResult.noMatch;
        } else
            return CommandResult.noPermission;
    }
}
