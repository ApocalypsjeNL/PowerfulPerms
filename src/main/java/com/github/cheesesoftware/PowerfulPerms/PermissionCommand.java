package com.github.cheesesoftware.PowerfulPerms;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;

public class PermissionCommand {

    private PermissionManagerBase permissionManager;

    public PermissionCommand(PermissionManagerBase permissionManager) {
        this.permissionManager = permissionManager;
    }

    public boolean onCommand(ICommand invoker, String sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("user") && args.length >= 2) {
            String playerName = args[1];
            int page = -1;
            if (args.length >= 3) {
                if (args[2].equalsIgnoreCase("clearperms")) {
                    PMR result = permissionManager.removePlayerPermissions(playerName);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("setprimarygroup") && args.length >= 4) {
                    String group = args[3];
                    PMR result = permissionManager.setPlayerPrimaryGroup(playerName, group);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("addgroup") && args.length >= 4) {
                    String group = args[3];
                    String server = "";
                    if (args.length >= 5)
                        server = args[4];
                    PMR result = permissionManager.addPlayerGroup(playerName, group, server);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("removegroup") && args.length >= 4) {
                    String group = args[3];
                    String server = "";
                    if (args.length >= 5)
                        server = args[4];
                    PMR result = permissionManager.removePlayerGroup(playerName, group, server);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args.length >= 4 && args[2].equalsIgnoreCase("add")) {
                    String permission = args[3];
                    String world = "";
                    String server = "";
                    if (args.length >= 5)
                        world = args[4];
                    if (args.length >= 6)
                        server = args[5];
                    if (server.equalsIgnoreCase("all"))
                        server = "";
                    if (world.equalsIgnoreCase("all"))
                        world = "";
                    PMR result = permissionManager.addPlayerPermission(playerName, permission, world, server);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args.length >= 4 && args[2].equalsIgnoreCase("remove")) {
                    String permission = args[3];
                    String world = "";
                    String server = "";
                    if (args.length >= 5)
                        world = args[4];
                    if (args.length >= 6)
                        server = args[5];
                    if (server.equalsIgnoreCase("all"))
                        server = "";
                    if (world.equalsIgnoreCase("all"))
                        world = "";
                    PMR result = permissionManager.removePlayerPermission(playerName, permission, world, server);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("prefix")) {
                    if (args.length >= 5 && args[3].equalsIgnoreCase("set")) {
                        String prefix = "";
                        if (args[4].length() >= 1 && args[4].toCharArray()[0] == '"') {
                            // Input is between quote marks.
                            String result = "";
                            result += args[4].substring(1) + " ";

                            if (args.length >= 6) {
                                for (int i = 5; i < args.length; i++) {
                                    result += args[i] + " ";
                                }
                            }

                            if (result.toCharArray()[result.length() - 1] == ' ')
                                result = result.substring(0, result.length() - 1);
                            if (result.toCharArray()[result.length() - 1] == '"')
                                result = result.substring(0, result.length() - 1);

                            prefix = result;
                        } else
                            prefix = args[4];

                        PMR result = permissionManager.setPlayerPrefix(playerName, prefix);
                        sendSender(invoker, sender, result.getResponse());

                    } else if (args.length >= 4 && args[3].equalsIgnoreCase("remove")) {
                        PMR result = permissionManager.setPlayerPrefix(playerName, "");
                        sendSender(invoker, sender, result.getResponse());
                    } else
                        sendSender(invoker, sender, "Prefix for player(non-inherited) " + playerName + ": " + permissionManager.getPlayerPrefix(playerName));
                } else if (args[2].equalsIgnoreCase("suffix")) {
                    if (args.length >= 5 && args[3].equalsIgnoreCase("set")) {
                        String suffix = "";
                        if (args[4].length() >= 1 && args[4].toCharArray()[0] == '"') {
                            // Input is between quote marks.
                            String result = "";
                            result += args[4].substring(1) + " ";

                            if (args.length >= 6) {
                                for (int i = 5; i < args.length; i++) {
                                    result += args[i] + " ";
                                }
                            }

                            if (result.toCharArray()[result.length() - 1] == ' ')
                                result = result.substring(0, result.length() - 1);
                            if (result.toCharArray()[result.length() - 1] == '"')
                                result = result.substring(0, result.length() - 1);

                            suffix = result;
                        } else
                            suffix = args[4];

                        PMR result = permissionManager.setPlayerSuffix(playerName, suffix);
                        sendSender(invoker, sender, result.getResponse());

                    } else if (args.length >= 4 && args[3].equalsIgnoreCase("remove")) {
                        PMR result = permissionManager.setPlayerSuffix(playerName, "");
                        sendSender(invoker, sender, result.getResponse());
                    } else
                        sendSender(invoker, sender, "Suffix for player(non-inherited) " + playerName + ": " + permissionManager.getPlayerSuffix(playerName));
                } else {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        showCommandInfo(invoker, sender);
                    }
                }
            }
            if (page != -1 || args.length == 2) {
                if (page == -1)
                    page = 1;
                page--;
                if (page < 0)
                    sendSender(invoker, sender, "Invalid page. Page negative.");
                // List player permissions
                Queue<String> rows = new java.util.ArrayDeque<String>();
                rows.add(ChatColor.BLUE + "Listing permissions for player " + playerName + ".");
                ResultSet result = permissionManager.getPlayerData(playerName);
                String tempUUID = "empty";
                try {
                    if (result != null)
                        tempUUID = result.getString("uuid");
                    rows.add(ChatColor.GREEN + "UUID" + ChatColor.WHITE + ": " + tempUUID);
                } catch (SQLException e1) {
                    e1.printStackTrace();
                }
                HashMap<String, List<Group>> groups = permissionManager.getPlayerGroups(playerName);
                Group primary = permissionManager.getPlayerPrimaryGroup(playerName);
                rows.add(ChatColor.GREEN + "Primary Group" + ChatColor.WHITE + ": " + (primary != null ? primary.getName() : "Player has no group."));

                String otherGroups = ChatColor.GREEN + "Groups" + ChatColor.WHITE + ": ";
                if (groups.size() > 0) {
                    Iterator<Entry<String, List<Group>>> it = groups.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<String, List<Group>> current = it.next();
                        Iterator<Group> itt = current.getValue().iterator();
                        while (itt.hasNext()) {
                            Group group = itt.next();
                            if (group != null) {
                                otherGroups += ChatColor.WHITE + group.getName() + ":" + ChatColor.RED + (current.getKey() == null || current.getKey().isEmpty() ? "ALL" : current.getKey());
                                if (it.hasNext() || itt.hasNext())
                                    otherGroups += ", ";
                            }
                        }
                    }
                }
                rows.add(otherGroups);

                ArrayList<PowerfulPermission> playerPerms = permissionManager.getPlayerPermissions(playerName);
                if (playerPerms.size() > 0)
                    for (PowerfulPermission e : playerPerms) {
                        rows.add(ChatColor.DARK_GREEN + e.getPermissionString() + ChatColor.WHITE + " (Server:" + (e.getServer().isEmpty() ? ChatColor.RED + "ALL" + ChatColor.WHITE : e.getServer())
                                + " World:" + (e.getWorld().isEmpty() ? ChatColor.RED + "ALL" + ChatColor.WHITE : e.getWorld()) + ")");
                    }
                else
                    rows.add("Player has no permissions.");

                List<List<String>> list = createList(rows, 19);
                sendSender(invoker, sender, ChatColor.BLUE + "Page " + (page + 1) + " of " + list.size());
                if (page < list.size()) {
                    for (String s : list.get(page))
                        sendSender(invoker, sender, s);
                } else
                    sendSender(invoker, sender, "Invalid page. Page too high. ");
            }

            // /////////////////////////////////////////////////////////////////////////////////////////////////
            // /////////////////////////GROUP COMMAND BEGIN/////////////////////////////////////////
            // ///////////////////////////////////////////////////////////////////////
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("group") && args.length >= 2) {
            String groupName = args[1];
            int page = -1;
            if (args.length >= 3) {
                if (args[2].equalsIgnoreCase("clearperms")) {
                    PMR result = permissionManager.removeGroupPermissions(groupName);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("create")) {
                    PMR result = permissionManager.createGroup(groupName);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("delete")) {
                    PMR result = permissionManager.deleteGroup(groupName);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args.length >= 4 && args[2].equalsIgnoreCase("add")) {
                    String permission = args[3];
                    String world = "";
                    String server = "";
                    if (args.length >= 5)
                        world = args[4];
                    if (args.length >= 6)
                        server = args[5];
                    if (server.equalsIgnoreCase("all"))
                        server = "";
                    if (world.equalsIgnoreCase("all"))
                        world = "";
                    PMR result = permissionManager.addGroupPermission(groupName, permission, world, server);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args.length >= 4 && args[2].equalsIgnoreCase("remove")) {
                    String permission = args[3];
                    String world = "";
                    String server = "";
                    if (args.length >= 5)
                        world = args[4];
                    if (args.length >= 6)
                        server = args[5];
                    if (server.equalsIgnoreCase("all"))
                        server = "";
                    if (world.equalsIgnoreCase("all"))
                        world = "";
                    PMR result = permissionManager.removeGroupPermission(groupName, permission, world, server);
                    sendSender(invoker, sender, result.getResponse());
                } else if (args[2].equalsIgnoreCase("prefix")) {
                    if (args.length >= 5 && args[3].equalsIgnoreCase("set")) {
                        String prefix = "";
                        if (args[4].length() >= 1 && args[4].toCharArray()[0] == '"') {
                            // Input is between quote marks.
                            String result = "";
                            result += args[4].substring(1) + " ";

                            if (args.length >= 6) {
                                for (int i = 5; i < args.length; i++) {
                                    result += args[i] + " ";
                                }
                            }

                            if (result.toCharArray()[result.length() - 1] == ' ')
                                result = result.substring(0, result.length() - 1);
                            if (result.toCharArray()[result.length() - 1] == '"')
                                result = result.substring(0, result.length() - 1);

                            prefix = result;
                        } else
                            prefix = args[4];

                        PMR result = permissionManager.setGroupPrefix(groupName, prefix);
                        sendSender(invoker, sender, result.getResponse());
                    } else if (args.length >= 4 && args[3].equalsIgnoreCase("remove")) {
                        PMR result = permissionManager.setGroupPrefix(groupName, "");
                        sendSender(invoker, sender, result.getResponse());
                    } else {
                        String prefix = permissionManager.getGroupPrefix(groupName);
                        sendSender(invoker, sender, "Prefix for group " + groupName + ": " + (prefix.equals("") ? ChatColor.RED + "none" : prefix));
                    }
                } else if (args[2].equalsIgnoreCase("suffix")) {
                    if (args.length >= 5 && args[3].equalsIgnoreCase("set")) {
                        String suffix = "";
                        if (args[4].length() >= 1 && args[4].toCharArray()[0] == '"') {
                            // Input is between quote marks.
                            String result = "";
                            result += args[4].substring(1) + " ";

                            if (args.length >= 6) {
                                for (int i = 5; i < args.length; i++) {
                                    result += args[i] + " ";
                                }
                            }

                            if (result.toCharArray()[result.length() - 1] == ' ')
                                result = result.substring(0, result.length() - 1);
                            if (result.toCharArray()[result.length() - 1] == '"')
                                result = result.substring(0, result.length() - 1);

                            suffix = result;
                        } else
                            suffix = args[4];

                        PMR result = permissionManager.setGroupSuffix(groupName, suffix);
                        sendSender(invoker, sender, result.getResponse());
                    } else if (args.length >= 4 && args[3].equalsIgnoreCase("remove")) {
                        PMR result = permissionManager.setGroupSuffix(groupName, "");
                        sendSender(invoker, sender, result.getResponse());
                    } else {
                        String suffix = permissionManager.getGroupSuffix(groupName);
                        sendSender(invoker, sender, "Suffix for group " + groupName + ": " + (suffix.equals("") ? ChatColor.RED + "none" : suffix));
                    }
                } else if (args[2].equalsIgnoreCase("parents")) {
                    if (args.length >= 5 && args[3].equalsIgnoreCase("add")) {
                        String parent = args[4];
                        PMR result = permissionManager.addGroupParent(groupName, parent);
                        sendSender(invoker, sender, result.getResponse());
                    } else if (args.length >= 5 && args[3].equalsIgnoreCase("remove")) {
                        String parent = args[4];
                        PMR result = permissionManager.removeGroupParent(groupName, parent);
                        sendSender(invoker, sender, result.getResponse());
                    } else {
                        // List parents
                        Group group = permissionManager.getGroup(groupName);
                        if (group != null) {
                            sendSender(invoker, sender, "Listing parents for group " + groupName + ":");

                            if (group.getParents() != null && group.getParents().size() > 0) {
                                for (Group g : group.getParents())
                                    sendSender(invoker, sender, g.getName());
                            } else
                                sendSender(invoker, sender, "Group has no parents.");
                        } else
                            sendSender(invoker, sender, "Group doesn't exist.");
                    }
                } else {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        showCommandInfo(invoker, sender);
                    }
                }
            }
            if (page != -1 || args.length == 2) {
                if (page == -1)
                    page = 1;
                page--;
                if (page < 0)
                    sendSender(invoker, sender, "Invalid page. Page negative.");
                // List group permissions
                Queue<String> rows = new java.util.ArrayDeque<String>();
                Group group = permissionManager.getGroup(groupName);
                if (group != null) {
                    rows.add("Listing permissions for group " + groupName + ":");
                    ArrayList<PowerfulPermission> permissions = group.getPermissions();
                    if (permissions.size() > 0) {
                        for (PowerfulPermission e : permissions)
                            rows.add(ChatColor.DARK_GREEN + e.getPermissionString() + ChatColor.WHITE + " (Server:"
                                    + (e.getServer() == null || e.getServer().isEmpty() ? ChatColor.RED + "ALL" + ChatColor.WHITE : e.getServer()) + " World:"
                                    + (e.getServer() == null || e.getWorld().isEmpty() ? ChatColor.RED + "ALL" + ChatColor.WHITE : e.getWorld()) + ")");
                    } else
                        rows.add("Group has no permissions.");

                } else
                    sendSender(invoker, sender, "Group doesn't exist.");

                List<List<String>> list = createList(rows, 19);
                sendSender(invoker, sender, ChatColor.BLUE + "Page " + (page + 1) + " of " + list.size());
                if (page < list.size()) {
                    for (String s : list.get(page))
                        sendSender(invoker, sender, s);
                } else
                    sendSender(invoker, sender, "Invalid page. Page too high. ");
            }
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("groups")) {
            Collection<Group> groups = permissionManager.getGroups();
            String s = "";
            for (Group group : groups) {
                s += group.getName() + ", ";
            }
            if (s.length() > 0 && groups.size() > 0) {
                s = s.substring(0, s.length() - 2);
            }
            sendSender(invoker, sender, "Groups: " + s);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            permissionManager.reloadGroups();
            permissionManager.reloadPlayers();
            sendSender(invoker, sender, "Groups and players have been reloaded.");
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("globalreload")) {
            permissionManager.reloadGroups();
            permissionManager.reloadPlayers();

            permissionManager.notifyReloadGroups();
            permissionManager.notifyReloadPlayers();
            sendSender(invoker, sender, "Groups and players have been reloaded globally.");
        } else
            showCommandInfo(invoker, sender);
        return true;
    }

    private List<List<String>> createList(Queue<String> input, int rowsPerPage) {
        int rowWidth = 55;
        List<List<String>> list = new ArrayList<List<String>>();
        while (input.size() > 0) {
            List<String> page = new ArrayList<String>();
            for (int j = 0; j < rowsPerPage; j++) {
                if (input.size() > 0) {
                    String row = input.remove();
                    page.add(row);
                    if (row.length() > rowWidth)
                        j++;

                }
            }
            list.add(page);
        }
        return list;
    }

    private void sendSender(ICommand command, String sender, String message) {
        command.sendSender(sender, PermissionManagerBase.pluginPrefixShort + message);
    }

    private void showCommandInfo(ICommand command, String sender) {
        String helpPrefix = "§b ";
        command.sendSender(sender, ChatColor.RED + "~ " + ChatColor.BLUE + "PowerfulPerms" + ChatColor.BOLD + ChatColor.RED + " Reference ~");
        command.sendSender(sender, helpPrefix + "/pp user <username>");
        command.sendSender(sender, helpPrefix + "/pp user <username> setprimarygroup <group>");
        command.sendSender(sender, helpPrefix + "/pp user <username> addgroup <group> (server)");
        command.sendSender(sender, helpPrefix + "/pp user <username> removegroup <group> (server)");
        command.sendSender(sender, helpPrefix + "/pp user <username> add/remove <permission> (world) (server)");
        command.sendSender(sender, helpPrefix + "/pp user <username> clearperms");
        command.sendSender(sender, helpPrefix + "/pp user <username> prefix set/remove <prefix>");
        command.sendSender(sender, helpPrefix + "/pp user <username> suffix set/remove <suffix>");
        command.sendSender(sender, helpPrefix + "/pp groups");
        command.sendSender(sender, helpPrefix + "/pp group <group>");
        command.sendSender(sender, helpPrefix + "/pp group <group> create/delete/clearperms");
        command.sendSender(sender, helpPrefix + "/pp group <group> add/remove <permission> (world) (server)");
        command.sendSender(sender, helpPrefix + "/pp group <group> parents add/remove <parent>");
        command.sendSender(sender, helpPrefix + "/pp group <group> prefix set/remove <prefix>");
        command.sendSender(sender, helpPrefix + "/pp group <group> suffix set/remove <suffix>");
        command.sendSender(sender, helpPrefix + "/pp reload  |  /pp globalreload");
        command.sendSender(sender, helpPrefix + "PowerfulPerms version " + command.getVersion() + " by gustav9797");
    }

}
