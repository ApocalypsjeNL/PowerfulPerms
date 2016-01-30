package com.github.cheesesoftware.PowerfulPerms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionRemovedExecutor;
import org.bukkit.plugin.Plugin;

public class CustomPermissibleBase extends PermissibleBase {

    private PowerfulPermissionPlayer permissionsPlayer;
    private List<PermissionAttachment> ppAttachments = new LinkedList<PermissionAttachment>();
    private Permissible parent = this;
    private List<String> temporaryPrePermissions = new ArrayList<String>();
    private List<String> temporaryPostPermissions = new ArrayList<String>();

    public CustomPermissibleBase(PowerfulPermissionPlayer permissionsPlayer) {
        super(permissionsPlayer.getPlayer());
        this.permissionsPlayer = permissionsPlayer;

        if (permissionsPlayer.getPlayer() instanceof Permissible) {
            this.parent = (Permissible) permissionsPlayer.getPlayer();
        }

        this.recalculatePermissions();
    }

    public boolean isOp() {
        if (permissionsPlayer == null || permissionsPlayer.getPlayer() == null) {
            return false;
        } else {
            return permissionsPlayer.getPlayer().isOp();
        }
    }

    public void setOp(boolean value) {
        if (permissionsPlayer == null || permissionsPlayer.getPlayer() == null) {
            throw new UnsupportedOperationException("Cannot change op value as no ServerOperator is set");
        } else {
            permissionsPlayer.getPlayer().setOp(value);
        }
    }

    @Override
    public boolean isPermissionSet(String permission) {
        if (permission != null) {
            permission = permission.toLowerCase();
            return permissionsPlayer.isPermissionSet(permission);
        } else
            throw new IllegalArgumentException("Permission cannot be null");
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        if (perm == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        return isPermissionSet(perm.getName());
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }

        permission = permission.toLowerCase();
        boolean permissionSet = permissionsPlayer.isPermissionSet(permission);

        if (!permissionSet) {
            Permission perm = Bukkit.getServer().getPluginManager().getPermission(permission);

            if (perm != null) {
                return perm.getDefault().getValue(isOp());
            } else {
                return Permission.DEFAULT_PERMISSION.getValue(isOp());
            }
        } else
            return permissionsPlayer.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }

        return hasPermission(permission.getName());
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        if (name == null) {
            throw new IllegalArgumentException("Permission name cannot be null");
        } else if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        } else if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }

        PermissionAttachment result = addAttachment(plugin);
        result.setPermission(name, value);

        recalculatePermissions();

        return result;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        } else if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }

        PermissionAttachment result = new PermissionAttachment(plugin, parent);

        ppAttachments.add(result);
        recalculatePermissions();

        return result;
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment cannot be null");
        }

        if (ppAttachments.contains(attachment)) {
            ppAttachments.remove(attachment);
            PermissionRemovedExecutor ex = attachment.getRemovalCallback();

            if (ex != null) {
                ex.attachmentRemoved(attachment);
            }

            recalculatePermissions();
        } else {
            throw new IllegalArgumentException("Given attachment is not part of Permissible object " + parent);
        }
    }

    @Override
    public void recalculatePermissions() {
        if (ppAttachments == null || permissionsPlayer == null) {
            return;
        }

        temporaryPrePermissions.clear();
        temporaryPostPermissions.clear();

        Set<Permission> defaults = Bukkit.getServer().getPluginManager().getDefaultPermissions(isOp());
        Bukkit.getServer().getPluginManager().subscribeToDefaultPerms(isOp(), parent);

        for (Permission perm : defaults) {
            String name = perm.getName().toLowerCase();
            temporaryPrePermissions.add(name);
            Bukkit.getServer().getPluginManager().subscribeToPermission(name, parent);
            calculatePreChildPermissions(perm.getChildren(), false);
        }

        for (PermissionAttachment attachment : ppAttachments) {
            calculatePostChildPermissions(attachment.getPermissions(), false);
        }

        permissionsPlayer.setTemporaryPrePermissions(temporaryPrePermissions);
        permissionsPlayer.setTemporaryPostPermissions(temporaryPostPermissions);
    }

    private void calculatePreChildPermissions(Map<String, Boolean> children, boolean invert) {
        Set<String> keys = children.keySet();
        if (keys.size() > 0) {
            for (String name : keys) {
                Permission perm = Bukkit.getServer().getPluginManager().getPermission(name);
                boolean value = children.get(name) ^ invert;
                String lname = name.toLowerCase();

                if (value == true)
                    temporaryPrePermissions.add(lname);
                else if (value == false)
                    temporaryPrePermissions.add("-" + lname);

                Bukkit.getServer().getPluginManager().subscribeToPermission(name, parent);

                if (perm != null) {
                    calculatePreChildPermissions(perm.getChildren(), !value);
                }
            }
        }
    }
    
    private void calculatePostChildPermissions(Map<String, Boolean> children, boolean invert) {
        Set<String> keys = children.keySet();
        if (keys.size() > 0) {
            for (String name : keys) {
                Permission perm = Bukkit.getServer().getPluginManager().getPermission(name);
                boolean value = children.get(name) ^ invert;
                String lname = name.toLowerCase();

                if (value == true)
                    temporaryPostPermissions.add(lname);
                else if (value == false)
                    temporaryPostPermissions.add("-" + lname);

                Bukkit.getServer().getPluginManager().subscribeToPermission(name, parent);

                if (perm != null) {
                    calculatePostChildPermissions(perm.getChildren(), !value);
                }
            }
        }
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        if (name == null) {
            throw new IllegalArgumentException("Permission name cannot be null");
        } else if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        } else if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }

        PermissionAttachment result = addAttachment(plugin, ticks);

        if (result != null) {
            result.setPermission(name, value);
        }

        return result;
    }

    // Functions for timed attachments

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        } else if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }

        PermissionAttachment result = addAttachment(plugin);

        if (Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new RemoveAttachmentRunnable(result), ticks) == -1) {
            Bukkit.getServer().getLogger().log(Level.WARNING, "Could not add PermissionAttachment to " + parent + " for plugin " + plugin.getDescription().getFullName() + ": Scheduler returned -1");
            result.remove();
            return null;
        } else {
            return result;
        }
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        Set<PermissionAttachmentInfo> output = new HashSet<PermissionAttachmentInfo>();
        for (String permission : permissionsPlayer.getPermissionsInEffect()) {
            if (permission.startsWith("-"))
                output.add(new PermissionAttachmentInfo(this, permission.substring(1), null, false));
            else
                output.add(new PermissionAttachmentInfo(this, permission, null, true));
        }
        return output;
    }

    private class RemoveAttachmentRunnable implements Runnable {
        private PermissionAttachment attachment;

        public RemoveAttachmentRunnable(PermissionAttachment attachment) {
            this.attachment = attachment;
        }

        public void run() {
            attachment.remove();
        }
    }
}
