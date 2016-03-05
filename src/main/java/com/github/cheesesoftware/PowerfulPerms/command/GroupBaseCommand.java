package com.github.cheesesoftware.PowerfulPerms.command;

import java.util.ArrayList;
import java.util.List;

import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;

public class GroupBaseCommand extends SubCommand {

    public GroupBaseCommand(PowerfulPermsPlugin plugin, PermissionManager permissionManager) {
        super(plugin, permissionManager);

        subCommands.add(new GroupCommand(plugin, permissionManager));
    }

    @Override
    public CommandResult execute(ICommand invoker, String sender, String[] args) {
        if (hasBasicPerms(invoker, sender, null)) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("group")) {

                String[] newArgs = new String[args.length - 1];
                System.arraycopy(args, 1, newArgs, 0, args.length - 1);

                boolean hasSomePermission = false;
                for (SubCommand subCommand : subCommands) {
                    CommandResult result = subCommand.execute(invoker, sender, newArgs);
                    if (result == CommandResult.success) {
                        return CommandResult.success;
                    } else if (result == CommandResult.showUsage) {
                        sendSender(invoker, sender, subCommand.getUsage());
                        return CommandResult.success;
                    } else if (result == CommandResult.noMatch) {
                        hasSomePermission = true;

                    }
                }

                if (hasSomePermission)
                    return CommandResult.showUsage;
                else
                    return CommandResult.noPermission;
            }
            return CommandResult.noMatch;
        }
        return CommandResult.noPermission;
    }

    @Override
    public List<String> getUsage() {
        List<String> usage = new ArrayList<String>();
        for (SubCommand subCommand : subCommands)
            usage.addAll(subCommand.getUsage());
        usage.add("This is from GroupBaseCommand");
        return usage;
    }

}
