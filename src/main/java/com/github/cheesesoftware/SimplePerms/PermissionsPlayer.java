package com.github.cheesesoftware.SimplePerms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

public class PermissionsPlayer {
    private Player player;
    private Group group;
    private ArrayList<SimplePermission> permissions = new ArrayList<SimplePermission>();
    private PermissionAttachment pa;
    private String prefix = "";
    private String suffix = "";

    public PermissionsPlayer(Player p, Group group, ArrayList<SimplePermission> permissions, PermissionAttachment pa, String prefix, String suffix) {
	this.player = p;
	this.group = group;
	this.permissions = permissions;
	this.pa = pa;
	this.prefix = prefix;
	this.suffix = suffix;
	this.UpdatePermissionAttachment();
    }

    public void Update(Group group, PermissionAttachment pa, String prefix) {
	this.group = group != null ? group : this.group;
	this.pa = pa != null ? pa : this.pa;
	this.prefix = prefix != null ? prefix : this.prefix;
    }

    public Group getGroup() {
	return this.group;
    }

    public PermissionAttachment getPermissionAttachment() {
	return this.pa;
    }

    public void clearPermissions() {
	permissions.clear();
    }

    public ArrayList<SimplePermission> getPermissions() {
	ArrayList<SimplePermission> newTemp = new ArrayList<SimplePermission>();
	newTemp.addAll(this.permissions);
	if (group != null) {
	    newTemp.addAll(group.getPermissions());
	}
	return newTemp;
    }

    public void UpdatePermissionAttachment() {
	if (group != null) {
	    for (SimplePermission e : group.getPermissions()) {
		boolean isSameServer = false;
		boolean isSameWorld = false;

		if (e.getServer().isEmpty() || e.getServer().equalsIgnoreCase("ALL") || e.getServer().equals(Bukkit.getServerName()))
		    isSameServer = true;

		if (e.getWorld().isEmpty() || e.getWorld().equalsIgnoreCase("ALL") || e.getWorld().equals(player.getWorld().getName()))
		    isSameWorld = true;

		pa.setPermission(e.getPermissionString(), (isSameServer && isSameWorld ? true : false));
	    }
	}
	for (SimplePermission e : permissions) {
	    boolean isSameServer = false;
	    boolean isSameWorld = false;

	    if (e.getServer().isEmpty() || e.getServer().equalsIgnoreCase("ALL") || e.getServer().equals(Bukkit.getServerName()))
		isSameServer = true;

	    if (e.getWorld().isEmpty() || e.getWorld().equalsIgnoreCase("ALL") || e.getWorld().equals(player.getWorld().getName()))
		isSameWorld = true;

	    pa.setPermission(e.getPermissionString(), (isSameServer && isSameWorld ? true : false));
	}
    }

    public String getPrefix() {
	return (!this.prefix.isEmpty() ? this.prefix : (group != null && group.getPrefix() != "" ? group.getPrefix() : ""));
    }

    public String getSuffix() {
	return (!this.suffix.isEmpty() ? this.suffix : (group != null && group.getSuffix() != "" ? group.getSuffix() : ": "));
    }
}
