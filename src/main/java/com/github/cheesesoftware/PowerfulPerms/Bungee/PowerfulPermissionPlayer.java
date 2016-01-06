package com.github.cheesesoftware.PowerfulPerms.Bungee;

import java.util.HashMap;
import java.util.List;

import com.github.cheesesoftware.PowerfulPerms.common.PermissionPlayerBase;
import com.github.cheesesoftware.PowerfulPermsAPI.CachedGroup;
import com.github.cheesesoftware.PowerfulPermsAPI.Permission;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class PowerfulPermissionPlayer extends PermissionPlayerBase {
    private ProxiedPlayer player;

    public PowerfulPermissionPlayer(ProxiedPlayer p, HashMap<String, List<CachedGroup>> serverGroups, List<Permission> permissions, String prefix, String suffix, PowerfulPermsPlugin plugin) {
        super(serverGroups, permissions, prefix, suffix, plugin);
        this.player = p;
    }

    public PowerfulPermissionPlayer(ProxiedPlayer p, PermissionPlayerBase base, PowerfulPermsPlugin plugin) {
        super(base.getCachedGroups(), base.getPermissions(), base.getPrefix(), base.getSuffix(), plugin);
        this.player = p;
    }

    /**
     * Update this PermissionsPlayerBase with data from another one.
     */
    @Override
    public void update(PermissionPlayerBase base) {
        super.update(base);
        this.updatePermissions();
    }

    /**
     * Returns the player attached to this PermissionsPlayer.
     */
    public ProxiedPlayer getPlayer() {
        return this.player;
    }

    /**
     * Sets the player's groups as seen in getServerGroups() Changes won't save for now.
     */
    @Override
    public void setGroups(HashMap<String, List<CachedGroup>> serverGroups) {
        super.setGroups(serverGroups);
        this.updatePermissions();
    }

    /**
     * Internal function to update the permissions.
     */
    public void updatePermissions() {
        if (this.player.getServer() != null)
            this.updatePermissions(this.player.getServer().getInfo());
    }

    /**
     * Internal function to update the PermissionAttachment.
     */
    public void updatePermissions(ServerInfo serverInfo) {
        this.updateGroups(serverInfo.getName());
        this.realPermissions = super.calculatePermissions(serverInfo.getName(), null);
    }
}
