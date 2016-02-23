package com.github.cheesesoftware.PowerfulPerms.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.github.cheesesoftware.PowerfulPerms.database.DBResult;
import com.github.cheesesoftware.PowerfulPerms.database.DBRunnable;
import com.github.cheesesoftware.PowerfulPerms.database.Database;
import com.github.cheesesoftware.PowerfulPermsAPI.CachedGroup;
import com.github.cheesesoftware.PowerfulPermsAPI.DBDocument;
import com.github.cheesesoftware.PowerfulPermsAPI.Group;
import com.github.cheesesoftware.PowerfulPermsAPI.IScheduler;
import com.github.cheesesoftware.PowerfulPermsAPI.Permission;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionManager;
import com.github.cheesesoftware.PowerfulPermsAPI.PermissionPlayer;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;
import com.github.cheesesoftware.PowerfulPermsAPI.ResponseRunnable;
import com.github.cheesesoftware.PowerfulPermsAPI.ResultRunnable;
import com.github.cheesesoftware.PowerfulPermsAPI.ServerMode;
import com.google.common.base.Charsets;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

public abstract class PermissionManagerBase implements PermissionManager {
    protected HashMap<UUID, PermissionPlayer> players = new HashMap<UUID, PermissionPlayer>();
    protected ConcurrentHashMap<UUID, CachedPlayer> cachedPlayers = new ConcurrentHashMap<UUID, CachedPlayer>();
    protected HashMap<Integer, Group> groups = new HashMap<Integer, Group>();

    protected JedisPool pool;
    protected JedisPubSub subscriber;

    private final Database db;
    protected PowerfulPermsPlugin plugin;

    public static boolean redis;
    public static String redis_ip;
    public static int redis_port;
    public static String redis_password;

    public static String serverName;
    public static String consolePrefix = "[PowerfulPerms] ";
    public static String pluginPrefixShort = ChatColor.WHITE + "[" + ChatColor.BLUE + "PP" + ChatColor.WHITE + "] ";
    public static String redisMessage = "Unable to connect to Redis server. Check your credentials in the config file. If you don't use Redis, this message is perfectly fine.";

    public PermissionManagerBase(Database database, PowerfulPermsPlugin plugin, String serverName) {
        this.db = database;
        this.plugin = plugin;

        PermissionManagerBase.serverName = serverName;

        final PowerfulPermsPlugin tempPlugin = plugin;

        // Create table Groups, add group Guest
        db.tableExists(Database.tblGroups, new DBRunnable(true) {

            @Override
            public void run() {
                if (!result.booleanValue()) {
                    db.createGroupsTable(new DBRunnable(true) {

                        @Override
                        public void run() {
                            if (result.booleanValue())
                                tempPlugin.getLogger().info("Created table \"" + Database.tblGroups + "\"");
                            else
                                tempPlugin.getLogger().info("Could not create table \"" + Database.tblGroups + "\"");
                        }
                    });
                }
            }
        });

        // Create table Players
        db.tableExists(Database.tblPlayers, new DBRunnable(true) {

            @Override
            public void run() {
                if (!result.booleanValue()) {
                    db.createPlayersTable(new DBRunnable(true) {

                        @Override
                        public void run() {
                            if (result.booleanValue())
                                tempPlugin.getLogger().info("Created table \"" + Database.tblPlayers + "\"");
                            else
                                tempPlugin.getLogger().info("Could not create table \"" + Database.tblPlayers + "\"");
                        }
                    });
                }
            }
        });

        // Create table Permissions
        db.tableExists(Database.tblPermissions, new DBRunnable(true) {

            @Override
            public void run() {
                if (!result.booleanValue()) {
                    db.createPermissionsTable(new DBRunnable(true) {

                        @Override
                        public void run() {
                            if (result.booleanValue())
                                tempPlugin.getLogger().info("Created table \"" + Database.tblPermissions + "\"");
                            else
                                tempPlugin.getLogger().info("Could not create table \"" + Database.tblPermissions + "\"");
                        }
                    });
                }
            }
        });
        
        db.applyPatches(this);

        // Initialize Redis
        if (redis_password == null || redis_password.isEmpty())
            pool = new JedisPool(new GenericObjectPoolConfig(), redis_ip, redis_port, 0);
        else
            pool = new JedisPool(new GenericObjectPoolConfig(), redis_ip, redis_port, 0, redis_password);
    }

    protected void debug(String msg) {
        plugin.debug(msg);
    }

    @Override
    public void getConvertUUID(final String playerName, final ResultRunnable<UUID> resultRunnable) {
        if (playerName.equalsIgnoreCase("[default]")) {
            resultRunnable.setResult(java.util.UUID.nameUUIDFromBytes(("[default]").getBytes(Charsets.UTF_8)));
            db.scheduler.runSync(resultRunnable);
            return;
        }

        // If player is online, get UUID directly
        if (plugin.isPlayerOnline(playerName)) {
            resultRunnable.setResult(plugin.getPlayerUUID(playerName));
            db.scheduler.runSync(resultRunnable);
            return;
        }

        // Check if DB contains online uuid. If so, return it.
        // Check if DB contains offline uuid. If so, return it. If not, return online uuid.
        if (plugin.getServerMode() == ServerMode.MIXED) {
            // Generate offline UUID and check database if it exists. If so, return it.

            db.scheduler.runAsync(new Runnable() {

                @Override
                public void run() {
                    final UUID offlineuuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(Charsets.UTF_8));
                    debug("Generated mixed mode offline UUID " + offlineuuid);

                    // Get online UUID.

                    debug("Begin UUID retrieval...");
                    ArrayList<String> list = new ArrayList<String>();
                    list.add(playerName);
                    UUIDFetcher fetcher = new UUIDFetcher(list);
                    try {
                        Map<String, UUID> result = fetcher.call();
                        if (result != null && result.containsKey(playerName)) {
                            final UUID onlineuuid = result.get(playerName);
                            debug("Retrieved UUID " + onlineuuid);
                            // Check if database contains online UUID.

                            db.getPlayer(onlineuuid, new DBRunnable() {
                                @Override
                                public void run() {
                                    if (result.hasNext()) {
                                        // Database contains online UUID. Return it.
                                        debug("online UUID found in DB");
                                        resultRunnable.setResult(onlineuuid);
                                        db.scheduler.runSync(resultRunnable);
                                    } else {
                                        // Could not find online UUID in database.
                                        // Check if offline UUID exists.
                                        debug("online UUID not found in DB");
                                        db.getPlayer(offlineuuid, new DBRunnable() {

                                            @Override
                                            public void run() {
                                                if (result.hasNext()) {
                                                    // Found offline UUID in database. Return it.
                                                    debug("offline UUID found in DB, return offline");
                                                    resultRunnable.setResult(offlineuuid);
                                                } else {
                                                    // Could not find neither of offline or online UUIDs in database.
                                                    // Online UUID exists for player name so return it.
                                                    debug("offline UUID not found in DB, return online");
                                                    resultRunnable.setResult(onlineuuid);
                                                }
                                                db.scheduler.runSync(resultRunnable);
                                            }
                                        });
                                    }

                                }
                            });
                        } else {
                            // Could not find online UUID for specified name
                            debug("Did not find online UUID for player name " + playerName + ", return offline");
                            resultRunnable.setResult(offlineuuid);
                            db.scheduler.runSync(resultRunnable);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }, false);

        } else {
            if (plugin.getServerMode() == ServerMode.ONLINE) {
                // Convert player name to UUID using Mojang API
                db.scheduler.runAsync(new Runnable() {

                    @Override
                    public void run() {
                        debug("Begin UUID retrieval...");
                        ArrayList<String> list = new ArrayList<String>();
                        list.add(playerName);
                        UUIDFetcher fetcher = new UUIDFetcher(list);
                        try {
                            Map<String, UUID> result = fetcher.call();
                            if (result != null && result.containsKey(playerName)) {
                                UUID uuid = result.get(playerName);
                                debug("Retrieved UUID " + uuid);
                                resultRunnable.setResult(uuid);
                            } else
                                resultRunnable.setResult(null);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        db.scheduler.runSync(resultRunnable);
                    }
                }, false);
            } else {
                // Generate UUID from player name
                UUID uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(Charsets.UTF_8));
                resultRunnable.setResult(uuid);
                debug("Generated offline mode UUID " + uuid);
                db.scheduler.runSync(resultRunnable);
            }
        }

    }

    @Override
    public IScheduler getScheduler() {
        return db.scheduler;
    }

    @Override
    public void createPlayer(final String name, final UUID uuid, final ResponseRunnable response) {
        db.scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                loadPlayer(uuid, name, true);
                response.setResponse(true, "Player created.");
                db.scheduler.runSync(response);
            }
        }, false);

    }

    @Override
    public void notifyReloadGroups() {
        if (redis) {
            plugin.runTaskAsynchronously(new Runnable() {
                @SuppressWarnings("deprecation")
                public void run() {
                    try {
                        Jedis jedis = pool.getResource();
                        try {
                            jedis.publish("PowerfulPerms", "[groups]" + " " + serverName);
                        } catch (Exception e) {
                            pool.returnBrokenResource(jedis);
                        }
                        pool.returnResource(jedis);
                    } catch (Exception e) {
                        plugin.getLogger().warning(redisMessage);
                    }
                }
            });
        }
    }

    @Override
    public void notifyReloadPlayers() {
        if (redis) {
            plugin.runTaskAsynchronously(new Runnable() {
                @SuppressWarnings("deprecation")
                public void run() {
                    try {
                        Jedis jedis = pool.getResource();
                        try {
                            jedis.publish("PowerfulPerms", "[players]" + " " + serverName);
                        } catch (Exception e) {
                            pool.returnBrokenResource(jedis);
                        }
                        pool.returnResource(jedis);

                    } catch (Exception e) {
                        plugin.getLogger().warning(redisMessage);
                    }
                }
            });
        }
    }

    @Override
    public void notifyReloadPlayer(final UUID uuid) {
        if (redis) {
            plugin.runTaskAsynchronously(new Runnable() {
                @SuppressWarnings("deprecation")
                public void run() {
                    try {
                        Jedis jedis = pool.getResource();
                        try {
                            jedis.publish("PowerfulPerms", uuid + " " + serverName);
                        } catch (Exception e) {
                            pool.returnBrokenResource(jedis);
                        }
                        pool.returnResource(jedis);
                    } catch (Exception e) {
                        plugin.getLogger().warning(redisMessage);
                    }
                }
            });
        }
    }

    @Override
    public void reloadPlayers() {
        for (UUID uuid : players.keySet()) {
            if (plugin.isPlayerOnline(uuid)) {
                players.remove(uuid);
            }
            debug("Reloading player " + uuid.toString());
            loadPlayer(uuid, null, false);
        }
    }

    @Override
    public void reloadPlayer(UUID uuid) {
        if (plugin.isPlayerOnline(uuid)) {
            String name = plugin.getPlayerName(uuid);
            if (name != null) {
                this.loadPlayer(uuid, name, false);
            }
        }
    }

    @Override
    public void reloadPlayer(String name) {
        if (plugin.isPlayerOnline(name)) {
            UUID uuid = plugin.getPlayerUUID(name);
            if (uuid != null) {
                this.loadPlayer(uuid, name, false);
            }
        }
    }

    /**
     * Returns the PermissionsPlayer-object for the specified player, used for getting permissions information about the player. Player has to be online.
     */
    @Override
    public PermissionPlayer getPermissionsPlayer(UUID uuid) {
        return players.get(uuid);
    }

    /**
     * Returns the PermissionsPlayer-object for the specified player, used for getting permissions information about the player. Player has to be online.
     */
    @Override
    public PermissionPlayer getPermissionsPlayer(String name) {
        UUID uuid = plugin.getPlayerUUID(name);
        return players.get(uuid);
    }

    protected void loadPlayer(final UUID uuid, final String name, final boolean login) {
        debug("loadPlayer begin");

        db.getPlayer(uuid, new DBRunnable(login) {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    final DBDocument row = result.next();
                    if (row != null) {
                        // The player exists in database.

                        String playerName_loaded = row.getString("name");
                        debug("playername_loaded " + playerName_loaded);

                        if (name != null) {
                            debug("playerName " + name);

                            // Check if name mismatch, update player name
                            if (!playerName_loaded.equals(name)) {
                                debug("PLAYER NAME MISMATCH.");
                                db.setPlayerName(uuid, name, new DBRunnable(login) {

                                    @Override
                                    public void run() {
                                        debug("PLAYER NAME UPDATED. NAMECHANGE");
                                        db.updatePlayerPermissions(uuid, name, new DBRunnable(login) {

                                            @Override
                                            public void run() {
                                                debug("UPDATED PLAYER PERMISSIONS");
                                                loadPlayerFinished(row, login, uuid);
                                            }
                                        });
                                    }
                                });
                            } else
                                loadPlayerFinished(row, login, uuid);
                        } else
                            loadPlayerFinished(row, login, uuid);
                    } else {
                        // Could not find player with UUID. Create new player.
                        db.getPlayers("[default]", new DBRunnable(login) {

                            @Override
                            public void run() {
                                final DBDocument row = result.next();
                                if (row != null) {

                                    db.insertPlayer(uuid, name, row.getString("groups"), row.getString("prefix"), row.getString("suffix"), new DBRunnable(login) {

                                        @Override
                                        public void run() {
                                            debug("NEW PLAYER CREATED");
                                            loadPlayerFinished(row, login, uuid);
                                        }
                                    });
                                } else
                                    plugin.getLogger().severe(consolePrefix + "Can not get data from user [default]. Please create the default user.");
                            }
                        });
                    }
                }
            }
        });
    }

    protected void loadPlayerFinished(DBDocument row, final boolean login, final UUID uuid) {
        debug("loadPlayerFinished begin");
        final String groups_loaded = (row != null ? row.getString("groups") : "");
        final String prefix_loaded = (row != null ? row.getString("prefix") : "");
        final String suffix_loaded = (row != null ? row.getString("suffix") : "");

        loadPlayerPermissions(uuid, new ResultRunnable<List<Permission>>(login) {

            @Override
            public void run() {
                debug("loadPlayerFinished runnable begin");
                List<Permission> perms;
                if (result != null) {
                    perms = result;
                } else
                    perms = new ArrayList<Permission>();

                if (login) {
                    debug("Inserted into cachedPlayers allowing playerjoin to finish");
                    cachedPlayers.put(uuid, new CachedPlayer(groups_loaded, prefix_loaded, suffix_loaded, perms));

                } else {
                    // Player should be reloaded if "login" is false. Reload already loaded player.
                    if (plugin.isPlayerOnline(uuid) && players.containsKey(uuid)) {
                        PermissionPlayerBase toUpdate = (PermissionPlayerBase) players.get(uuid);
                        PermissionPlayerBase base = new PermissionPlayerBase(getPlayerGroups(groups_loaded), perms, prefix_loaded, suffix_loaded, plugin);
                        toUpdate.update(base);

                        if (cachedPlayers.get(uuid) != null)
                            cachedPlayers.remove(uuid);
                    }
                }
                debug("loadPlayerFinished runnable end");
            }
        });

    }

    protected PermissionPlayerBase loadCachedPlayer(UUID uuid) {
        debug("continueLoadPlayer " + uuid);
        CachedPlayer cachedPlayer = cachedPlayers.get(uuid);
        if (cachedPlayer == null) {
            plugin.getLogger().severe(consolePrefix + "Could not continue load player. Cached player is null.");
            return null;
        }

        if (players.containsKey(uuid)) {
            players.remove(uuid);
        }

        PermissionPlayerBase base = new PermissionPlayerBase(this.getPlayerGroups(cachedPlayer.getGroups()), cachedPlayer.getPermissions(), cachedPlayer.getPrefix(), cachedPlayer.getSuffix(), plugin);

        cachedPlayers.remove(uuid);
        return base;
    }

    public void onDisable() {
        if (subscriber != null)
            subscriber.unsubscribe();
        if (pool != null)
            pool.destroy();
        if (groups != null)
            groups.clear();
        if (players != null)
            players.clear();
        if (cachedPlayers != null)
            cachedPlayers.clear();
    }

    @Override
    public void reloadGroups() {
        groups.clear();
        loadGroups();
    }

    /**
     * Loads groups from MySQL, removes old group data. Will reload all players too.
     */
    protected void loadGroups() {
        loadGroups(false, false);
    }

    /**
     * Loads groups from MySQL, removes old group data. Will reload all players too. beginSameThread: Set this to true if you want it to fetch group data on the same thread you call from. Set it to
     * false and it will run asynchronously. endSameThread: Set this to true if you want to finish and insert groups on the same thread. Note: This -MUST- be Bukkit main thread you execute on. Set to
     * false if you want to run it synchronously but scheduled.
     */
    protected void loadGroups(boolean beginSameThread, final boolean endSameThread) {
        debug("loadGroups begin");
        groups.clear();

        db.getGroups(new DBRunnable(beginSameThread) {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    HashMap<Integer, String> tempParents = new HashMap<Integer, String>();
                    while (result.hasNext()) {
                        DBDocument row = result.next();
                        final int groupId = row.getInt("id");
                        final String name = row.getString("name");
                        String parents = row.getString("parents");
                        final String prefix = row.getString("prefix");
                        final String suffix = row.getString("suffix");
                        final String ladder = row.getString("ladder");
                        final int rank = row.getInt("rank");

                        tempParents.put(groupId, parents);

                        db.getGroupPermissions(name, new DBRunnable(true) {

                            @Override
                            public void run() {
                                PowerfulGroup group = new PowerfulGroup(groupId, name, loadGroupPermissions(result), prefix, suffix, ladder, rank);
                                groups.put(groupId, group);
                            }
                        });

                    }

                    final HashMap<Integer, String> tempParentsFinal = tempParents;

                    // Make sure to run on Bukkit main thread when altering the groups
                    db.scheduler.runSync(new Runnable() {

                        @Override
                        public void run() {
                            Iterator<Entry<Integer, String>> it = tempParentsFinal.entrySet().iterator();
                            while (it.hasNext()) {
                                Entry<Integer, String> e = it.next();
                                // debug("Adding parents to group with ID " + e.getKey());// + " and name " + groups.get(e.getKey()).getName());
                                ArrayList<Group> finalGroups = new ArrayList<Group>();
                                ArrayList<String> rawParents = getGroupParents(e.getValue());
                                for (String s : rawParents) {
                                    for (Group testGroup : groups.values()) {
                                        // debug("Comparing " + s + " with " + testGroup.getId());
                                        if (!s.isEmpty() && Integer.parseInt(s) == testGroup.getId()) {
                                            finalGroups.add(testGroup);
                                            // debug("Added parent ID " + testGroup.getId() + " to group with ID " + e.getKey());
                                            // debug("Added parent " + testGroup.getName() + " to " + groups.get(e.getKey()).getName());
                                            break;
                                        }
                                    }
                                }
                                Group temp = groups.get(e.getKey());
                                if (temp != null)
                                    temp.setParents(finalGroups);
                                else
                                    debug("Group with ID " + e.getKey() + " was null");
                            }

                            // Reload players too.
                            Set<UUID> keysCopy = new HashSet<UUID>(players.keySet());
                            for (UUID uuid : keysCopy) {
                                if (plugin.isPlayerOnline(uuid))
                                    reloadPlayer(uuid);
                            }
                            debug("loadGroups end");
                        }
                    }, endSameThread);
                }
            }
        });
    }

    protected ArrayList<PowerfulPermission> loadGroupPermissions(DBResult result) {
        ArrayList<PowerfulPermission> perms = new ArrayList<PowerfulPermission>();
        while (result.hasNext()) {
            DBDocument row = result.next();
            PowerfulPermission tempPerm = new PowerfulPermission(row.getString("permission"), row.getString("world"), row.getString("server"));
            perms.add(tempPerm);
        }
        return perms;
    }

    protected ArrayList<String> getGroupParents(String parentsString) {
        ArrayList<String> parents = new ArrayList<String>();
        if (parentsString.contains(";")) {
            for (String s : parentsString.split(";")) {
                parents.add(s);
            }
        } else
            parents.add(parentsString);
        return parents;
    }

    @Override
    public Group getGroup(String groupName) {
        for (Map.Entry<Integer, Group> e : groups.entrySet())
            if (e.getValue().getName().equalsIgnoreCase(groupName))
                return e.getValue();
        return null;
    }

    @Override
    public Group getGroup(int id) {
        return groups.get(id);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<Integer, Group> getGroups() {
        return (Map<Integer, Group>) this.groups.clone();
    }

    protected HashMap<String, List<CachedGroup>> getPlayerGroups(String raw) {
        HashMap<String, List<CachedGroup>> tempGroups = new HashMap<String, List<CachedGroup>>();
        for (String s : raw.split(";")) {
            // Each group entry
            String[] split = s.split(":");
            if (split.length >= 2) {
                String server = split[0];

                // If list null, initialize list
                List<CachedGroup> input = tempGroups.get(server);
                if (input == null)
                    input = new ArrayList<CachedGroup>();

                boolean negated = split[1].startsWith("-");
                if (negated)
                    split[1] = split[1].substring(1);

                int groupId = Integer.parseInt(split[1]);

                /*-boolean primary = false;
                boolean secondary = false;
                if (split.length >= 3) {
                    if (split[2].equals("p"))
                        primary = true;
                    else if (split[2].equals("s"))
                        secondary = true;
                }*/

                debug("add group:" + groupId + " negated:" + negated);
                input.add(new CachedGroup(groups.get(groupId), negated));
                tempGroups.put(server, input);
            } else {
                if (!s.isEmpty()) {
                    // If list null, initialize list
                    List<CachedGroup> input = tempGroups.get("");
                    if (input == null)
                        input = new ArrayList<CachedGroup>();

                    input.add(new CachedGroup(groups.get(Integer.parseInt(s)), false));
                    tempGroups.put("", input);
                    debug(s + " old ");
                }
            }
        }
        return tempGroups;
    }

    protected HashMap<String, List<CachedGroupRaw>> getPlayerGroupsRaw(String raw) {
        HashMap<String, List<CachedGroupRaw>> tempGroups = new HashMap<String, List<CachedGroupRaw>>();
        for (String s : raw.split(";")) {
            // Each group entry
            String[] split = s.split(":");
            if (split.length >= 2) {
                String server = split[0];

                // If list null, initialize list
                List<CachedGroupRaw> input = tempGroups.get(server);
                if (input == null)
                    input = new ArrayList<CachedGroupRaw>();

                boolean negated = split[1].startsWith("-");
                if (negated)
                    split[1] = split[1].substring(1);

                int groupId = Integer.parseInt(split[1]);

                /*-boolean primary = false;
                boolean secondary = false;
                if (split.length >= 3) {
                    if (split[2].equals("p"))
                        primary = true;
                    else if (split[2].equals("s"))
                        secondary = true;
                }*/

                debug("add group:" + groupId + " negated:" + negated);
                input.add(new CachedGroupRaw(groupId, negated));
                tempGroups.put(server, input);
            } else {
                if (!s.isEmpty()) {
                    // If list null, initialize list
                    List<CachedGroupRaw> input = tempGroups.get("");
                    if (input == null)
                        input = new ArrayList<CachedGroupRaw>();

                    input.add(new CachedGroupRaw(Integer.parseInt(s), false));
                    tempGroups.put("", input);
                    debug(s + " old ");
                }
            }
        }
        return tempGroups;
    }

    public static String getPlayerGroupsRaw(HashMap<String, List<CachedGroupRaw>> input) {
        String output = "";
        for (Entry<String, List<CachedGroupRaw>> entry : input.entrySet()) {
            for (CachedGroupRaw cachedGroup : entry.getValue()) {
                output += entry.getKey() + ":" + (cachedGroup.isNegated() ? "-" : "") + cachedGroup.getGroupId() + ":" /* old primary/secondary here */+ ";";
            }
        }
        return output;
    }

    @Override
    public void getPlayerGroups(UUID uuid, final ResultRunnable<Map<String, List<CachedGroup>>> resultRunnable) {
        // If player is online, get data directly from player
        PermissionPlayer gp = (PermissionPlayer) players.get(uuid);
        if (gp != null) {
            resultRunnable.setResult(gp.getCachedGroups());
            db.scheduler.runSync(resultRunnable);
            return;
        }

        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    HashMap<String, List<CachedGroup>> output = getPlayerGroups(row.getString("groups"));
                    resultRunnable.setResult(output);
                }
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    @Override
    public void getPlayerData(UUID uuid, final ResultRunnable<DBDocument> resultRunnable) {
        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    resultRunnable.setResult(row);
                }
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    /**
     * Gets a map containing all the permissions a player has. If player is not online data will be loaded from DB.
     */
    @Override
    public void getPlayerOwnPermissions(final UUID uuid, final ResultRunnable<List<Permission>> resultRunnable) {
        // If player is online, get data directly from player
        PermissionPlayer gp = (PermissionPlayer) players.get(uuid);
        if (gp != null) {
            resultRunnable.setResult(gp.getPermissions());
            db.scheduler.runSync(resultRunnable);
            return;
        }

        loadPlayerPermissions(uuid, new ResultRunnable<List<Permission>>() {

            @Override
            public void run() {
                final List<Permission> perms;
                if (result != null)
                    perms = result;
                else
                    perms = new ArrayList<Permission>();
                resultRunnable.setResult(perms);
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    protected void loadPlayerPermissions(UUID uuid, final ResultRunnable<List<Permission>> resultRunnable) {
        db.getPlayerPermissions(uuid, new DBRunnable(resultRunnable.sameThread()) {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    ArrayList<Permission> perms = new ArrayList<Permission>();
                    while (result.hasNext()) {
                        DBDocument row = result.next();
                        Permission tempPerm = new PowerfulPermission(row.getString("permission"), row.getString("world"), row.getString("server"));
                        perms.add(tempPerm);
                        resultRunnable.setResult(perms);
                    }
                    db.scheduler.runSync(resultRunnable, resultRunnable.sameThread());
                }
            }
        });
    }

    /**
     * Gets the prefix of a player. If player isn't online it retrieves data from database.
     */
    @Override
    public void getPlayerPrefix(UUID uuid, final ResultRunnable<String> resultRunnable) {
        // If player is online, get data directly from player
        PermissionPlayer gp = (PermissionPlayer) players.get(uuid);
        if (gp != null) {
            resultRunnable.setResult(gp.getPrefix());
            db.scheduler.runSync(resultRunnable);
            return;
        }

        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    resultRunnable.setResult(row.getString("prefix"));
                }
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    /**
     * Gets the suffix of a player. If player isn't online it retrieves data from database.
     */
    @Override
    public void getPlayerSuffix(UUID uuid, final ResultRunnable<String> resultRunnable) {
        // If player is online, get data directly from player
        PermissionPlayer gp = (PermissionPlayer) players.get(uuid);
        if (gp != null) {
            resultRunnable.setResult(gp.getSuffix());
            db.scheduler.runSync(resultRunnable);
            return;
        }

        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    resultRunnable.setResult(row.getString("suffix"));
                }
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    /**
     * Gets the own prefix of a player. If player isn't online it retrieves data from database.
     */
    @Override
    public void getPlayerOwnPrefix(UUID uuid, final ResultRunnable<String> resultRunnable) {
        // If player is online, get data directly from player
        PermissionPlayer gp = (PermissionPlayer) players.get(uuid);
        if (gp != null) {
            resultRunnable.setResult(gp.getOwnPrefix());
            db.scheduler.runSync(resultRunnable);
            return;
        }
        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    resultRunnable.setResult(row.getString("prefix"));
                }
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    /**
     * Gets the own suffix of a player. If player isn't online it retrieves data from database.
     */
    @Override
    public void getPlayerOwnSuffix(UUID uuid, final ResultRunnable<String> resultRunnable) {
        // If player is online, get data directly from player
        PermissionPlayer gp = (PermissionPlayer) players.get(uuid);
        if (gp != null) {
            resultRunnable.setResult(gp.getOwnSuffix());
            db.scheduler.runSync(resultRunnable);
            return;
        }

        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    resultRunnable.setResult(row.getString("suffix"));
                }
                db.scheduler.runSync(resultRunnable);
            }
        });
    }

    @Override
    public String getGroupPrefix(String groupName, String server) {
        Group g = getGroup(groupName);
        if (g != null)
            return g.getPrefix(server);
        return null;
    }

    @Override
    public String getGroupSuffix(String groupName, String server) {
        Group g = getGroup(groupName);
        if (g != null)
            return g.getSuffix(server);
        return null;
    }

    @Override
    public HashMap<String, String> getGroupServerPrefix(String groupName) {
        Group g = getGroup(groupName);
        if (g != null)
            return g.getPrefixes();
        return null;
    }

    @Override
    public HashMap<String, String> getGroupServerSuffix(String groupName) {
        Group g = getGroup(groupName);
        if (g != null)
            return g.getSuffixes();
        return null;
    }

    // -------------------------------------------------------------------//
    // //
    // ------------PLAYER PERMISSION MODIFYING FUNCTIONS BELOW------------//
    // //
    // -------------------------------------------------------------------//

    @Override
    public void addPlayerPermission(UUID uuid, final String playerName, String permission, ResponseRunnable response) {
        addPlayerPermission(uuid, permission, playerName, "", "", response);
    }

    @Override
    public void addPlayerPermission(final UUID uuid, final String playerName, final String permission, final String world, final String server, final ResponseRunnable response) {

        if (playerName.equalsIgnoreCase("[default]")) {
            response.setResponse(false, "You can not add permissions to the default player. Add them to a group instead and add the group to the default player.");
            db.scheduler.runSync(response);
            return;
        }

        // Check if the same permission already exists.
        db.playerHasPermission(uuid, permission, world, server, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(false, "Player already has the specified permission.");
                    db.scheduler.runSync(response);
                } else {
                    db.getPlayer(uuid, new DBRunnable() {

                        @Override
                        public void run() {
                            if (result.hasNext()) {
                                final UUID uuid = UUID.fromString(result.next().getString("uuid"));
                                if (uuid != null) {
                                    db.insertPermission(uuid, playerName, "", permission, world, server, new DBRunnable() {

                                        @Override
                                        public void run() {
                                            if (result.booleanValue()) {
                                                response.setResponse(true, "Permission added to player.");
                                                reloadPlayer(uuid);
                                                notifyReloadPlayer(uuid);
                                            } else
                                                response.setResponse(false, "Could not add permission. Check console for any errors.");
                                            db.scheduler.runSync(response);
                                        }
                                    });
                                } else {
                                    response.setResponse(false, "Could not add permission. Player's UUID is invalid.");
                                    db.scheduler.runSync(response);
                                }
                            } else {
                                response.setResponse(false, "Could not add permission. Player doesn't exist.");
                                db.scheduler.runSync(response);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void removePlayerPermission(UUID uuid, String permission, ResponseRunnable response) {
        removePlayerPermission(uuid, permission, "", "", response);
    }

    @Override
    public void removePlayerPermission(final UUID uuid, String permission, String world, String server, final ResponseRunnable response) {
        db.deletePlayerPermission(uuid, permission, world, server, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    int amount = result.rowsChanged();
                    response.setResponse(true, "Removed " + amount + " permissions from the player.");
                    reloadPlayer(uuid);
                    notifyReloadPlayer(uuid);
                } else
                    response.setResponse(false, "Player does not have the specified permission.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void removePlayerPermissions(final UUID uuid, final ResponseRunnable response) {

        db.deletePlayerPermissions(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    int amount = result.rowsChanged();
                    response.setResponse(true, "Removed " + amount + " permissions from the player.");
                    reloadPlayer(uuid);
                    notifyReloadPlayer(uuid);
                } else
                    response.setResponse(false, "Player does not have any permissions.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void setPlayerPrefix(final UUID uuid, String prefix, final ResponseRunnable response) {
        db.setPlayerPrefix(uuid, prefix, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(true, "Player prefix set.");
                    reloadPlayer(uuid);
                    notifyReloadPlayer(uuid);
                } else
                    response.setResponse(false, "Could not set player prefix. Check console for errors.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void setPlayerSuffix(final UUID uuid, String suffix, final ResponseRunnable response) {
        db.setPlayerSuffix(uuid, suffix, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(true, "Player suffix set.");
                    reloadPlayer(uuid);
                    notifyReloadPlayer(uuid);
                } else
                    response.setResponse(false, "Could not set player suffix. Check console for errors.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void removePlayerGroup(UUID uuid, String groupName, ResponseRunnable response) {
        removePlayerGroup(uuid, groupName, "", false, response);
    }

    @Override
    public void removePlayerGroup(UUID uuid, String groupName, boolean negated, ResponseRunnable response) {
        removePlayerGroup(uuid, groupName, "", negated, response);
    }

    @Override
    public void removePlayerGroup(final UUID uuid, String groupName, String server, final boolean negated, final ResponseRunnable response) {
        if (server.equalsIgnoreCase("all"))
            server = "";

        final String serv = server;

        final int groupId_new;
        Group group = getGroup(groupName);
        if (group != null)
            groupId_new = group.getId();
        else {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
            return;
        }

        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    String playerGroupString = row.getString("groups");

                    boolean removed = false;
                    HashMap<String, List<CachedGroupRaw>> playerGroups = getPlayerGroupsRaw(playerGroupString);
                    List<CachedGroupRaw> groupList = playerGroups.get(serv);
                    if (groupList == null)
                        groupList = new ArrayList<CachedGroupRaw>();
                    Iterator<CachedGroupRaw> it = groupList.iterator();
                    while (it.hasNext()) {
                        CachedGroupRaw cachedGroup = it.next();
                        if (cachedGroup.getGroupId() == groupId_new && cachedGroup.isNegated() == negated) {
                            it.remove();
                            removed = true;
                        }
                    }

                    if (removed)
                        playerGroups.put(serv, groupList);
                    else {
                        response.setResponse(false, "Player does not have the specified group for the specified server.");
                        db.scheduler.runSync(response);
                        return;
                    }

                    String playerGroupStringOutput = getPlayerGroupsRaw(playerGroups);
                    db.setPlayerGroups(uuid, playerGroupStringOutput, new DBRunnable() {

                        @Override
                        public void run() {
                            if (result.booleanValue()) {
                                response.setResponse(true, "Player group removed.");
                                reloadPlayer(uuid);
                                notifyReloadPlayer(uuid);
                            } else
                                response.setResponse(false, "Could not remove player group. Check console for errors.");
                            db.scheduler.runSync(response);
                        }
                    });
                } else {
                    response.setResponse(false, "Player does not exist.");
                    db.scheduler.runSync(response);
                }
            }
        });
    }

    @Override
    public void addPlayerGroup(UUID uuid, String groupName, ResponseRunnable response) {
        addPlayerGroup(uuid, groupName, false, response);
    }

    @Override
    public void addPlayerGroup(UUID uuid, String groupName, final boolean negated, ResponseRunnable response) {
        addPlayerGroup(uuid, groupName, "", negated, response);
    }

    @Override
    public void addPlayerGroup(final UUID uuid, String groupName, String server, final boolean negated, final ResponseRunnable response) {
        if (server.equalsIgnoreCase("all"))
            server = "";

        final String serv = server;

        final Group group = getGroup(groupName);
        if (group == null) {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
            return;
        }

        db.getPlayer(uuid, new DBRunnable() {

            @Override
            public void run() {
                if (result.hasNext()) {
                    DBDocument row = result.next();
                    String playerGroupString = row.getString("groups");

                    // Add group. Put it first.
                    HashMap<String, List<CachedGroupRaw>> playerGroups = getPlayerGroupsRaw(playerGroupString);
                    List<CachedGroupRaw> groupList = playerGroups.get(serv);
                    if (groupList == null)
                        groupList = new ArrayList<CachedGroupRaw>();
                    Iterator<CachedGroupRaw> it = groupList.iterator();
                    while (it.hasNext()) {
                        CachedGroupRaw cachedGroup = it.next();
                        if (cachedGroup.getGroupId() == group.getId() && cachedGroup.isNegated() == negated) {
                            response.setResponse(false, "Player already has this group.");
                            db.scheduler.runSync(response);
                            return;
                        }
                    }

                    groupList.add(new CachedGroupRaw(group.getId(), negated));
                    playerGroups.put(serv, groupList);

                    String playerGroupStringOutput = getPlayerGroupsRaw(playerGroups);
                    db.setPlayerGroups(uuid, playerGroupStringOutput, new DBRunnable() {

                        @Override
                        public void run() {
                            if (result.booleanValue()) {
                                response.setResponse(true, "Player group added.");
                                reloadPlayer(uuid);
                                notifyReloadPlayer(uuid);
                            } else
                                response.setResponse(false, "Could not add player group. Check console for errors.");
                            db.scheduler.runSync(response);
                        }
                    });

                } else {
                    response.setResponse(false, "Player does not exist.");
                    db.scheduler.runSync(response);
                }
            }
        });

    }

    // -------------------------------------------------------------------//
    // //
    // ------------GROUP PERMISSION MODIFYING FUNCTIONS BELOW-------------//
    // //
    // -------------------------------------------------------------------//

    @Override
    public void createGroup(String name, String ladder, int rank, final ResponseRunnable response) {
        Iterator<Entry<Integer, Group>> it = this.groups.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Integer, Group> e = it.next();
            if (e.getValue().getName().equalsIgnoreCase(name)) {
                // Group already exists
                response.setResponse(false, "Group already exists.");
                db.scheduler.runSync(response);
                return;
            }
        }

        db.insertGroup(name, "", "", "", ladder, rank, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(true, "Created group.");
                    loadGroups();
                    notifyReloadGroups();
                } else
                    response.setResponse(false, "Group already exists.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void deleteGroup(String groupName, final ResponseRunnable response) {
        db.deleteGroup(groupName, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(true, "Deleted group.");
                    loadGroups();
                    notifyReloadGroups();
                } else
                    response.setResponse(false, "Group does not exist.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void addGroupPermission(String groupName, String permission, ResponseRunnable response) {
        addGroupPermission(groupName, permission, "", "", response);
    }

    @Override
    public void addGroupPermission(String groupName, String permission, String world, String server, final ResponseRunnable response) {
        Group group = getGroup(groupName);
        if (group != null) {
            List<Permission> groupPermissions = group.getOwnPermissions();

            PowerfulPermission sp = new PowerfulPermission(permission, world, server);

            for (Permission temp : groupPermissions) {
                if (temp.getPermissionString().equals(permission) && temp.getServer().equals(server) && temp.getWorld().equals(world)) {
                    response.setResponse(false, "Group already has the specified permission.");
                    db.scheduler.runSync(response);
                    return;
                }
            }

            groupPermissions.add(sp);

            db.insertPermission((UUID) null, "", groupName, permission, world, server, new DBRunnable() {

                @Override
                public void run() {
                    if (result.booleanValue()) {
                        response.setResponse(true, "Added permission to group.");
                        loadGroups();
                        notifyReloadGroups();
                    } else
                        response.setResponse(false, "Could not add permission to group. Check console for errors.");
                    db.scheduler.runSync(response);
                }
            });
        } else {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
        }
    }

    @Override
    public void removeGroupPermission(String groupName, String permission, ResponseRunnable response) {
        removeGroupPermission(groupName, permission, "", "", response);
    }

    @Override
    public void removeGroupPermission(String groupName, String permission, String world, String server, final ResponseRunnable response) {
        Group group = getGroup(groupName);
        if (group != null) {
            db.deleteGroupPermission(groupName, permission, world, server, new DBRunnable() {

                @Override
                public void run() {
                    if (result.booleanValue()) {
                        response.setResponse(true, "Removed " + result.rowsChanged() + " permissions from the group.");
                        loadGroups();
                        notifyReloadGroups();
                    } else
                        response.setResponse(false, "Group does not have the specified permission.");
                    db.scheduler.runSync(response);
                }
            });

        } else {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
        }
    }

    @Override
    public void removeGroupPermissions(String groupName, final ResponseRunnable response) {
        Group group = getGroup(groupName);
        if (group != null) {
            List<Permission> groupPermissions = group.getOwnPermissions();

            if (groupPermissions.size() <= 0) {
                response.setResponse(false, "Group does not have any permissions.");
                db.scheduler.runSync(response);
                return;
            }

            final Counter counter = new Counter();
            db.deleteGroupPermissions(groupName, new DBRunnable() {

                @Override
                public void run() {
                    counter.add(result.rowsChanged());
                }
            });

            response.setResponse(true, "Removed " + counter.amount() + " permissions from the group.");
            db.scheduler.runSync(response);
            loadGroups();
            notifyReloadGroups();
        } else {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
        }
    }

    @Override
    public void addGroupParent(String groupName, String parentGroupName, final ResponseRunnable response) {
        Group group = getGroup(groupName);
        if (group != null) {
            Group parentGroup = getGroup(parentGroupName);
            if (parentGroup != null) {
                String currentParents = PowerfulGroup.encodeParents(group.getParents());
                if (currentParents.contains(parentGroupName)) {
                    response.setResponse(false, "Group already has the specified parent.");
                    db.scheduler.runSync(response);
                    return;
                }
                currentParents += parentGroup.getId() + ";";

                db.setGroupParents(groupName, currentParents, new DBRunnable() {

                    @Override
                    public void run() {
                        if (result.booleanValue()) {
                            response.setResponse(true, "Added parent to group.");
                            loadGroups();
                            notifyReloadGroups();
                        } else
                            response.setResponse(false, "Could not add parent to group. Check console for errors.");
                        db.scheduler.runSync(response);
                    }
                });

            } else {
                response.setResponse(false, "Parent group does not exist.");
                db.scheduler.runSync(response);
            }
        } else {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
        }
    }

    @Override
    public void removeGroupParent(String groupName, String parentGroupName, final ResponseRunnable response) {
        Group group = getGroup(groupName);
        if (group != null) {
            Group parentGroup = getGroup(parentGroupName);
            if (parentGroup != null) {
                String currentParents = PowerfulGroup.encodeParents(group.getParents());
                String toRemove = parentGroup.getId() + ";";
                if (!currentParents.contains(toRemove)) {
                    response.setResponse(false, "Group does not have the specified parent.");
                    db.scheduler.runSync(response);
                    return;
                }
                currentParents = currentParents.replaceFirst(parentGroup.getId() + ";", "");

                db.setGroupParents(groupName, currentParents, new DBRunnable() {

                    @Override
                    public void run() {
                        if (result.booleanValue()) {
                            response.setResponse(true, "Removed parent from group.");
                            loadGroups();
                            notifyReloadGroups();
                        } else
                            response.setResponse(false, "Could not remove parent from group. Check console for errors.");
                        db.scheduler.runSync(response);
                    }
                });

            } else {
                response.setResponse(false, "Parent group does not exist.");
                db.scheduler.runSync(response);
            }
        } else {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
        }
    }

    @Override
    public void setGroupPrefix(String groupName, String prefix, final ResponseRunnable response) {
        setGroupPrefix(groupName, prefix, "", response);
    }

    @Override
    public void setGroupPrefix(String groupName, String prefix, String server, final ResponseRunnable response) {
        if (server.equalsIgnoreCase("all"))
            server = "";

        Group group = getGroup(groupName);
        if (group == null) {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
            return;
        }

        HashMap<String, String> currentPrefix = group.getPrefixes();
        if (prefix.isEmpty())
            currentPrefix.remove(server);
        else
            currentPrefix.put(server, prefix);
        final String output = PowerfulGroup.encodePrefixSuffix(currentPrefix);

        db.setGroupPrefix(groupName, output, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(true, "Group prefix set.");
                    loadGroups();
                    notifyReloadGroups();
                } else
                    response.setResponse(false, "Could set group prefix. Check console for errors.");
                db.scheduler.runSync(response);
            }
        });
    }

    @Override
    public void setGroupSuffix(String groupName, String suffix, final ResponseRunnable response) {
        setGroupSuffix(groupName, suffix, "", response);
    }

    @Override
    public void setGroupSuffix(String groupName, String suffix, String server, final ResponseRunnable response) {
        if (server.equalsIgnoreCase("all"))
            server = "";

        Group group = getGroup(groupName);
        if (group == null) {
            response.setResponse(false, "Group does not exist.");
            db.scheduler.runSync(response);
            return;
        }

        HashMap<String, String> currentSuffix = group.getSuffixes();
        if (suffix.isEmpty())
            currentSuffix.remove(server);
        else
            currentSuffix.put(server, suffix);
        final String output = PowerfulGroup.encodePrefixSuffix(currentSuffix);

        db.setGroupSuffix(groupName, output, new DBRunnable() {

            @Override
            public void run() {
                if (result.booleanValue()) {
                    response.setResponse(true, "Group suffix set.");
                    loadGroups();
                    notifyReloadGroups();
                } else
                    response.setResponse(false, "Could set group suffix. Check console for errors.");
                db.scheduler.runSync(response);
            }
        });
    }

}
