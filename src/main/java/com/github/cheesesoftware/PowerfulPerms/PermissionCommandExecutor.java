package com.github.cheesesoftware.PowerfulPerms;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.github.cheesesoftware.PowerfulPerms.command.BaseCommand;
import com.github.cheesesoftware.PowerfulPerms.common.ICommand;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;

public class PermissionCommandExecutor implements ICommand, CommandExecutor {

    private BaseCommand cmd;

    protected PermissionCommandExecutor(PermissionManager permissionManager) {
        cmd = new BaseCommand(PowerfulPerms.getPlugin(), permissionManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        cmd.execute(this, sender.getName(), args);
        return true;
    }

    @Override
    public boolean hasPermission(String name, String permission) {
        if (name.equalsIgnoreCase("console"))
            return true;
        Player player = Bukkit.getPlayerExact(name);
        if (player != null && player.hasPermission(permission))
            return true;
        return false;
    }

}
