package com.github.cheesesoftware.PowerfulPerms.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import com.github.cheesesoftware.PowerfulPerms.common.ChatColor;
import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.Group;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;
import com.github.cheesesoftware.PowerfulPermsAPI.Response;
import com.google.common.util.concurrent.ListenableFuture;

public class GroupPrefixCommand extends SubCommand {

    public GroupPrefixCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);
        usage.add("/pp group <group> prefix set <prefix> (server) | ... prefix remove (server)");
    }

    @Override
    public CommandResult execute(final ICommand invoker, final String sender, String[] args) throws InterruptedException, ExecutionException {
        if (hasBasicPerms(invoker, sender, "powerfulperms.group.prefix")) {
            if (args != null && args.length >= 2 && args[1].equalsIgnoreCase("prefix")) {
                if (args.length == 3 && !args[2].equalsIgnoreCase("remove")) {
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

                String server = "";
                if (args.length >= 4 && args[2].equalsIgnoreCase("set")) {
                    String prefix = "";
                    if (args.length >= 5 && args[3].toCharArray()[0] != '"')
                        server = args[4];
                    if (args[3].length() >= 1 && args[3].toCharArray()[0] == '"') {
                        // Input is between quote marks.
                        String result = "";
                        result += args[3].substring(1) + " ";

                        int lastArg = 3;
                        if (!result.endsWith("\" ")) {
                            if (args.length >= 5) {
                                for (int i = 4; i < args.length; i++) {
                                    result += args[i] + " ";
                                    if (args[i].endsWith("\"")) {
                                        lastArg = i;
                                        break;
                                    }
                                }
                            }
                        }

                        // If server is specified set server to argument after
                        if (args.length >= lastArg + 2) {
                            server = args[lastArg + 1];
                        }

                        // remove '" '
                        if (result.toCharArray()[result.length() - 1] == ' ')
                            result = result.substring(0, result.length() - 1);
                        if (result.toCharArray()[result.length() - 1] == '"')
                            result = result.substring(0, result.length() - 1);

                        prefix = result;
                    } else
                        prefix = args[3];

                    ListenableFuture<Response> first = permissionManager.setGroupPrefix(groupId, prefix, server);
                    sendSender(invoker, sender, first.get().getResponse());
                } else if (args.length >= 3 && args[2].equalsIgnoreCase("remove")) {
                    ListenableFuture<Response> first = permissionManager.setGroupPrefix(groupId, "", (args.length >= 4 ? args[3] : ""));
                    sendSender(invoker, sender, first.get().getResponse());
                } else {
                    HashMap<String, String> prefix = permissionManager.getGroupServerPrefix(groupId);
                    if (prefix != null) {
                        String output = "";
                        Iterator<Entry<String, String>> it = prefix.entrySet().iterator();
                        while (it.hasNext()) {
                            Entry<String, String> entry = it.next();
                            output += ChatColor.WHITE + "\"" + entry.getValue() + "\":" + (entry.getKey().isEmpty() ? ChatColor.RED + "ALL" + ChatColor.WHITE : entry.getKey());
                            if (it.hasNext())
                                output += ", ";
                        }
                        sendSender(invoker, sender, "Prefixes for group " + groupName + ": " + output);
                    } else
                        sendSender(invoker, sender, "Group does not exist.");
                }
                return CommandResult.success;
            } else
                return CommandResult.noMatch;
        } else
            return CommandResult.noPermission;
    }
}
