package com.github.cheesesoftware.PowerfulPerms.command;

import java.util.ArrayList;
import java.util.List;

import com.github.cheesesoftware.PowerfulPerms.common.ChatColor;
import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;

public class BaseCommand extends SubCommand {

    public BaseCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);

        subCommands.add(new UserBaseCommand(plugin, permissionManager));
        subCommands.add(new ShowGroupsCommand(plugin, permissionManager));
        subCommands.add(new GroupBaseCommand(plugin, permissionManager));
        subCommands.add(new ShowLaddersCommand(plugin, permissionManager));
        subCommands.add(new HasPermissionCommand(plugin, permissionManager));
        subCommands.add(new ReloadCommand(plugin, permissionManager));
        subCommands.add(new AboutCommand(plugin, permissionManager));
    }

    @Override
    public CommandResult execute(ICommand invoker, String sender, String[] args) {
        args = resolveArgs(args);
        resolveSequences(invoker, sender, args);
        return CommandResult.success;
    }

    private void continueExecute(ICommand invoker, String sender, String[] args) {
        boolean hasSomePermission = false;
        for (SubCommand subCommand : subCommands) {
            CommandResult result = subCommand.execute(invoker, sender, args);
            if (result == CommandResult.success)
                return;
            else if (result == CommandResult.noMatch)
                hasSomePermission = true;
        }

        if (hasSomePermission) {
            sendSender(invoker, sender, getUsage());
        } else {
            sendSender(invoker, sender, "You do not have permission.");
        }
    }

    @Override
    public List<String> getUsage() {
        List<String> usage = new ArrayList<String>();
        usage.add(ChatColor.RED + "~ " + ChatColor.BLUE + "PowerfulPerms" + ChatColor.BOLD + ChatColor.RED + " Reference ~");
        for (SubCommand subCommand : subCommands)
            usage.addAll(subCommand.getUsage());
        return usage;
    }

    public static String[] resolveArgs(String[] args) {
        List<String> output = new ArrayList<String>();
        String current = "";
        boolean adding = false;

        for (int i = 0; i < args.length; ++i) {
            String s = args[i];

            if (!adding) {
                current = "";

                if (!s.contains("\"")) {
                    output.add(s);
                    continue;
                }
            }

            for (char c : s.toCharArray()) {
                if (c == '"' || c == '\'') {
                    adding = !adding;
                } else
                    current += c;
            }

            if ((!adding || (adding && i == args.length - 1)) && !current.isEmpty())
                output.add(current);
            else if (adding)
                current += " ";

        }
        return output.toArray(new String[output.size()]);
    }

    private void resolveSequences(ICommand invoker, String sender, String[] args) {
        int beginIndex = -1;
        int endIndex = -1;

        for (int a = 0; a < args.length; ++a) {
            String arg = args[a];
            char[] chars = arg.toCharArray();
            for (int i = 0; i < chars.length; ++i) {
                if (beginIndex == -1 && chars[i] == '{')
                    beginIndex = i;
                if (endIndex == -1 && beginIndex != -1 && chars[i] == '}') {
                    endIndex = i;
                    // Found sequence
                    String sequence = arg.substring(beginIndex + 1, endIndex);
                    String[] sequenceList = sequence.split(",");
                    for (String s : sequenceList) {
                        StringBuilder builder = new StringBuilder(arg);
                        builder.replace(beginIndex, endIndex + 1, s);
                        String[] temp = args.clone();
                        temp[a] = builder.toString();
                        resolveSequences(invoker, sender, temp);
                    }
                }

            }

            if (beginIndex == -1 && endIndex == -1) {
                // Didn't find any more sequence
                continueExecute(invoker, sender, args);
            }
        }
    }
}
