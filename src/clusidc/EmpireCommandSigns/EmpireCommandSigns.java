package clusidc.EmpireCommandSigns;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import lib.PatPeter.SQLibrary.SQLite;
import clusidc.EmpireCommandSigns.EventListener;

public class EmpireCommandSigns extends JavaPlugin{
  private File pluginFolder;
  private File configFile;
  
  public boolean useEconomy;  
  public static Economy economy;
  
  public boolean debug = true; 
  public enum dlvl {  NORMAL,
                      DEBUG,
                      SPECIAL};

  public enum state { NONE, 
                      INFO,
                      CREATE,
                      ADDCOMMAND,
                      REMCOMMAND,
                      MODCOMMAND,
                      ADDGIVEPERM,
                      REMPERM,
                      MODPERM,
                      SETNEEDPERM,
                      SETNEEDOP,
                      SETLOCATION,
                      REMOVE,
                      SETECONOMY,
                      //untrigger
                      ADDCOMMANDUT,
                      REMCOMMANDUT,
                      MODCOMMANDUT,
                      ADDGIVEPERMUT,
                      REMPERMUT,
                      MODPERMUT,
                      SETNEEDPERMUT,
                      SETNEEDOPUT,
                      SETLOCATIONUT,
                      SETECONOMYUT,
                      //sitsonpressureplate
                      SITSONPRESSUREPLATE};
                     
  public enum economystate { NONE,
                             CHECK,
                             WITHDRAW,
                             GIVE};
                             
  /**
   * Determines the Action, whether you click an Object
   * or the Object is released (leave Pessure Plate)
   *
   */
  
  public final HashMap<String, Object> defaults = new HashMap<String, Object>();
                             
  public final HashMap<Player, state> playerstate = new HashMap<Player, state>();
  public final HashMap<Player, Object> commandstore = new HashMap<Player, Object>();
  public final HashMap<Player, Object> commandstore2 = new HashMap<Player, Object>();
  
  public final HashMap<Location, Integer> positions = new HashMap<Location, Integer>();
  
  public final HashMap<Integer, String[]> commands = new HashMap<Integer, String[]>();
  public final HashMap<Integer, String[]> givenpermissions = new HashMap<Integer, String[]>();
  public final HashMap<Integer, Boolean> needop = new HashMap<Integer, Boolean>();
  public final HashMap<Integer, String> needpermission = new HashMap<Integer, String>();
  public final HashMap<Integer, Location> executeat = new HashMap<Integer, Location>();
  public final HashMap<Integer, economystate> economys = new HashMap<Integer, economystate>();
  public final HashMap<Integer, Double> economyv = new HashMap<Integer, Double>();
  
  public final HashMap<Integer, String[]> untrigger_commands = new HashMap<Integer, String[]>();
  public final HashMap<Integer, String[]> untrigger_givenpermissions = new HashMap<Integer, String[]>();
  public final HashMap<Integer, Boolean> untrigger_needop = new HashMap<Integer, Boolean>();
  public final HashMap<Integer, String> untrigger_needpermission = new HashMap<Integer, String>();
  public final HashMap<Integer, Location> untrigger_executeat = new HashMap<Integer, Location>();
  public final HashMap<Integer, economystate> untrigger_economys = new HashMap<Integer, economystate>();
  public final HashMap<Integer, Double> untrigger_economyv = new HashMap<Integer, Double>();
  
  Logger log;
  
  SQLite db;
  
  public void logDebug(Level ll, dlvl dl, String msg) {
    if (dl == dlvl.NORMAL) {
      log.log(ll, msg);
    } else if (dl == dlvl.DEBUG && debug) {
      log.log(ll, "[DEBUG]" + msg);
    } else if (dl == dlvl.SPECIAL) {
      log.log(ll, "[IMPORTANT]" + msg);
    }
  }
  
  @Override
  public void onEnable() {
    log = this.getLogger();

    PluginDescriptionFile pdfFile = this.getDescription();
    
    //enabling Process
    pluginFolder = getDataFolder();
    configFile = new File(pluginFolder, "config.yml");
    createConfig();
    this.getConfig().options().copyDefaults(true);    
    saveConfig();
    
    debug = this.getConfig().getBoolean("debugMessages");
    
    EventListener eventListener = new EventListener(this);
    getServer().getPluginManager().registerEvents(eventListener, this);

    logDebug(Level.INFO, dlvl.DEBUG, "Loading Signs. Please wait...");
    try {
      setEnabled(loadSigns());
    } catch (NoClassDefFoundError e) {
      if(e.getCause().toString().contains("lib.PatPeter.SQLibrary.SQLite")) {
        log.severe("Couldn't find any SQLibrary Class. Do You have installed the SQLibrary?: " + e.getCause().toString());
      } else {
        log.severe(e.getCause().toString());
      }
      setEnabled(false);
    }

    //Enable Economy
    useEconomy = false;
    economy = null;
    
    if(isEnabled() && getConfig().getBoolean("useEconomy")) {
      useEconomy = true;
      if(!setupEconomy()) {
        log.severe("Could not attach to Vault!");
        setEnabled(false);
      }
    }

    //If successfully enabled
    if(isEnabled()) {
      log.info("Version " + pdfFile.getVersion() + " is enabled!");
    } else {
      log.log(Level.SEVERE, "Version " + pdfFile.getVersion() + " could not be enabled!");
    }
  }
  
  @Override
  public void onDisable() {
    
  }
  
  /*private void fillDefaultConfig(){
    defaults.put("leftclick", true);
    defaults.put("useEconomy", true);
    defaults.put("silentexecute", false);
    defaults.put("debugMessages", false);
    this.getConfig().addDefaults(defaults);
  }*/
  
  private boolean opendb() {
    logDebug(Level.INFO, dlvl.DEBUG, "Open Database...");
    db = new SQLite(log, "[ECS] ", pluginFolder.getPath(), "signs");
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
      String query = "CREATE TABLE signs (id INTEGER PRIMARY KEY ASC, world VARCHAR(255), x INTEGER, y INTEGER, z INTEGER, command TEXT, givenpermissions TEXT, operator BOOLEAN, needpermission VARCHAR(255), exeworld VARCHAR(255), exex DOUBLE, exey DOUBLE, exez DOUBLE, economystate INTEGER, economyvalue DOUBLE, untrigger_command TEXT, untrigger_givenpermissions TEXT, untrigger_operator BOOLEAN, untrigger_needpermission VARCHAR(255), untrigger_exeworld VARCHAR(255), untrigger_exex DOUBLE, untrigger_exey DOUBLE, untrigger_exez DOUBLE, untrigger_economystate INTEGER, untrigger_economyvalue DOUBLE);"; // CHARACTER SET utf8 COLLATE utf8_general_ci
      try {
        result = db.query(query);
        result.close();
      } catch (SQLException e) {
        log.log(Level.SEVERE, "Failed to create the Table.");
        e.printStackTrace();
        return false;
      }
    }
    
    String query = "SELECT sql FROM sqlite_master WHERE tbl_name = \"signs\" AND type = \"table\"";
    
    try {
      result = db.query(query);
    } catch (SQLException e) {
      log.log(Level.SEVERE, "Failed to execute the query: " + query);
      e.printStackTrace();
      return false;
    }

    try {
      if(result != null && result.next()) {
        //log.info(result.getString("sql"));
        if(!result.getString("sql").contains("economystate INTEGER, economyvalue DOUBLE")) {
          result.close();
          log.info("Economy not detected in Library, adding it.");
          query = "ALTER TABLE signs ADD COLUMN economystate INTEGER;";
          
          try {
            result = db.query(query);
            result.close();
          } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to execute the query: " + query);
            e.printStackTrace();
            return false;
          }
          
          query = "ALTER TABLE signs ADD COLUMN economyvalue DOUBLE;";
          
          try {
            result = db.query(query);
            result.close();
          } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to execute the query: " + query);
            e.printStackTrace();
            return false;
          }
        }
        
        //check if dthe database is already on the new version
        if(!result.getString("sql").contains("untrigger_command TEXT")) {
          result.close();/*
          untrigger_command TEXT
          untrigger_givenpermissions TEXT
          untrigger_operator BOOLEAN
          untrigger_needpermission VARCHAR(255)
          untrigger_exeworld VARCHAR(255)
          untrigger_exex DOUBLE
          untrigger_exey DOUBLE
          untrigger_exez DOUBLE
          untrigger_economystate INTEGER
          untrigger_economyvalue DOUBLE*/
          log.info("Untrigger not detected in Library, adding it.");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_command TEXT DEFAULT \"\"; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_givenpermissions TEXT DEFAULT \"\"; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_operator BOOLEAN DEFAULT NULL; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_needpermission VARCHAR(255) DEFAULT \"\"; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_exeworld VARCHAR(255) DEFAULT \"\"; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_exex DOUBLE DEFAULT NULL; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_exey DOUBLE DEFAULT NULL; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_exez DOUBLE DEFAULT NULL; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_economystate INTEGER DEFAULT NULL; ");
          querysql("ALTER TABLE signs ADD COLUMN untrigger_economyvalue DOUBLE DEFAULT 0;");
          
          //logDebug(Level.INFO, dlvl.DEBUG, "executing" + query);
          
          /*try {
            result = db.query(query);
            result.close();
          } catch (SQLException e) {
            logDebug(Level.SEVERE, dlvl.SPECIAL, "Failed to execute the query: " + query);
            e.printStackTrace();
            return false;
          }*/
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    
    //db.close(); //<- DO:NOT:E.V.E.R:TOUCH THIS COMMENTED OUT LINE BECAUSE OF REASONS - IT BREAKS IF I TAKE IT IN
    return true;
  }
  
  private void querysql(String query){
    logDebug(Level.INFO, dlvl.DEBUG, "executing" + query);
    ResultSet result;
    try {
      result = db.query(query);
      result.close();
    } catch (SQLException e) {
      logDebug(Level.SEVERE, dlvl.SPECIAL, "Failed to execute the query: " + query);
      e.printStackTrace();
    }
  }
  
  private boolean loadSigns(){
    positions.clear();
    commands.clear();
    givenpermissions.clear();
    needop.clear();
    needpermission.clear();
    executeat.clear();
    economys.clear();
    economyv.clear();
    
    untrigger_commands.clear();
    untrigger_givenpermissions.clear();
    untrigger_needop.clear();
    untrigger_needpermission.clear();
    untrigger_executeat.clear();
    untrigger_economys.clear();
    untrigger_economyv.clear();
    
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
      log.log(Level.SEVERE, "Failed to execute the query: " + query);
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
          switch (result.getInt("economystate")) {
            case 0:
              economys.put(id, economystate.NONE);
              break;
            case 1:
              economys.put(id, economystate.CHECK);
              break;
            case 2:
              economys.put(id, economystate.WITHDRAW);
              break;
            case 3:
              economys.put(id, economystate.GIVE);
              break;
            default:
              economys.put(id, economystate.NONE);
              break;
          }
          economyv.put(id, result.getDouble("economyvalue"));
          
          if(!result.getString("untrigger_command").isEmpty())
            untrigger_commands.put(id, result.getString("untrigger_command").split("\u0009"));
          if(!result.getString("untrigger_givenpermissions").isEmpty())
            untrigger_givenpermissions.put(id, result.getString("untrigger_givenpermissions").split("\u0009"));
          untrigger_needop.put(id, result.getBoolean("untrigger_operator"));
          untrigger_needpermission.put(id, ((result.getString("untrigger_needpermission").equalsIgnoreCase("") || result.getString("untrigger_needpermission") == null) ? "ecs.use" : result.getString("untrigger_needpermission")));
          if(!result.getString("untrigger_exeworld").isEmpty()) {
            untrigger_executeat.put(id, new Location(Bukkit.getWorld(result.getString("untrigger_exeworld")), result.getDouble("untrigger_exex"), result.getDouble("untrigger_exey"), result.getDouble("untrigger_exez")));
          }
          switch (result.getInt("untrigger_economystate")) {
            case 0:
              untrigger_economys.put(id, economystate.NONE);
              break;
            case 1:
              untrigger_economys.put(id, economystate.CHECK);
              break;
            case 2:
              untrigger_economys.put(id, economystate.WITHDRAW);
              break;
            case 3:
              untrigger_economys.put(id, economystate.GIVE);
              break;
            default:
              untrigger_economys.put(id, economystate.NONE);
              break;
          }
          untrigger_economyv.put(id, result.getDouble("economyvalue"));
          
          count++;
        } while(result.next());
      }
      logDebug(Level.INFO, dlvl.DEBUG, "Found " + count + " command sings!");
    } catch (SQLException e) {
      e.printStackTrace();
      db.close();
      return false;
    }
    db.close();
    
    return true;
  }
 
  private void createConfig() {
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
      }
      catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

  private boolean setupEconomy() {
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
      log.severe("Could not find the plugin Vault!");
      return false;
    }
    
    RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
    if (economyProvider == null) {
      log.severe("Could not find an economy provider! Maybe no Economy Plugin is installed!");
      return false;
    }
    economy = economyProvider.getProvider();

    return (economy != null);
  }
  
  
  
  //Part for Normal execution
  
  public void executecommands(Player pl, Block bl, PlayerInteractEvent evt) {
    if(positions.containsKey(bl.getLocation().clone())) {
      int id = positions.get(bl.getLocation().clone());
      if(needop.get(id) ? pl.isOp() : (pl.hasPermission("*") || pl.hasPermission("ecs.*") || pl.hasPermission("ecs.use.*") || pl.hasPermission("ecs.admin") || pl.hasPermission(needpermission.get(id)))) {
            //(plugin.needpermission.get(id)!=null ? pl.hasPermission(plugin.needpermission.get(id)) : true))) {
        if(getConfig().getBoolean("silentexecute")) {
          pl.sendMessage("[ECS] Performing commands assigned to this Sign.");
        }
        Location loc = pl.getLocation();
        
        if(useEconomy) {
          if(economys.get(id) == economystate.WITHDRAW) {
            EconomyResponse r = economy.withdrawPlayer(pl.getName(), economyv.get(id));
            if(r.transactionSuccess()) {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] For using this Sign " + economy.format(r.amount) + " was withdrawn from your account.");
              }
            } else {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] An error occured while withdrawing money from your account.");
                evt.setCancelled(true);
                return;
              }
            }
          } else if(economys.get(id) == economystate.GIVE) {
            EconomyResponse r = economy.depositPlayer(pl.getName(), economyv.get(id));
            if(r.transactionSuccess()) {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] This great Sign gave you " + economy.format(r.amount) + ".");
              }
            } else {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] An error occured while depositiong money to your account.");
                evt.setCancelled(true);
                return;
              }
            }
          } else if(economys.get(id) == economystate.CHECK) {
            if(economy.has(pl.getName(), economyv.get(id))) {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] This great Sign thinks that you have enough money.");
              }
            } else {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] You don't have enough money.");
                evt.setCancelled(true);
                return;
              }
            }
          }
        }
        
        HashMap<String, PermissionAttachment> att = new HashMap<String, PermissionAttachment>();
        if(givenpermissions.containsKey(id)) {
          for(String perm : givenpermissions.get(id)) {
            att.put(perm, pl.addAttachment(this, perm, true));
          }
        }
        
        if(executeat.containsKey(id)) {
          pl.teleport(executeat.get(id));
          log.info(pl.getName() + " is performing following commands through the block at x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " on \"" + bl.getWorld().getName() +
              "\" at position x:" + executeat.get(id).getX() + " y:" + executeat.get(id).getY() + " z:" + executeat.get(id).getZ() + " on \"" + executeat.get(id).getWorld().getName() +"\":");
        } else {
          log.info(pl.getName() + " is performing following commands through the block at x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " on \"" + bl.getWorld().getName() + "\":");
        }
        
        if(commands.containsKey(id)) {
          for(String com : commands.get(id)) {
            if(com != null && !com.equalsIgnoreCase("")) {
              HashMap<String, Object> validtemplates = new HashMap<String, Object>();
              validtemplates.put("player", pl.getName());
              for(String template : validtemplates.keySet()) {
                com=com.replace("%"+template+"%", validtemplates.get(template).toString());
              }
              log.info(pl.getDisplayName() + ": " + com);
              pl.chat(com);
            }
          }
        }
        if(!att.isEmpty()) {
          for(PermissionAttachment at : att.values()){
            pl.removeAttachment(at);
          }
          att.clear();
        }

        if(executeat.containsKey(id)) {
          pl.teleport(loc);
        }
      } else {
        pl.sendMessage("[ECS] You are not allowed to use this CommandSign.");
      }
      if(bl.getType() == Material.WALL_SIGN || bl.getType() == Material.SIGN_POST) {
        evt.setCancelled(true);
      }
    }
  }
  
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
    
    log.info(sender.getName() + " added the following Command to Sign with id " + positions.get(loc) + ": " + command);
    
    return true;
  }
  
  public boolean remcommand(Location loc, int index, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    if(index < 0) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a command from the Database. (Index out of bounds.)");  
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
      log.log(Level.SEVERE, "Failed to remove a command from the Database. (Index out of bounds. Maybe There is no command to remove left.)");
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

    needpermission.put(positions.get(loc), perm);
    
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
    
    needop.put(positions.get(loc), op);
    
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
      log.log(Level.SEVERE, "Failed to remove a permission from the Database. (Index out of bounds. Maybe There is no perm to remove left.)");
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

    String query = "";
    if(exeloc != null) {
      query = "UPDATE signs SET exex=" + exeloc.getX() + ", exey=" + exeloc.getY() + ", exez=" + exeloc.getZ() + ", exeworld=\"" + exeloc.getWorld().getName() + "\" WHERE id=" + positions.get(loc) + ";";
    } else {
      query = "UPDATE signs SET exex=0, exey=0, exez=0, exeworld=\"\" WHERE id=" + positions.get(loc) + ";";
    }
      
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
    
    if(exeloc != null) {
      log.info(sender.getName() + " set the following location on the Sign with id " + positions.get(loc) + ": " + exeloc.toString());
    } else {
      log.info(sender.getName() + " removed the location from the Sign with id " + positions.get(loc));
    }

    if(!loadSigns()) {
      return false;
    }
    
    return true;
  }
  
  public boolean addsign(Location loc, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    //String query = "INSERT into signs(world, x, y, z, command, givenpermissions, operator, needpermission, exeworld, exex, exey, exez)" +
    //		" VALUES (\"" + loc.getWorld().getName() + "\", " + (int)loc.getX() + ", " + (int)loc.getY() + ", " + (int)loc.getZ() + ", " + "\"\"" + ", " + "\"\"" + ", " + "0" + ", " + "\"ecs.use\"" + ", " + 
    //    "\"" + /*loc.getWorld().getName()*/ "" + "\", " + (loc.getX()+0.5) + ", " + (loc.getY()) + ", " + (loc.getZ()+0.5) + ");";
    
    String query = "INSERT into signs(world, x, y, z, command, givenpermissions, operator, needpermission, exeworld, exex, exey, exez, untrigger_command, untrigger_givenpermissions, untrigger_operator, untrigger_needpermission, untrigger_exeworld, untrigger_exex, untrigger_exey, untrigger_exez)" +
        " VALUES (\"" + loc.getWorld().getName() + "\", " + (int)loc.getX() + ", " + (int)loc.getY() + ", " + (int)loc.getZ() + ", " + "\"\"" + ", " + "\"\"" + ", " + "0" + ", " + "\"ecs.use\"" + ", " + 
        "\"" + /*loc.getWorld().getName()*/ "" + "\", " + (loc.getX()+0.5) + ", " + (loc.getY()) + ", " + (loc.getZ()+0.5) + ", " + "\"\"" + ", " + "\"\"" + ", " + "0" + ", " + "\"ecs.use\"" + ", " + 
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
  
  public boolean seteconomy(Location loc, economystate estate, double value, CommandSender sender) {
    if(!useEconomy) return false;
    
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET economystate=" + estate.ordinal() + ", economyvalue=\"" + value + "\" WHERE id=" + positions.get(loc) + ";";
    
    try {
      db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to set the Economy-State of a Sign in Database.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    if(!loadSigns()) {
      return false;
    }
    db.close();
    log.info(sender.getName() + " set the economy-actions in the database: " + positions.get(loc) + ": State " + estate + ", value " + value);
    
    return true;
  }
  
  
  //Part for Untrigger execution
  
  public void untrigger_executecommands(Player pl, Block bl, Cancellable evt) {
    if(positions.containsKey(bl.getLocation().clone())) {
      int id = positions.get(bl.getLocation().clone());
      if(untrigger_needop.get(id) ? pl.isOp() : (pl.hasPermission("*") || pl.hasPermission("ecs.*") || pl.hasPermission("ecs.use.*") || pl.hasPermission("ecs.admin") || pl.hasPermission(untrigger_needpermission.get(id)))) {
            //(plugin.needpermission.get(id)!=null ? pl.hasPermission(plugin.needpermission.get(id)) : true))) {
        if(getConfig().getBoolean("silentexecute")) {
          pl.sendMessage("[ECS] Performing untrigger_commands assigned to this Sign.");
        }
        Location loc = pl.getLocation();
        
        if(useEconomy) {
          if(untrigger_economys.get(id) == economystate.WITHDRAW) {
            EconomyResponse r = economy.withdrawPlayer(pl.getName(), untrigger_economyv.get(id));
            if(r.transactionSuccess()) {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] For using this Sign " + economy.format(r.amount) + " was withdrawn from your account.");
              }
            } else {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] An error occured while withdrawing money from your account.");
                evt.setCancelled(true);
                return;
              }
            }
          } else if(untrigger_economys.get(id) == economystate.GIVE) {
            EconomyResponse r = economy.depositPlayer(pl.getName(), untrigger_economyv.get(id));
            if(r.transactionSuccess()) {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] This great Sign gave you " + economy.format(r.amount) + ".");
              }
            } else {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] An error occured while depositiong money to your account.");
                evt.setCancelled(true);
                return;
              }
            }
          } else if(untrigger_economys.get(id) == economystate.CHECK) {
            if(economy.has(pl.getName(), untrigger_economyv.get(id))) {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] This great Sign thinks that you have enough money.");
              }
            } else {
              if(!getConfig().getBoolean("silentexecute")) {
                pl.sendMessage("[ECS] You don't have enough money.");
                evt.setCancelled(true);
                return;
              }
            }
          }
        }
        
        HashMap<String, PermissionAttachment> att = new HashMap<String, PermissionAttachment>();
        if(untrigger_givenpermissions.containsKey(id)) {
          for(String perm : untrigger_givenpermissions.get(id)) {
            att.put(perm, pl.addAttachment(this, perm, true));
          }
        }
        
        if(untrigger_executeat.containsKey(id)) {
          pl.teleport(untrigger_executeat.get(id));
          log.info(pl.getName() + " is performing following untrigger_commands through the block at x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " on \"" + bl.getWorld().getName() +
              "\" at position x:" + untrigger_executeat.get(id).getX() + " y:" + untrigger_executeat.get(id).getY() + " z:" + untrigger_executeat.get(id).getZ() + " on \"" + untrigger_executeat.get(id).getWorld().getName() +"\":");
        } else {
          log.info(pl.getName() + " is performing following untrigger_commands through the block at x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " on \"" + bl.getWorld().getName() + "\":");
        }
        
        if(untrigger_commands.containsKey(id)) {
          for(String com : untrigger_commands.get(id)) {
            if(com != null && !com.equalsIgnoreCase("")) {
              HashMap<String, Object> validtemplates = new HashMap<String, Object>();
              validtemplates.put("player", pl.getName());
              for(String template : validtemplates.keySet()) {
                com=com.replace("%"+template+"%", validtemplates.get(template).toString());
              }
              log.info(pl.getDisplayName() + ": " + com);
              pl.chat(com);
            }
          }
        }
        if(!att.isEmpty()) {
          for(PermissionAttachment at : att.values()){
            pl.removeAttachment(at);
          }
          att.clear();
        }

        if(untrigger_executeat.containsKey(id)) {
          pl.teleport(loc);
        }
      } else {
        pl.sendMessage("[ECS] You are not allowed to use this CommandSign.");
      }
      if(bl.getType() == Material.WALL_SIGN || bl.getType() == Material.SIGN_POST) {
        evt.setCancelled(true);
      }
    }
  }
  
  public boolean untrigger_addcommand(Location loc, String command, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "";
    String[] comman;
    if(!untrigger_commands.containsKey(positions.get(loc))) {
      comman = new String[1];
      query = "UPDATE signs SET untrigger_command=\"" + command + "\" WHERE id=" + positions.get(loc) + ";";
    } else {
      comman = new String[untrigger_commands.get(positions.get(loc)).length+1];
      String comm = "";
      for(String c : untrigger_commands.get(positions.get(loc))) {
        comm += c + "\u0009";
      }
      comm += command;
      query = "UPDATE signs SET untrigger_command=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
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
      comman[i] = untrigger_commands.get(positions.get(loc))[i];
    }
    comman[comman.length-1] = command;    
    untrigger_commands.put(positions.get(loc), comman);
    
    log.info(sender.getName() + " added the following Untrigger_Command to Sign with id " + positions.get(loc) + ": " + command);
    
    return true;
  }
  
  public boolean untrigger_remcommand(Location loc, int index, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    if(index < 0) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_command from the Database. (Index out of bounds.)");  
      return false;    
    }
    
    String query = "";
    String[] comman;
    if((untrigger_commands.containsKey(positions.get(loc)) ? untrigger_commands.get(positions.get(loc)).length : -1) > index) {
      comman = new String[untrigger_commands.get(positions.get(loc)).length-1];
      int i = 0, j = 0;
      for(String c : untrigger_commands.get(positions.get(loc))) {
        if(i!=index) {
          comman[j] = c;
          j++;
        }
        i++;
      }
      if(comman.length == 0) {
        query = "UPDATE signs SET untrigger_command=\"\" WHERE id=" + positions.get(loc) + ";";
      }
      else if(comman.length == 1) {
        query = "UPDATE signs SET untrigger_command=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[i];
        query = "UPDATE signs SET untrigger_command=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_command from the Database. (Index out of bounds. Maybe There is no command to remove left.)");
      return false;
    }
    
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_command from the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_command from the Database.");
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
     
    if(comman.length == 0) untrigger_commands.remove(positions.get(loc));
    else untrigger_commands.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Removed the untrigger_Command with index: " + index + " from Sign with id " + positions.get(loc));
    
    return true;
  }
  
  public boolean untrigger_modcommand(Location loc, int index, String replacement, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "";
    String[] comman;
    if(untrigger_commands.get(positions.get(loc)).length > index) {
      comman = new String[untrigger_commands.get(positions.get(loc)).length];
      comman = untrigger_commands.get(positions.get(loc));
      comman[index] = replacement;
      if(comman.length == 1) {
        query = "UPDATE signs SET untrigger_command=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(int i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[comman.length-1];
        query = "UPDATE signs SET untrigger_command=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a untrigger_command in the Database. (Index out of bounds.)");
      return false;
    }
    
    ResultSet result = null;
  
    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a untrigger_command in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to mod a untrigger_command in the Database.");
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
     
    untrigger_commands.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Modified the untrigger_Command with index: " + index + " in Sign with id " + positions.get(loc));
    
    return true;
  }
  
  public boolean untrigger_setneedperm(Location loc, String perm, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET untrigger_needpermission=\"" + perm + "\" WHERE id=" + positions.get(loc) + ";";
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to set the needed untrigger_permission in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to set the needed untrigger_permission in the Database. ResultSet is null.");
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
    
    untrigger_needpermission.put(positions.get(loc), perm);
    
    log.info(sender.getName() + "set the needed untrigger_permissions for the sign with id " + positions.get(loc) + ": " + perm);
    
    return true;
  }
  
  public boolean untrigger_setop(Location loc, boolean op, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET untrigger_operator=\"" + op + "\" WHERE id=" + positions.get(loc) + ";";
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
    
    untrigger_needop.put(positions.get(loc), op);
    
    log.info(sender.getName() + "set the needed permissions for the sign with id " + positions.get(loc) + ": " + op);
    
    return true;
  }
  
  public boolean untrigger_addperms(Location loc, String[] perms, CommandSender sender) {
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
    if(!untrigger_givenpermissions.containsKey(positions.get(loc))) {
      query = "UPDATE signs SET untrigger_givenpermissions=\"" + perm + "\" WHERE id=" + positions.get(loc) + ";";
      comm = perm;
    } else {
      for(String c : untrigger_givenpermissions.get(positions.get(loc))) {
        comm += c + "\u0009";
      }
      comm += perm;
      query = "UPDATE signs SET untrigger_givenpermissions=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
    }
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to add untrigger_permissions to the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to add a untrigger_permissions to the Database.");
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
    
    untrigger_givenpermissions.put(positions.get(loc), comm.split("\u0009"));
    
    log.info(sender.getName() + "added the following untrigger_permissions to Sign with id " + positions.get(loc) + ": " + perm);
    
    return true;
  }
  
  public boolean untrigger_remperm(Location loc, int index, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    if(index < 0) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_permission from the Database. (Index out of bounds.)");  
      return false;    
    }
    
    String query = "";
    String[] comman;
    if((untrigger_givenpermissions.containsKey(positions.get(loc)) ? untrigger_givenpermissions.get(positions.get(loc)).length : -1) > index) {
      comman = new String[untrigger_givenpermissions.get(positions.get(loc)).length-1];
      int i = 0, j = 0;
      for(String c : untrigger_givenpermissions.get(positions.get(loc))) {
        if(i!=index) {
          comman[j] = c;
          j++;
        }
        i++;
      }
      if(comman.length == 0) {
        query = "UPDATE signs SET untrigger_givenpermissions=\"\" WHERE id=" + positions.get(loc) + ";";
      }
      else if(comman.length == 1) {
        query = "UPDATE signs SET untrigger_givenpermissions=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[i];
        query = "UPDATE signs SET untrigger_givenpermissions=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_permission from the Database. (Index out of bounds. Maybe There is no perm to remove left.)");
      return false;
    }
    
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_permission from the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to remove a untrigger_permission from the Database.");
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
     
    if(comman.length == 0) untrigger_givenpermissions.remove(positions.get(loc));
    else untrigger_givenpermissions.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Removed the untrigger_Permission with index: " + index + " from Sign with id " + positions.get(loc) + ".");
    
    return true;
  }
  
  public boolean untrigger_modperm(Location loc, int index, String replacement, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "";
    String[] comman = new String[untrigger_givenpermissions.get(positions.get(loc)).length];
    if(untrigger_givenpermissions.get(positions.get(loc)).length > index) {
      comman = untrigger_givenpermissions.get(positions.get(loc));
      comman[index] = replacement;
      if(comman.length == 1) {
        query = "UPDATE signs SET untrigger_givenpermissions=\"" + comman[0] + "\" WHERE id=" + positions.get(loc) + ";";
      } else {
        String comm = "";
        for(int i=0; i<comman.length-1; i++) {
          comm += comman[i] + "\u0009";
        }
        comm += comman[comman.length-1];
        query = "UPDATE signs SET untrigger_givenpermissions=\"" + comm + "\" WHERE id=" + positions.get(loc) + ";";
      }      
    } else {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a untrigger_permission in the Database. (Index out of bounds.)");
      return false;
    }
    
    ResultSet result = null;
  
    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to mod a untrigger_permission in the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to mod a untrigger_permission in the Database.");
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
     
    untrigger_givenpermissions.put(positions.get(loc), comman);
    
    log.info(sender.getName() + "Modified the untrigger_permission with index: " + index + " in Sign with id " + positions.get(loc));
    
    return true;
  }
  
  public boolean untrigger_setloc(Location loc, Location exeloc, CommandSender sender) {
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }

    String query = "";
    if(exeloc != null) {
      query = "UPDATE signs SET untrigger_exex=" + exeloc.getX() + ", untrigger_exey=" + exeloc.getY() + ", untrigger_exez=" + exeloc.getZ() + ", untrigger_exeworld=\"" + exeloc.getWorld().getName() + "\" WHERE id=" + positions.get(loc) + ";";
    } else {
      query = "UPDATE signs SET untrigger_exex=0, untrigger_exey=0, untrigger_exez=0, untrigger_exeworld=\"\" WHERE id=" + positions.get(loc) + ";";
    }
      
    ResultSet result = null;

    try {
      result = db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to add untrigger_location to the Database.");
      e.printStackTrace();
      return false;
    }
    if(result == null){
      db.close();
      log.log(Level.SEVERE, "Failed to add a untrigger_location to the Database.");
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
    
    if(exeloc != null) {
      log.info(sender.getName() + " set the following untrigger_location on the Sign with id " + positions.get(loc) + ": " + exeloc.toString());
    } else {
      log.info(sender.getName() + " removed the untrigger_location from the Sign with id " + positions.get(loc));
    }

    if(!loadSigns()) {
      return false;
    }
    
    return true;
  }

  public boolean untrigger_seteconomy(Location loc, economystate estate, double value, CommandSender sender) {
    if(!useEconomy) return false;
    
    if(!db.open()) {
      log.log(Level.SEVERE, "Failed to connect to the Database.");
      return false;
    }
    
    String query = "UPDATE signs SET untrigger_economystate=" + estate.ordinal() + ", untrigger_economyvalue=\"" + value + "\" WHERE id=" + positions.get(loc) + ";";
    
    try {
      db.query(query);
    } catch (Exception e) {
      db.close();
      log.log(Level.SEVERE, "Failed to set the untrigger_Economy-State of a Sign in Database.");
      e.printStackTrace();
      return false;
    }
    db.close();
    
    if(!loadSigns()) {
      return false;
    }
    db.close();
    log.info(sender.getName() + " set the untrigger_economy-actions in the database: " + positions.get(loc) + ": State " + estate + ", value " + value);
    
    return true;
  }
  
  
  
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("ecs")) {
      if(args.length > 0) {
        if(args[0].equalsIgnoreCase("reload")) {
          //SECTION: RELOAD
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
          //SECTION: CREATE NEW SIGN
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
          //SECTION: GET INFO ABOUR SIGN
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
          //SECTION: ADD A COMMAND TO A SIGN
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
          //SECTION: REMOVE A COMMAND FROM A SIGN
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
          //SECTION: MODIFY A COMMAND ON A SIGN
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
          //SECTION: ADD A PERMISSION TO A SIGN
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
          //SECTION: REMOVE A PERMISSION FROM A SIGN
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
          //SECTION: MODIFY A PERMISSION ON A SIGN
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
                            if(!modperm(ent.getKey(), index, cmd, sender)) {
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
          //SECTION: SET THE PERMISSION WHICH IS NEEDED FOR USING THE SIGN
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
        } else if(args[0].equalsIgnoreCase("seteconomy")) {
          //SECTION: MODIFY A COMMAND ON A SIGN
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
                            economystate ecos = economystate.NONE;
                            if(args[3].equalsIgnoreCase("Withdraw")) {
                              ecos = economystate.WITHDRAW;
                            } else if(args[3].equalsIgnoreCase("Check")) {
                              ecos = economystate.CHECK;
                            } else if(args[3].equalsIgnoreCase("Give")) {
                              ecos = economystate.GIVE;
                            }

                            double value = 0;
                            if(args.length > 4) {
                              try {
                                value = Double.parseDouble(args[4]);
                              } catch(NumberFormatException e) {
                                sender.sendMessage("[ECS] Your value is not a parsable double/float value.");
                                return true;
                              }
                            }
                            if(!seteconomy(ent.getKey(), ecos, value, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the economy state, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully modified the economy state in the sign.");
                              return true;
                            }
                          } else {
                            sender.sendMessage("[ECS] Please specify an action!");
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
                  economystate ecos = economystate.NONE;
                  if(args[1].equalsIgnoreCase("Withdraw")) {
                    ecos = economystate.WITHDRAW;
                  } else if(args[1].equalsIgnoreCase("Check")) {
                    ecos = economystate.CHECK;
                  } else if(args[1].equalsIgnoreCase("Give")) {
                    ecos = economystate.GIVE;
                  } 

                  double value = 0;
                  try {
                    value = Double.parseDouble(args[2]);
                  } catch(NumberFormatException e) {
                    sender.sendMessage("[ECS] Your value is not a parsable double/float value.");
                    return true;
                  }
                  
                  if(playerstate.get((Player) sender) != state.SETECONOMY) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETECONOMY);
                    
                    commandstore.put((Player) sender, ecos);
                    commandstore2.put((Player) sender, value);
                    sender.sendMessage("[ECS] To modify the economy state of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted modifying!");
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
        } else if(args[0].equalsIgnoreCase("setloc")) {
          //SECTION: SET THE LOCATION OF EXECUTION
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
                            if (!args[3].equalsIgnoreCase("none")) {
                              if(!setloc(ent.getKey(), ((Player) sender).getLocation(), sender)) {
                                sender.sendMessage("[ECS] A problem occured while setting the executing location of the sign, please view the server log for further information.");
                                return true;
                              } else {
                                sender.sendMessage("[ECS] Successfully set the executing location of the sign to x:" + ((Player) sender).getLocation().getX() + " y:" + ((Player) sender).getLocation().getY() + " z:" + ((Player) sender).getLocation().getZ() + " in world:" + ((Player) sender).getLocation().getWorld().getName());
                                return true;
                              }
                            } else {
                              if(!setloc(ent.getKey(), null, sender)) {
                                sender.sendMessage("[ECS] A problem occured while setting the executing location of the sign, please view the server log for further information.");
                                return true;
                              } else {
                                sender.sendMessage("[ECS] Successfully removed the executing location of the sign");
                                return true;
                              }
                            }
                          } else {
                            if(!setloc(ent.getKey(), ((Player) sender).getLocation(), sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the executing location of the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the executing location of the sign to x:" + ((Player) sender).getLocation().getX() + " y:" + ((Player) sender).getLocation().getY() + " z:" + ((Player) sender).getLocation().getZ() + " in world:" + ((Player) sender).getLocation().getWorld().getName());
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
                } else if (args[1].equalsIgnoreCase("none")) {
                  if(playerstate.get((Player) sender) != state.SETLOCATION) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETLOCATION);
                    
                    commandstore.put((Player) sender, null);
                    sender.sendMessage("[ECS] To set the executing location of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    commandstore.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted setting location!");
                    return true;
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
          //SECTION: SETS WHETHER YOU NEED OP OR NOT
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
          //SECTION: REMOVE THE ATTACHMENT TO A SIGN
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
        } 
        
        //PART FOR UNTRIGGER
        else if(args[0].equalsIgnoreCase("addcommandut")) {
          //SECTION: ADD A COMMAND TO A SIGN
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
                            if(!untrigger_addcommand(ent.getKey(), cmd, sender)) {
                              sender.sendMessage("[ECS] A problem occured while adding a untrigger_command, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully added the untrigger_command to the sign:" + cmd);
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
                  if(playerstate.get((Player) sender) != state.ADDCOMMANDUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.ADDCOMMANDUT);
                    
                    String cmd = "";
                    for(int i=1; i<args.length; i++) {
                      if(i < args.length-1) {
                        cmd += args[i]+" ";
                      } else {
                        cmd += args[i];
                      }
                    }
                    commandstore.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To add the untrigger_command to a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted adding untrigger_command!");
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
        } else if(args[0].equalsIgnoreCase("remcommandut")) {
          //SECTION: REMOVE A COMMAND FROM A SIGN
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
                            if(!untrigger_remcommand(ent.getKey(), index, sender)) {
                              sender.sendMessage("[ECS] A problem occured while removing a untrigger_command, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully removed the untrigger_command with id " + index + " from the sign.");
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
                  if(playerstate.get((Player) sender) != state.REMCOMMANDUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.REMCOMMANDUT);
                    
                    commandstore.put((Player) sender, index);
                    sender.sendMessage("[ECS] To remove the untrigger_command from a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing untrigger_command!");
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
        } else if(args[0].equalsIgnoreCase("modcommandut")) {
          //SECTION: MODIFY A COMMAND ON A SIGN
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
                            if(!untrigger_modcommand(ent.getKey(), index, cmd, sender)) {
                              sender.sendMessage("[ECS] A problem occured while modifying a untrigger_command, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully modified the untrigger_command with id " + index + " in the sign.");
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
                  if(playerstate.get((Player) sender) != state.MODCOMMANDUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.MODCOMMANDUT);
                    
                    commandstore.put((Player) sender, index);
                    commandstore2.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To modify the untrigger_command in a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing untrigger_command!");
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
        } else if(args[0].equalsIgnoreCase("addpermut")) {
          //SECTION: ADD A PERMISSION TO A SIGN
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
                            if(!untrigger_addperms(ent.getKey(), perms, sender)) {
                              sender.sendMessage("[ECS] A problem occured while adding some untrigger_permissions to the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully added untrigger_permissions to the sign.");
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
                  if(playerstate.get((Player) sender) != state.ADDGIVEPERMUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.ADDGIVEPERMUT);
                    
                    String[] cmd = new String[args.length - 1];
                    for(int i=0; i<cmd.length; i++) {
                      cmd[i]=args[i+1];
                    }
                    commandstore.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To add the untrigger_permissions to a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted adding untrigger_permissions!");
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
        } else if(args[0].equalsIgnoreCase("rempermut")) {
          //SECTION: REMOVE A PERMISSION FROM A SIGN
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
                            if(!untrigger_remperm(ent.getKey(), index, sender)) {
                              sender.sendMessage("[ECS] A problem occured while removing a untrigger_permission, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully removed the untrigger_permission with id " + index + " from the sign.");
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
                  if(playerstate.get((Player) sender) != state.REMPERMUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.REMPERMUT);
                    
                    commandstore.put((Player) sender, index);
                    sender.sendMessage("[ECS] To remove the untrigger_permission from a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing untrigger_permission!");
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
        } else if(args[0].equalsIgnoreCase("modpermut")) {
          //SECTION: MODIFY A PERMISSION ON A SIGN
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
                            if(!untrigger_modperm(ent.getKey(), index, cmd, sender)) {
                              sender.sendMessage("[ECS] A problem occured while modifying a untrigger_permission, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully modified the untrigger_permission with id " + index + " in the sign.");
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
                  if(playerstate.get((Player) sender) != state.MODPERMUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.MODPERMUT);
                    
                    commandstore.put((Player) sender, index);
                    commandstore2.put((Player) sender, cmd);
                    sender.sendMessage("[ECS] To modify the untrigger_permission in a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted removing untrigger_permission!");
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
        } else if(args[0].equalsIgnoreCase("setpermut")) {
          //SECTION: SET THE PERMISSION WHICH IS NEEDED FOR USING THE SIGN
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
                            if(!untrigger_setneedperm(ent.getKey(), perm, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the untrigger_permission to the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the required untrigger_permission of this sign to " + perm);
                              return true;
                            }
                          } else {
                            if(!untrigger_setneedperm(ent.getKey(), "ecs.use", sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the untrigger_permission to the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the required untrigger_permission of this sign to ecs.use.");
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
                  if(playerstate.get((Player) sender) != state.SETNEEDPERMUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETNEEDPERMUT);
                    
                    commandstore.put((Player) sender, args[1]);
                    sender.sendMessage("[ECS] To set the required untrigger_permission of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    commandstore.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted setting untrigger_permission!");
                    return true;
                  }
                }
              } else {
                if(playerstate.get((Player) sender) != state.SETNEEDPERMUT) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.SETNEEDPERMUT);
                  
                  commandstore.put((Player) sender, "ecs.use");
                  sender.sendMessage("[ECS] To set the required untrigger_permission of a sign to default, please click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  commandstore.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted setting untrigger_permission!");
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
        } else if(args[0].equalsIgnoreCase("seteconomyut")) {
          //SECTION: MODIFY A COMMAND ON A SIGN
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
                            economystate ecos = economystate.NONE;
                            if(args[3].equalsIgnoreCase("Withdraw")) {
                              ecos = economystate.WITHDRAW;
                            } else if(args[3].equalsIgnoreCase("Check")) {
                              ecos = economystate.CHECK;
                            } else if(args[3].equalsIgnoreCase("Give")) {
                              ecos = economystate.GIVE;
                            }

                            double value = 0;
                            if(args.length > 4) {
                              try {
                                value = Double.parseDouble(args[4]);
                              } catch(NumberFormatException e) {
                                sender.sendMessage("[ECS] Your value is not a parsable double/float value.");
                                return true;
                              }
                            }
                            if(!untrigger_seteconomy(ent.getKey(), ecos, value, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the untrigger_economy state, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully modified the untrigger_economy state in the sign.");
                              return true;
                            }
                          } else {
                            sender.sendMessage("[ECS] Please specify an action!");
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
                  economystate ecos = economystate.NONE;
                  if(args[1].equalsIgnoreCase("Withdraw")) {
                    ecos = economystate.WITHDRAW;
                  } else if(args[1].equalsIgnoreCase("Check")) {
                    ecos = economystate.CHECK;
                  } else if(args[1].equalsIgnoreCase("Give")) {
                    ecos = economystate.GIVE;
                  } 

                  double value = 0;
                  try {
                    value = Double.parseDouble(args[2]);
                  } catch(NumberFormatException e) {
                    sender.sendMessage("[ECS] Your value is not a parsable double/float value.");
                    return true;
                  }
                  
                  if(playerstate.get((Player) sender) != state.SETECONOMYUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETECONOMYUT);
                    
                    commandstore.put((Player) sender, ecos);
                    commandstore2.put((Player) sender, value);
                    sender.sendMessage("[ECS] To modify the untrigger_economy state of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted modifying!");
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
        } else if(args[0].equalsIgnoreCase("setlocut")) {
          //SECTION: SET THE LOCATION OF EXECUTION
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
                            if (!args[3].equalsIgnoreCase("none")) {
                              if(!untrigger_setloc(ent.getKey(), ((Player) sender).getLocation(), sender)) {
                                sender.sendMessage("[ECS] A problem occured while setting the untrigger_executing location of the sign, please view the server log for further information.");
                                return true;
                              } else {
                                sender.sendMessage("[ECS] Successfully set the untrigger_executing location of the sign to x:" + ((Player) sender).getLocation().getX() + " y:" + ((Player) sender).getLocation().getY() + " z:" + ((Player) sender).getLocation().getZ() + " in world:" + ((Player) sender).getLocation().getWorld().getName());
                                return true;
                              }
                            } else {
                              if(!untrigger_setloc(ent.getKey(), null, sender)) {
                                sender.sendMessage("[ECS] A problem occured while setting the untrigger_executing location of the sign, please view the server log for further information.");
                                return true;
                              } else {
                                sender.sendMessage("[ECS] Successfully removed the untrigger_executing location of the sign");
                                return true;
                              }
                            }
                          } else {
                            if(!untrigger_setloc(ent.getKey(), ((Player) sender).getLocation(), sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the untrigger_executing location of the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the untrigger_executing location of the sign to x:" + ((Player) sender).getLocation().getX() + " y:" + ((Player) sender).getLocation().getY() + " z:" + ((Player) sender).getLocation().getZ() + " in world:" + ((Player) sender).getLocation().getWorld().getName());
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
                } else if (args[1].equalsIgnoreCase("none")) {
                  if(playerstate.get((Player) sender) != state.SETLOCATIONUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETLOCATIONUT);
                    
                    commandstore.put((Player) sender, null);
                    sender.sendMessage("[ECS] To set the untrigger_executing location of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    commandstore.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted setting untrigger_location!");
                    return true;
                  }
                } else {
                  sender.sendMessage("[ECS] Too few arguments.");
                }
              } else {
                if(playerstate.get((Player) sender) != state.SETLOCATIONUT) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.SETLOCATIONUT);
                  
                  commandstore.put((Player) sender, ((Player)sender).getLocation());
                  sender.sendMessage("[ECS] To set the untrigger_executing location of a sign, please click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  commandstore.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted setting untrigger_location!");
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
        }  else if(args[0].equalsIgnoreCase("setoput")) {
          //SECTION: SETS WHETHER YOU NEED OP OR NOT
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
                            if(!untrigger_setop(ent.getKey(), state, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the untrigger_onlyop state of the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the untrigger_onlyop state of this sign to " + state);
                              return true;
                            }
                          } else {
                            if(!untrigger_setop(ent.getKey(), false, sender)) {
                              sender.sendMessage("[ECS] A problem occured while setting the untrigger_onlyop state of the sign, please view the server log for further information.");
                              return true;
                            } else {
                              sender.sendMessage("[ECS] Successfully set the untrigger_onlyop state of this sign to " + false);
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
                  if(playerstate.get((Player) sender) != state.SETNEEDOPUT) {
                    playerstate.remove((Player) sender);
                    playerstate.put((Player) sender, state.SETNEEDOPUT);
                    
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
                    sender.sendMessage("[ECS] To set the untrigger_onlyop state of a sign, please click on it.");
                    return true;
                  } else {
                    playerstate.remove((Player) sender);
                    commandstore.remove((Player) sender);
                    sender.sendMessage("[ECS] Aborted setting untrigger_permission!");
                    return true;
                  }
                }
              } else {
                if(playerstate.get((Player) sender) != state.SETNEEDOPUT) {
                  playerstate.remove((Player) sender);
                  playerstate.put((Player) sender, state.SETNEEDOPUT);
                  
                  commandstore.put((Player) sender, false);
                  sender.sendMessage("[ECS] To set the untrigger_onlyop state of a sign, please click on it.");
                  return true;
                } else {
                  playerstate.remove((Player) sender);
                  commandstore.remove((Player) sender);
                  sender.sendMessage("[ECS] Aborted setting untrigger_permission!");
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
          //SECTION: REMOVE THE ATTACHMENT TO A SIGN
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
          sender.sendMessage("Please use a proper subcommand!");
          return false;
        }
      }
    }
    return true;
  }
}