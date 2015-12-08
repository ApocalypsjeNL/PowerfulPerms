package com.github.cheesesoftware.PowerfulPerms.Bungee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.github.cheesesoftware.PowerfulPerms.Group;
import com.github.cheesesoftware.PowerfulPerms.PermissionsPlayerBase;
import com.github.cheesesoftware.PowerfulPerms.PowerfulPermission;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class PermissionsPlayer extends PermissionsPlayerBase {
    private ProxiedPlayer player;

    public PermissionsPlayer(ProxiedPlayer p, HashMap<String, List<Group>> serverGroups, ArrayList<PowerfulPermission> permissions, String prefix, String suffix) {
        super(serverGroups, permissions, prefix, suffix);
        this.player = p;
    }
    
    public PermissionsPlayer(ProxiedPlayer p, PermissionsPlayerBase base) {
        super(base.getServerGroups(), base.getPermissions(), base.getPrefix(), base.getSuffix());
        this.player = p;
    }
    
    /**
     * Update this PermissionsPlayerBase with data from another one.
     */
    @Override
    public void update(PermissionsPlayerBase base) {
        super.update(base);
        this.UpdatePermissions();
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
    public void setServerGroups(HashMap<String, List<Group>> serverGroups) {
        super.setServerGroups(serverGroups);
        this.UpdatePermissions();
    }

    /**
     * Clears the player-specific permissions of this player. Changes won't save for now.
     */
    @Override
    public void clearPermissions() {
        super.clearPermissions();
        this.UpdatePermissions();
    }

    /**
     * Internal function to update the permissions.
     */
    public void UpdatePermissions() {
        if(this.player.getServer() != null)
            this.UpdatePermissions(this.player.getServer().getInfo());
    }

    /**
     * Internal function to update the PermissionAttachment.
     */
    public void UpdatePermissions(ServerInfo serverInfo) {
        this.realPermissions = super.calculatePermissions(serverInfo.getName(), null);
    }
}
