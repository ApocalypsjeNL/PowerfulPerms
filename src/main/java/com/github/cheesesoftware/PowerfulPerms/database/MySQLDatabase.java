package com.github.cheesesoftware.PowerfulPerms.database;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.cheesesoftware.PowerfulPermsAPI.DBDocument;
import com.github.cheesesoftware.PowerfulPermsAPI.IScheduler;
import com.github.cheesesoftware.PowerfulPermsAPI.PowerfulPermsPlugin;
import com.google.common.base.Charsets;

public class MySQLDatabase extends Database {

    private SQL sql;
    private PowerfulPermsPlugin plugin;

    public MySQLDatabase(IScheduler scheduler, SQL sql, PowerfulPermsPlugin plugin) {
        super(scheduler);
        this.sql = sql;
        this.plugin = plugin;
    }

    private DBResult fromResultSet(ResultSet r) throws SQLException {
        ArrayList<DBDocument> rows = new ArrayList<DBDocument>();
        ResultSetMetaData md = r.getMetaData();
        int columns = md.getColumnCount();
        while (r.next()) {
            Map<String, Object> row = new HashMap<String, Object>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(md.getColumnName(i), r.getObject(i));
            }
            rows.add(new DBDocument(row));
        }
        return new DBResult(rows);
    }

    @Override
    public void applyPatches() {
        if (plugin.getOldVersion() < 233) {
            // Set [default] UUID
            final PowerfulPermsPlugin pl = plugin;
            setPlayerUUID("[default]", java.util.UUID.nameUUIDFromBytes(("[default]").getBytes(Charsets.UTF_8)), new DBRunnable(true) {

                @Override
                public void run() {
                    pl.getLogger().info("Applied database patch #1: Inserted UUID for player [default].");
                }
            });
        }

        if (plugin.getOldVersion() < 240) {
            // Add "ladder" and "rank" columns to groups table
            try {

                PreparedStatement s = sql.getConnection().prepareStatement("SHOW COLUMNS FROM `" + tblGroups + "` LIKE 'ladder';");
                ResultSet result = s.executeQuery();
                if (!result.next()) {
                    s.close();
                    s = sql.getConnection().prepareStatement("ALTER TABLE `" + tblGroups + "` ADD COLUMN `ladder` VARCHAR(64) NOT NULL AFTER `suffix`,ADD COLUMN `rank` INT NOT NULL AFTER `ladder`");
                    s.execute();
                    s.close();
                    plugin.getLogger().info("Applied database patch #2: Added columns 'ladder' and 'rank' to groups table.");
                } else {
                    plugin.getLogger().info("Skipping database patch #2.");
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void tableExists(final String table, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean exists = false;
                try {

                    DatabaseMetaData dbm = sql.getConnection().getMetaData();
                    ResultSet tables = dbm.getTables(null, null, table, null);
                    if (tables.next())
                        exists = true;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                done.setResult(new DBResult(exists));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void createTables(final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                final boolean success = true;

                try {
                    List<String> in = Files.readAllLines(Paths.get(this.getClass().getResource("tables.sql").toURI()), Charset.defaultCharset());
                    String oneString = "";
                    for (String s : in)
                        oneString += s + System.lineSeparator();

                    try {
                        PreparedStatement s = sql.getConnection().prepareStatement(oneString);
                        s.execute();
                        s.close();

                        done.setResult(new DBResult(success));
                        scheduler.runSync(done, done.sameThread());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (URISyntaxException e1) {
                    e1.printStackTrace();
                }

            }
        }, done.sameThread());
    }

    @Override
    public void insertGroup(final String group, final String ladder, final int rank, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblGroups + " SET `name`=?, `ladder`=?, `rank`=?");
                    s.setString(1, group);
                    s.setString(2, ladder);
                    s.setInt(3, rank);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void insertPlayer(final UUID uuid, final String name, final String prefix, final String suffix, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblPlayers + " SET `uuid`=?, `name`=?, `prefix`=?, `suffix`=?;");
                    s.setString(1, uuid.toString());
                    s.setString(2, name);
                    s.setString(3, prefix);
                    s.setString(4, suffix);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void getPlayer(final UUID uuid, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                DBResult result;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("SELECT * FROM " + tblPlayers + " WHERE `uuid`=?");
                    s.setString(1, uuid.toString());
                    s.execute();
                    ResultSet r = s.getResultSet();
                    result = fromResultSet(r);
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    result = new DBResult(false);
                }

                done.setResult(result);
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void getPlayers(final String name, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                DBResult result;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("SELECT * FROM " + tblPlayers + " WHERE `name`=?");
                    s.setString(1, name);
                    s.execute();
                    ResultSet r = s.getResultSet();
                    result = fromResultSet(r);
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    result = new DBResult(false);
                }

                done.setResult(result);
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void setPlayerName(final UUID uuid, final String name, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("UPDATE " + tblPlayers + " SET `name`=? WHERE `uuid`=?;");
                    s.setString(1, name);
                    s.setString(2, uuid.toString());
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void setPlayerUUID(final String name, final UUID uuid, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("UPDATE " + tblPlayers + " SET `uuid`=? WHERE `name`=?;");
                    s.setString(1, uuid.toString());
                    s.setString(2, name);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void getGroups(final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                DBResult result;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("SELECT * FROM " + tblGroups);
                    s.execute();
                    ResultSet r = s.getResultSet();
                    result = fromResultSet(r);
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    result = new DBResult(false);
                }

                done.setResult(result);
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void getGroupPermissions(final int groupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                DBResult result;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("SELECT * FROM " + tblGroupPermissions + " WHERE `groupid`=?");
                    s.setInt(1, groupId);
                    s.execute();
                    ResultSet r = s.getResultSet();
                    result = fromResultSet(r);
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    result = new DBResult(false);
                }

                done.setResult(result);
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void getPlayerPermissions(final UUID uuid, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                DBResult result;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("SELECT * FROM " + tblPlayerPermissions + " WHERE `playeruuid`=?");
                    s.setString(1, uuid.toString());
                    s.execute();
                    ResultSet r = s.getResultSet();
                    result = fromResultSet(r);
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    result = new DBResult(false);
                }

                done.setResult(result);
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void playerHasPermission(final UUID uuid, final String permission, final String world, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = false;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("SELECT * FROM " + tblPlayerPermissions + " WHERE `playeruuid`=? AND `permission`=? AND `world`=? AND `server`=?");
                    s.setString(1, uuid.toString());
                    s.setString(2, permission);
                    s.setString(3, world);
                    s.setString(4, server);
                    s.execute();
                    ResultSet result = s.getResultSet();
                    if (result.next()) {
                        success = true;
                    }
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void insertPlayerPermission(final UUID uuid, final String permission, final String world, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblPlayerPermissions + " SET `playeruuid`=?, `permission`=?, `world`=?, `server`=?");
                    s.setString(1, uuid.toString());
                    s.setString(2, permission);
                    s.setString(3, world);
                    s.setString(4, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deletePlayerPermission(final UUID uuid, final String permission, final String world, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;
                int amount = 0;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM `" + tblPlayerPermissions + "` WHERE `playeruuid`=? AND `permission`=? AND `server`=? AND `world`=?");

                    s.setString(1, uuid.toString());
                    s.setString(2, permission);
                    s.setString(3, server);
                    s.setString(4, world);

                    amount = s.executeUpdate();
                    if (amount <= 0)
                        success = false;
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success, amount));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deletePlayerPermissions(final UUID uuid, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;
                int amount = 0;

                try {
                    String statement = "DELETE FROM `" + tblPlayerPermissions + "` WHERE `playeruuid`=?";
                    PreparedStatement s = sql.getConnection().prepareStatement(statement);

                    s.setString(1, uuid.toString());
                    amount = s.executeUpdate();
                    if (amount <= 0)
                        success = false;
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success, amount));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void insertGroupPermission(final int groupId, final String permission, final String world, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblGroupPermissions + " SET `groupid`=?, `permission`=?, `world`=?, `server`=?");
                    s.setInt(1, groupId);
                    s.setString(2, permission);
                    s.setString(3, world);
                    s.setString(4, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupPermission(final int groupId, final String permission, final String world, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;
                int amount = 0;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupPermissions + " WHERE `groupid`=? AND `permission`=? AND `world`=? AND `server`=?");
                    s.setInt(1, groupId);
                    s.setString(2, permission);
                    s.setString(3, world);
                    s.setString(4, server);
                    amount = s.executeUpdate();
                    if (amount <= 0)
                        success = false;
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success, amount));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupPermissions(final int groupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;
                int amount = 0;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupPermissions + " WHERE `groupid`=?");
                    s.setInt(1, groupId);
                    amount = s.executeUpdate();
                    if (amount <= 0)
                        success = false;
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success, amount));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void setPlayerPrefix(final UUID uuid, final String prefix, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("UPDATE " + tblPlayers + " SET `prefix`=? WHERE `uuid`=?");
                    s.setString(1, prefix);
                    s.setString(2, uuid.toString());
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void setPlayerSuffix(final UUID uuid, final String suffix, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("UPDATE " + tblPlayers + " SET `suffix`=? WHERE `uuid`=?");
                    s.setString(1, suffix);
                    s.setString(2, uuid.toString());
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void addPlayerGroup(final UUID uuid, final int groupId, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblPlayerGroups + " SET `playeruuid`=?, `groupid`=?, `server`=?");
                    s.setString(1, uuid.toString());
                    s.setInt(2, groupId);
                    s.setString(3, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deletePlayerGroup(final UUID uuid, final int groupId, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblPlayerGroups + " WHERE `playeruuid`=? AND `groupid`=? AND `server`=?");
                    s.setString(1, uuid.toString());
                    s.setInt(2, groupId);
                    s.setString(3, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroup(final int groupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;
                int amount = 0;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroups + " WHERE `id`=?;");
                    s.setInt(1, groupId);
                    amount = s.executeUpdate();
                    if (amount <= 0)
                        success = false;
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                final boolean success2 = success;
                final int amount2 = amount;

                plugin.getLogger().info("Deleting group " + groupId + "...");
                deleteGroupPermissions(groupId, new DBRunnable(true) {

                    @Override
                    public void run() {
                        plugin.getLogger().info("Deleting group parents...");
                        deleteGroupParents(groupId, new DBRunnable(true) {

                            @Override
                            public void run() {
                                plugin.getLogger().info("Deleting group prefixes...");
                                deleteGroupPrefixes(groupId, new DBRunnable(true) {

                                    @Override
                                    public void run() {
                                        plugin.getLogger().info("Deleting group suffixes...");
                                        deleteGroupSuffixes(groupId, new DBRunnable(true) {

                                            @Override
                                            public void run() {
                                                plugin.getLogger().info("Done.");
                                                done.setResult(new DBResult(success2, amount2));
                                                scheduler.runSync(done, done.sameThread());
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });

            }
        }, done.sameThread());
    }

    @Override
    public void addGroupParent(final int groupId, final int parentGroupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblGroupParents + " SET `groupid`=?, `parentgroupid`=?");
                    s.setInt(1, groupId);
                    s.setInt(2, parentGroupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupParent(final int groupId, final int parentGroupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupParents + " WHERE `groupid`=? AND `parentgroupid`=?");
                    s.setInt(1, groupId);
                    s.setInt(2, parentGroupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupParents(final int groupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupParents + " WHERE `groupid`=?");
                    s.setInt(1, groupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void addGroupPrefix(final int groupId, final String prefix, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblGroupPrefixes + " SET `groupid`=?, `prefix`=?, `server`=?");
                    s.setInt(1, groupId);
                    s.setString(2, prefix);
                    s.setString(3, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupPrefix(final int groupId, final String prefix, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupPrefixes + " WHERE `groupid`=? AND `prefix`=? AND `server`=?");
                    s.setInt(1, groupId);
                    s.setString(2, prefix);
                    s.setString(3, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupPrefixes(final int groupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupPrefixes + " WHERE `groupid`=?");
                    s.setInt(1, groupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void addGroupSuffix(final int groupId, final String suffix, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("INSERT INTO " + tblGroupSuffixes + " SET `groupid`=?, `suffix`=?, `server`=?");
                    s.setInt(1, groupId);
                    s.setString(2, suffix);
                    s.setString(3, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupSuffix(final int groupId, final String suffix, final String server, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupSuffixes + " WHERE `groupid`=? AND `suffix`=? AND `server`=?");
                    s.setInt(1, groupId);
                    s.setString(2, suffix);
                    s.setString(3, server);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void deleteGroupSuffixes(final int groupId, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("DELETE FROM " + tblGroupSuffixes + " WHERE `groupid`=?");
                    s.setInt(1, groupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void setGroupLadder(final int groupId, final String ladder, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("UPDATE " + tblGroups + " SET `ladder`=? WHERE `id`=?");
                    s.setString(1, ladder);
                    s.setInt(2, groupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

    @Override
    public void setGroupRank(final int groupId, final int rank, final DBRunnable done) {
        scheduler.runAsync(new Runnable() {

            @Override
            public void run() {
                boolean success = true;

                try {
                    PreparedStatement s = sql.getConnection().prepareStatement("UPDATE " + tblGroups + " SET `rank`=? WHERE `id`=?");
                    s.setInt(1, rank);
                    s.setInt(2, groupId);
                    s.execute();
                    s.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    success = false;
                }

                done.setResult(new DBResult(success));
                scheduler.runSync(done, done.sameThread());
            }
        }, done.sameThread());
    }

}
