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

        boolean hasSomePermission = false;
        for (SubCommand subCommand : subCommands) {
            CommandResult result = subCommand.execute(invoker, sender, args);
            if (result == CommandResult.success) {
                return CommandResult.success;
            } else if (result == CommandResult.noMatch) {
                hasSomePermission = true;
            }
        }

        if (hasSomePermission) {
            sendSender(invoker, sender, getUsage());
            return CommandResult.success;
        } else {
            sendSender(invoker, sender, "You do not have permission.");
            return CommandResult.noPermission;
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

    public String[] resolveArgs(String[] args) {
        List<String> output = new ArrayList<String>();
        String current = "";
        boolean adding = false;

        for (int i = 0; i < args.length; ++i) {
            String s = args[i];

            if (!adding) {
                current = "";
            }

            if (!s.contains("\"")) {
                output.add(s);
                continue;
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
}
