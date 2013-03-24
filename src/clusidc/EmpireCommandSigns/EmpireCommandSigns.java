package clusidc.EmpireCommandSigns;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import lib.PatPeter.SQLibrary.SQLite;

import clusidc.EmpireCommandSigns.EventListener;

public class EmpireCommandSigns extends JavaPlugin{
  private File pluginFolder;
  //private File configFile;
  
  public enum state {NONE, INFO, CREATE, ADDCOMMAND, REMCOMMAND, MODCOMMAND, ADDGIVEPERM, REMPERM, MODPERM, SETNEEDPERM, SETNEEDOP, SETLOCATION, REMOVE};
  
  public final HashMap<Player, state> playerstate = new HashMap<Player, state>();
  public final HashMap<Player, Object> commandstore = new HashMap<Player, Object>();
  public final HashMap<Player, Object> commandstore2 = new HashMap<Player, Object>();
  
  public final HashMap<Location, Integer> positions = new HashMap<Location, Integer>();
  public final HashMap<Integer, String[]> commands = new HashMap<Integer, String[]>();
  public final HashMap<Integer, String[]> givenpermissions = new HashMap<Integer, String[]>();
  public final HashMap<Integer, Boolean> needop = new HashMap<Integer, Boolean>();
  public final HashMap<Integer, String> needpermission = new HashMap<Integer, String>();
  public final HashMap<Integer, Location> executeat = new HashMap<Integer, Location>();
  Logger log;
  
  SQLite db;
  
  @Override
  public void onEnable() {
    log = this.getLogger();

    PluginDescriptionFile pdfFile = this.getDescription();
    
    //enabling Process
    pluginFolder = getDataFolder();
    //configFile = new File(pluginFolder, "config.yml");
    //createConfig();
    saveConfig();
    
    EventListener eventListener = new EventListener(this);
    getServer().getPluginManager().registerEvents(eventListener, this);

    log.info("Loading Signs. Please wait...");
    if(!loadSigns()) {
      setEnabled(false);
    }
    
    if(isEnabled()) {
      log.info("Version " + pdfFile.getVersion() + " is enabled!");
      log.info("Have Fun and pray for keeping your server alive against this evil plugin!");
    } else {
      log.log(Level.SEVERE, "Version " + pdfFile.getVersion() + " could not be enabled!");
    }
  }
  
  @Override
  public void onDisable() {
    
  }
  
  private boolean opendb() {
    log.info("Open Database...");
    db = new SQLite(log, "", pluginFolder.getPath(), "signs.db");
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    /*try {
      db.open();
    } catch (SQLException e) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      e.printStackTrace();
      return false;
    }*/
    
    ResultSet result;
    if(!db.isTable("signs")) {
      log.info("Requiered table \"signs\" doesn't exist, creating it...");
      String query = "CREATE TABLE signs (id INTEGER PRIMARY KEY ASC, world VARCHAR(255), x INTEGER, y INTEGER, z INTEGER, command TEXT, givenpermissions TEXT, operator BOOLEAN, needpermission VARCHAR(255), exeworld VARCHAR(255), exex DOUBLE, exey DOUBLE, exez DOUBLE);"; // CHARACTER SET utf8 COLLATE utf8_general_ci
      try {
        result = db.query(query);
        result.close();
      } catch (SQLException e) {
        log.log(Level.SEVERE, "Failed to create the Table.");
        e.printStackTrace();
        return false;
      }
    }
    db.close();
    return true;
  }
  
  private boolean loadSigns(){
    positions.clear();
    commands.clear();
    givenpermissions.clear();
    needop.clear();
    needpermission.clear();
    
    if(!opendb()) {
      setEnabled(false);
      log.log(Level.SEVERE, "An error occured while connecting to the db!");
      return false;
    }
    
    String query = "SELECT * FROM signs;";
    ResultSet result = null;
    
    try {
      result = db.query(query);
    } catch (SQLException e) {
      log.log(Level.SEVERE, "Failed to execute the query.");
      e.printStackTrace();
      return false;
    }
    
    int count=0;
    
    try {
      if(result != null && result.next()) {
        do {
          int id = result.getInt("id");
          positions.put(new Location(Bukkit.getWorld(result.getString("world")), result.getInt("x"), result.getInt("y"), result.getInt("z")), id);
          if(!result.getString("command").isEmpty())
            commands.put(id, result.getString("command").split("\u0009"));
          if(!result.getString("givenpermissions").isEmpty())
            givenpermissions.put(id, result.getString("givenpermissions").split("\u0009"));
          needop.put(id, result.getBoolean("operator"));
          needpermission.put(id, ((result.getString("needpermission").equalsIgnoreCase("") || result.getString("needpermission") == null) ? "ecs.use" : result.getString("needpermission")));
          if(!result.getString("exeworld").isEmpty()) {
            executeat.put(id, new Location(Bukkit.getWorld(result.getString("exeworld")), result.getDouble("exex"), result.getDouble("exey"), result.getDouble("exez")));
          }
          count++;
        } while(result.next());
      }
      log.info("Found " + count + " command sings!");
    } catch (SQLException e) {
      e.printStackTrace();
      db.close();
      return false;
    }
    db.close();
    
    return true;
  }
  
/*  private void createConfig() {
    if(!pluginFolder.exists()) {
      try {
        pluginFolder.mkdir();
      }
      catch(Exception e) {
        e.printStackTrace();
      }
    }
    
    if(!configFile.exists()) {
      try {
        configFile.createNewFile();
        this.getConfig().options().copyDefaults(true);
      }
      catch(Exception e) {
        e.printStackTrace();
      }
    }
  }*/
  
  public boolean addcommand(Location loc, String command, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "";
    String[] comman;
    if(!commands.containsKey(positions.get(loc))) {
      comman = new String[1];
      query = "UPDATE signs SET command=\"" + command + "\" WHERE id=" + positions.get(loc) + ";";
    } else {
      comman = new String[commands.get(positions.get(loc)).length+1];
      String comm = "";
      for(String c : commands.get(positions.get(loc))) {
        comm += c + "\u0009";
      }
      comm += command;
      query = "UPDATE signs SET command=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
    }
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to add a command to the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to add a command to the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    for(int i = 0; i < comman.length-1; i++){
      comman[i] = commands.get(positions.get(loc))[i];
    }
    comman[comman.length-1] = command;    
    commands.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "added the following Command to Sign with id " + positions.get(loc) + ": " + command);
    
    return true;
  }
  
  public boolean remcommand(Location loc, int index, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    if(index < 0) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a permission from the Database. (Index out of bounds.)");  
      return false;    
    }
    
    String query = "";
    String[] comman;
    if((commands.containsKey(positions.get(loc)) ? commands.get(positions.get(loc)).length : -1) > index) {
      comman = new String[commands.get(positions.get(loc)).length-1];
      int i = 0, j = 0;
      for(String c : commands.get(positions.get(loc))) {
        if(i!=index) {
          comman[j] = c;
          j++;
        }
        i++;
      }
      if(comman.length == 0) {
        query = "UPDATE signs SET command=\"\" WHERE id=" + positions.get(loc) + ";";
      }
      else if(comman.length == 1) {
        query = "UPDATE signs SET command=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[i];
        query = "UPDATE signs SET command=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a command from the Database. (Index out of bounds.)");
      return false;
    }
    
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a command from the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to remove a command from the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
     
    if(comman.length == 0) commands.remove(positions.get(loc));
    else commands.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Removed the Command with index: " + index + " from Sign with id " + positions.get(loc));
    
    return true;
  }
  
  public boolean modcommand(Location loc, int index, String replacement, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "";
    String[] comman;
    if(commands.get(positions.get(loc)).length > index) {
      comman = new String[commands.get(positions.get(loc)).length];
      comman = commands.get(positions.get(loc));
      comman[index] = replacement;
      if(comman.length == 1) {
        query = "UPDATE signs SET command=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(int i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[comman.length-1];
        query = "UPDATE signs SET command=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a command in the Database. (Index out of bounds.)");
      return false;
    }
    
    ResultSet result = null;
  
    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a command in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to mod a command in the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
     
    commands.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Modified the Command with index: " + index + " in Sign with id " + positions.get(loc));
    
    return true;
  }
  
  public boolean setneedperm(Location loc, String perm, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET needpermission=\"" + perm + "\" WHERE id=" + positions.get(loc) + ";";
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to set the needed permission in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to set the needed permission in the Database. ResultSet is null.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    log.info(sender.getName() + "set the needed permissions for the sign with id " + positions.get(loc) + ": " + perm);
    
    return true;
  }
  
  public boolean setop(Location loc, boolean op, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET operator=\"" + op + "\" WHERE id=" + positions.get(loc) + ";";
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to set the onlyop state in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to set the onlyop state in the Database. ResultSet is null.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    log.info(sender.getName() + "set the needed permissions for the sign with id " + positions.get(loc) + ": " + op);
    
    return true;
  }
  
  public boolean addperms(Location loc, String[] perms, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String perm = "";
    for(int i=0; i < perms.length; i++) {
      if(i < perms.length-1) {
        perm += perms[i] + "\u0009";
      } else {
        perm += perms[i];
      }
    }
    
    String query = "";
    String comm = "";
    if(!givenpermissions.containsKey(positions.get(loc))) {
      query = "UPDATE signs SET givenpermissions=\"" + perm + "\" WHERE id=" + positions.get(loc) + ";";
      comm = perm;
    } else {
      for(String c : givenpermissions.get(positions.get(loc))) {
        comm += c + "\u0009";
      }
      comm += perm;
      query = "UPDATE signs SET givenpermissions=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
    }
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to add permissions to the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to add a permissions to the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    givenpermissions.put(positions.get(loc), comm.split("\u0009"));
    
    log.info(sender.getName() + "added the following permissions to Sign with id " + positions.get(loc) + ": " + perm);
    
    return true;
  }
  
  public boolean remperm(Location loc, int index, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    if(index < 0) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a permission from the Database. (Index out of bounds.)");  
      return false;    
    }
    
    String query = "";
    String[] comman;
    if((givenpermissions.containsKey(positions.get(loc)) ? givenpermissions.get(positions.get(loc)).length : -1) > index) {
      comman = new String[givenpermissions.get(positions.get(loc)).length-1];
      int i = 0, j = 0;
      for(String c : givenpermissions.get(positions.get(loc))) {
        if(i!=index) {
          comman[j] = c;
          j++;
        }
        i++;
      }
      if(comman.length == 0) {
        query = "UPDATE signs SET givenpermissions=\"\" WHERE id=" + positions.get(loc) + ";";
      }
      else if(comman.length == 1) {
        query = "UPDATE signs SET givenpermissions=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[i];
        query = "UPDATE signs SET givenpermissions=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a permission from the Database. (Index out of bounds.)");
      return false;
    }
    
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a permission from the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to remove a permission from the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
     
    if(comman.length == 0) givenpermissions.remove(positions.get(loc));
    else givenpermissions.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Removed the Permission with index: " + index + " from Sign with id " + positions.get(loc) + ".");
    
    return true;
  }
  
  public boolean modperm(Location loc, int index, String replacement, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "";
    String[] comman = new String[givenpermissions.get(positions.get(loc)).length];
    if(givenpermissions.get(positions.get(loc)).length > index) {
      comman = givenpermissions.get(positions.get(loc));
      comman[index] = replacement;
      if(comman.length == 1) {
        query = "UPDATE signs SET givenpermissions=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(int i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[comman.length-1];
        query = "UPDATE signs SET givenpermissions=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a permission in the Database. (Index out of bounds.)");
      return false;
    }
    
    ResultSet result = null;
  
    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a permission in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to mod a permission in the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
     
    givenpermissions.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Modified the permission with index: " + index + " in Sign with id " + positions.get(loc));
    
    return true;
  }
  
  public boolean setloc(Location loc, Location exeloc, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET exex=" + exeloc.getX() + ", exey=" + exeloc.getY() + ", exez=" + exeloc.getZ() + ", exeworld=\"" + exeloc.getWorld().getName() + "\" WHERE id=" + positions.get(loc) + ";";

    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to add location to the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to add a location to the Database.");
      return false;
    }
    
    try {
      result.close();
    } catch (SQLException e) {
      db.close();
      log.log(Level.SEVERE, "Failed to close the result Set.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    if(!loadSigns()) {
      return false;
    }
    
    log.info(sender.getName() + " set the following location on the Sign with id " + positions.get(loc) + ": " + exeloc.toString());
    
    return true;
  }
  
  public boolean addsign(Location loc, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "INSERT into signs(world, x, y, z, command, givenpermissions, operator, needpermission, exeworld, exex, exey, exez)" +
    		" VALUES (\"" + loc.getWorld().getName() + "\", " + (int)loc.getX() + ", " + (int)loc.getY() + ", " + (int)loc.getZ() + ", " + "\"\"" + ", " + "\"\"" + ", " + "0" + ", " + "\"ecs.use\"" + ", " + 
        "\"" + /*loc.getWorld().getName()*/ "" + "\", " + (loc.getX()+0.5) + ", " + (loc.getY()) + ", " + (loc.getZ()+0.5) + ");";
    
    try {
      db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to add the sign to the Database.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    if(!loadSigns()) {
      return false;
    }
    db.close();

    log.info(sender.getName() + "added the sign with id " + positions.get(loc) + " to the database: " + loc.toVector().toString());
    
    return true;
  }
  
  public boolean delsign(Location loc, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "DELETE FROM signs WHERE id=" + positions.get(loc) + ";";
    
    try {
      db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to delete the sign from the Database.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    if(!loadSigns()) {
      
      return false;
    }
    db.close();
    log.info(sender.getName() + "deleted the sign from the database: " + loc.toVector().toString());
    
    return true;
  }
  
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("ecs")) {
      if(args.length > 0) {
        if(args[0].equalsIgnoreCase("reload")) {
          if((sender.isOp() || sender.hasPermission("ecs.reload") || sender.hasPermission("*") || sender.hasPermission("ecs.*") || sender.hasPermission("ecs.admin"))) {
            log.info("Reloading Plugin. Command came from: " + sender.getName());
            reloadConfig();
            if(!loadSigns()) {
              log.log(Level.SEVERE, "An error occured while reading the db!");
            }
            sender.sendMessage("[ECS] EmpireCommandSigns reloaded.");
            return true;
          } else {
            return false;
          }
        } else if(args[0].equalsIgnoreCase("create")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(playerstate.get((Player) sender) != state.CREATE) {
                playerstate.remove((Player) sender);
                playerstate.put((Player) sender, state.CREATE);
                sender.sendMessage("[ECS] To Register a Sign please right-click on it!");
                return true;
              } else {
                playerstate.remove((Player) sender);
                sender.sendMessage("[ECS] Aborted sign registering!");
                return true;
              }
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("info")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.info") || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(playerstate.get((Player) sender) != state.INFO) {
                playerstate.remove((Player) sender);
                playerstate.put((Player) sender, state.INFO);
                sender.sendMessage("[ECS] To get information to a sign, right-click on it!");
                return true;
              } else {
                playerstate.remove((Player) sender);
                sender.sendMessage("[ECS] Aborted sign info!");
                return true;
              }
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("addcommand")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 3) {
                            String cmd = "";
                            for(int i=3; i<args.length; i++) {
                              if(i < args.length-1) {
                                cmd += args[i] + " ";
                              } else {
                                cmd += args[i];
                              }
                            }
                            if(!addcommand(ent.getKey(), cmd, sender)) {
                              sender.sendMessage("[ECS] A problem occured while adding a command, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully added the command to the sign:" + cmd);
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  if(playerstate.get((Player) sender) != state.ADDCOMMAND) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.ADDCOMMAND);
                    
                    String cmd = "";
                    for(int i=1; i<args.length; i++) {
                      if(i < args.length-1) {
                        cmd += args[i]+" ";
                      } else {
                        cmd += args[i];
                      }
                    }
                    commandstore.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To add the command to a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted adding command!");
                    return true;
                  }
                }
              } else {
                sender.sendMessage("[ECS] Too few arguments.");
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("remcommand")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 3) {
                            int index = 0;
                            try {                      
                              index=Integer.parseInt(args[3]);
                            }
                            catch(NumberFormatException e) {
                              index=-1;
                              sender.sendMessage("[ECS] Your id is not a parsable int.");
                              return true;
                            }
                            if(!remcommand(ent.getKey(), index, sender)) {
                              sender.sendMessage("[ECS] A problem occured while removing a command, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully removed the command with id " + index + " from the sign.");
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  int index = 0;
                  try {                      
                    index=Integer.parseInt(args[1]);
                  }
                  catch(NumberFormatException e) {
                    index=-1;
                    sender.sendMessage("[ECS] Your id is not a parsable int.");
                    return true;
                  }
                  if(playerstate.get((Player) sender) != state.REMCOMMAND) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.REMCOMMAND);
                    
                    commandstore.put((Player) sender, index);
                    sender.sendMessage("[ECS] To remove the command from a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing command!");
                    return true;
                  }
                }
              } else {
                sender.sendMessage("[ECS] Too few arguments.");
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("modcommand")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 3) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 4) {
                            int index = 0;
                            try {                      
                              index=Integer.parseInt(args[3]);
                            }
                            catch(NumberFormatException e) {
                              index=-1;
                              sender.sendMessage("[ECS] Your id is not a parsable int.");
                              return true;
                            }
                            String cmd = "";
                            for(int i=4; i<args.length; i++) {
                              if(i < args.length-1) {
                                cmd += args[i] + " ";
                              } else {
                                cmd += args[i];
                              }
                            }
                            if(!modcommand(ent.getKey(), index, cmd, sender)) {
                              sender.sendMessage("[ECS] A problem occured while modifying a command, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully modified the command with id " + index + " in the sign.");
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  } else {
                    sender.sendMessage("[ECS] Too few arguments.");
                  }
                } else if (args.length > 2){
                  int index = 0;
                  try {                      
                    index=Integer.parseInt(args[1]);
                  }
                  catch(NumberFormatException e) {
                    index=-1;
                    sender.sendMessage("[ECS] Your id is not a parsable int.");
                    return true;
                  }
                  String cmd = "";
                  for(int i=2; i<args.length; i++) {
                    if(i < args.length-1) {
                      cmd += args[i] + " ";
                    } else {
                      cmd += args[i];
                    }
                  }
                  if(playerstate.get((Player) sender) != state.MODCOMMAND) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.MODCOMMAND);
                    
                    commandstore.put((Player) sender, index);
                    commandstore2.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To modify the command in a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing command!");
                    return true;
                  }
                } else {
                  sender.sendMessage("[ECS] Too few arguments.");
                } 
              } else {
                sender.sendMessage("[ECS] Too few arguments.");
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("addperm")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 3) {
                            String[] perms = new String[args.length-3];
                            for(int i=0; i<perms.length; i++) {
                              perms[i]=args[i+3];
                            }
                            if(!addperms(ent.getKey(), perms, sender)) {
                              sender.sendMessage("[ECS] A problem occured while adding some permissions to the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully added permissions to the sign.");
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  if(playerstate.get((Player) sender) != state.ADDGIVEPERM) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.ADDGIVEPERM);
                    
                    String[] cmd = new String[args.length - 1];
                    for(int i=0; i<cmd.length; i++) {
                      cmd[i]=args[i+1];
                    }
                    commandstore.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To add the permissions to a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted adding permissions!");
                    return true;
                  }
                }
              } else {
                sender.sendMessage("[ECS] Too few arguments.");
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("remperm")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 3) {
                            int index = 0;
                            try {                      
                              index=Integer.parseInt(args[3]);
                            }
                            catch(NumberFormatException e) {
                              index=-1;
                              sender.sendMessage("[ECS] Your id is not a parsable int.");
                              return true;
                            }
                            if(!remperm(ent.getKey(), index, sender)) {
                              sender.sendMessage("[ECS] A problem occured while removing a permission, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully removed the permission with id " + index + " from the sign.");
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  int index = 0;
                  try {                      
                    index=Integer.parseInt(args[1]);
                  }
                  catch(NumberFormatException e) {
                    index=-1;
                    sender.sendMessage("[ECS] Your id is not a parsable int.");
                    return true;
                  }
                  if(playerstate.get((Player) sender) != state.REMPERM) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.REMPERM);
                    
                    commandstore.put((Player) sender, index);
                    sender.sendMessage("[ECS] To remove the permission from a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing permission!");
                    return true;
                  }
                }
              } else {
                sender.sendMessage("[ECS] Too few arguments.");
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("modperm")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 3) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 4) {
                            int index = 0;
                            try {                      
                              index=Integer.parseInt(args[3]);
                            }
                            catch(NumberFormatException e) {
                              index=-1;
                              sender.sendMessage("[ECS] Your id is not a parsable int.");
                              return true;
                            }
                            String cmd = "";
                            for(int i=4; i<args.length; i++) {
                              if(i < args.length-1) {
                                cmd += args[i] + " ";
                              } else {
                                cmd += args[i];
                              }
                            }
                            if(!modcommand(ent.getKey(), index, cmd, sender)) {
                              sender.sendMessage("[ECS] A problem occured while modifying a permission, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully modified the permission with id " + index + " in the sign.");
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  } else {
                    sender.sendMessage("[ECS] Too few arguments.");
                  }
                } else if (args.length > 2){
                  int index = 0;
                  try {                      
                    index=Integer.parseInt(args[1]);
                  }
                  catch(NumberFormatException e) {
                    index=-1;
                    sender.sendMessage("[ECS] Your id is not a parsable int.");
                    return true;
                  }
                  String cmd = "";
                  for(int i=2; i<args.length; i++) {
                    if(i < args.length-1) {
                      cmd += args[i] + " ";
                    } else {
                      cmd += args[i];
                    }
                  }
                  if(playerstate.get((Player) sender) != state.MODPERM) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.MODPERM);
                    
                    commandstore.put((Player) sender, index);
                    commandstore2.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To modify the permission in a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing permission!");
                    return true;
                  }
                } else {
                  sender.sendMessage("[ECS] Too few arguments.");
                } 
              } else {
                sender.sendMessage("[ECS] Too few arguments.");
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("setperm")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 3) {
                            String perm = args[3];
                            if(!setneedperm(ent.getKey(), perm, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the permission to the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the required permission of this sign to " + perm);
                              return true;
                            }
                          } else {
                            if(!setneedperm(ent.getKey(), "ecs.use", sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the permission to the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the required permission of this sign to ecs.use.");
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  if(playerstate.get((Player) sender) != state.SETNEEDPERM) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETNEEDPERM);
                    
                    commandstore.put((Player) sender, args[1]);
                    sender.sendMessage("[ECS] To set the required permission of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    commandstore.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted setting permission!");
                    return true;
                  }
                }
              } else {
                if(playerstate.get((Player) sender) != state.SETNEEDPERM) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.SETNEEDPERM);
                  
                  commandstore.put((Player) sender, "ecs.use");
                  sender.sendMessage("[ECS] To set the required permission of a sign to default, please click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  commandstore.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted setting permission!");
                  return true;
                }
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("setloc")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(!setloc(ent.getKey(), ((Player) sender).getLocation(), sender)) {
                            sender.sendMessage("[ECS] A problem occured while setting the executing location of the sign, please view the server log for further information.");
                            return true;
                          } else {
                            sender.sendMessage("[ECS] Successfully set the executing location of the sign to x:" + ((Player) sender).getLocation().getX() + " y:" + ((Player) sender).getLocation().getY() + " z:" + ((Player) sender).getLocation().getZ() + " in world:" + ((Player) sender).getLocation().getWorld().getName());
                            return true;
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  sender.sendMessage("[ECS] Too few arguments.");
                }
              } else {
                if(playerstate.get((Player) sender) != state.SETLOCATION) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.SETLOCATION);
                  
                  commandstore.put((Player) sender, ((Player)sender).getLocation());
                  sender.sendMessage("[ECS] To set the executing location of a sign, please click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  commandstore.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted setting location!");
                  return true;
                }
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        }  else if(args[0].equalsIgnoreCase("setop")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 1) {
                if(args[1].equalsIgnoreCase("id")) {
                  if(args.length > 2) {
                    int intid;
                    try {                      
                      intid=Integer.parseInt(args[2]);
                    }
                    catch(NumberFormatException e) {
                      intid=-1;
                      sender.sendMessage("[ECS] Your id is not a parsable int.");
                      return true;
                    }
                    if(positions.containsValue(intid)) {
                      Set<Entry<Location, Integer>> posset = positions.entrySet();
                      for(Entry<Location, Integer> ent: posset) {
                        if(ent.getValue() == intid) {
                          if(args.length > 3) {
                            boolean state = false;
                            try {                      
                              state=Boolean.parseBoolean(args[3]);
                            }
                            catch(NumberFormatException e) {
                              state = false;
                              sender.sendMessage("[ECS] Your bool is not a parsable bool.");
                              return true;
                            }
                            if(!setop(ent.getKey(), state, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the onlyop state of the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the onlyop state of this sign to " + state);
                              return true;
                            }
                          } else {
                            if(!setop(ent.getKey(), false, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the onlyop state of the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the onlyop state of this sign to " + false);
                              return true;
                            }
                          }
                        }
                      }
                    } else {
                      sender.sendMessage("[ECS] Your id does not exist.");
                      return true;
                    }
                  }
                } else {
                  if(playerstate.get((Player) sender) != state.SETNEEDOP) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETNEEDOP);
                    
                    boolean state = false;
                    try {                      
                      state=Boolean.parseBoolean(args[1]);
                    }
                    catch(NumberFormatException e) {
                      state = false;
                      sender.sendMessage("[ECS] Your bool is not a parsable bool.");
                      return true;
                    }
                    commandstore.put((Player) sender, state);
                    sender.sendMessage("[ECS] To set the onlyop state of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    commandstore.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted setting permission!");
                    return true;
                  }
                }
              } else {
                if(playerstate.get((Player) sender) != state.SETNEEDOP) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.SETNEEDOP);
                  
                  commandstore.put((Player) sender, false);
                  sender.sendMessage("[ECS] To set the onlyop state of a sign, please click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  commandstore.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted setting permission!");
                  return true;
                }
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else if(args[0].equalsIgnoreCase("remove")) {
          if(sender instanceof Player) {
            if((sender.isOp() || sender.hasPermission("ecs.admin") || sender.hasPermission("*") || sender.hasPermission("ecs.*"))) {
              if(args.length > 2) {
                if(args[1].equalsIgnoreCase("id")) {
                  int intid;
                  try {                      
                    intid=Integer.parseInt(args[2]);
                  }
                  catch(NumberFormatException e) {
                    intid=-1;
                    sender.sendMessage("[ECS] Your id is not a parsable int.");
                    return true;
                  }
                  if(positions.containsValue(intid)) {
                    Set<Entry<Location, Integer>> posset = positions.entrySet();
                    for(Entry<Location, Integer> ent: posset) {
                      if(ent.getValue() == intid) {
                        if(!delsign(ent.getKey(), sender)) {
                          sender.sendMessage("[ECS] A problem occured while removing this sign from the datatabase, please view the server log for further information.");
                          return true;
                        } else {
                          sender.sendMessage("[ECS] Successfully removed the sign:" + ent.getKey().toVector().toString());
                          return true;
                        }
                      }
                    }
                  } else {
                    sender.sendMessage("[ECS] Your id does not exist.");
                    return true;
                  }
                } else {
                  if(playerstate.get((Player) sender) != state.REMOVE) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.REMOVE);
                    sender.sendMessage("[ECS] To remove the association from a sign, please right-click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing!");
                    return true;
                  }
                }
              } else {
                if(playerstate.get((Player) sender) != state.REMOVE) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.REMOVE);
                  
                  String cmd = "";
                  for(int i=1; i<args.length; i++) {
                    if(i < args.length-1) {
                      cmd += args[i]+" ";
                    } else {
                      cmd += args[i];
                    }
                  }
                  commandstore.put((Player) sender, cmd);
                  sender.sendMessage("[ECS] To remove the association from a sign, please right-click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted removing!");
                  return true;
                }
              }              
            } else {
              return false;
            }
          } else {
            sender.sendMessage("[ECS] It seems that you are on the console, try this command ingame again.");
            return true;
          }
        } else {
          return false;
        }
      }
    }
    return true;
  }
}