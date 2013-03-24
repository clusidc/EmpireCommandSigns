package clusidc.EmpireCommandSigns;

import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import clusidc.EmpireCommandSigns.EmpireCommandSigns.state;

public class EventListener implements Listener {
  
  public EmpireCommandSigns plugin;
  
  public EventListener(EmpireCommandSigns plugin) {
    this.plugin = plugin;
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent evt) {
    if(plugin.positions.get(evt.getBlock().getLocation().clone()) !=null) {
      evt.getPlayer().sendMessage("[ECS] You must first unregister this sign!");
      evt.setCancelled(true);
      evt.getPlayer().sendBlockChange(evt.getBlock().getLocation(), evt.getBlock().getType(), evt.getBlock().getData());
    }
  }
  
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent evt) {
    if(evt.hasBlock() && evt.getAction() == Action.RIGHT_CLICK_BLOCK) {
      Player pl = evt.getPlayer();
      Block bl = evt.getClickedBlock();
      if(evt.getClickedBlock().getType() == Material.WALL_SIGN || evt.getClickedBlock().getType() == Material.SIGN_POST || evt.getClickedBlock().getTypeId() == 70 || evt.getClickedBlock().getTypeId() == 77 || evt.getClickedBlock().getTypeId() == 143) {
        //IF state NONE
        if(plugin.playerstate.get(pl) == state.NONE || plugin.playerstate.containsKey(pl) == false) {
          if(plugin.positions.containsKey(bl.getLocation().clone())) {
            int id = plugin.positions.get(bl.getLocation().clone());
            if(plugin.needop.get(id) ? pl.isOp() : (pl.hasPermission("*") || pl.hasPermission("ecs.*") || pl.hasPermission("ecs.use.*") || pl.hasPermission("ecs.admin") || pl.hasPermission(plugin.needpermission.get(id)))) {
                  //(plugin.needpermission.get(id)!=null ? pl.hasPermission(plugin.needpermission.get(id)) : true))) {
              pl.sendMessage("[ECS] Performing commands assigned to this Sign.");
              Location loc = pl.getLocation();
              
              HashMap<String, PermissionAttachment> att = new HashMap<String, PermissionAttachment>();
              if(plugin.givenpermissions.containsKey(id)) {
                for(String perm : plugin.givenpermissions.get(id)) {
                  att.put(perm, pl.addAttachment(this.plugin, perm, true));
                }
              }
              
              if(plugin.executeat.containsKey(id)) {
                pl.teleport(plugin.executeat.get(id));
                plugin.log.info(pl.getName() + " is performing following commands through the block at x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " on " + bl.getWorld().getName() +
                    " at position x:" + plugin.executeat.get(id).getX() + " y:" + plugin.executeat.get(id).getY() + " z:" + plugin.executeat.get(id).getZ() + " on " + plugin.executeat.get(id).getWorld().getName() +":");
              } else {
                plugin.log.info(pl.getName() + " is performing following commands through the block at x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " on " + bl.getWorld().getName() + ":");
              }
              
              if(plugin.commands.containsKey(id)) {
                for(String com : plugin.commands.get(id)) {
                  if(com != null && !com.equalsIgnoreCase("")) {
                    HashMap<String, Object> validtemplates = new HashMap<String, Object>();
                    validtemplates.put("player", pl.getName());
                    for(String template : validtemplates.keySet()) {
                      com=com.replace("%"+template+"%", validtemplates.get(template).toString());
                    }
                    plugin.log.info(pl.getDisplayName() + ": " + com);
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

              if(plugin.executeat.containsKey(id)) {
                pl.teleport(loc);
              }
              plugin.playerstate.remove(pl);
              evt.setCancelled(true);
            } else {
              pl.sendMessage("[ECS] You are not allowed to use this CommandSign.");
              plugin.playerstate.remove(pl);
              evt.setCancelled(true);
            }
          }
        } else if(plugin.playerstate.get(pl) == state.CREATE) { //If STATE CREATE
          evt.setCancelled(true);
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              pl.sendMessage("[ECS] The command sign is already created.");
            } else {
              if(plugin.addsign(bl.getLocation().clone(), (CommandSender) pl)) {
                pl.sendMessage("[ECS] Sign registered with id " + plugin.positions.get(bl.getLocation().clone()));
              } else {
                pl.sendMessage("[ECS] Cannot register the sign, please look up the server log for further information!");
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
            }
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.ADDCOMMAND) { //If STATE ADDCOMMAND
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.addcommand(bl.getLocation().clone(), (String)plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while adding a command, please view the server log for further information." + plugin.commandstore.get(pl));
              } else {
                pl.sendMessage("[ECS] Successfully added the command to the sign: " + plugin.commandstore.get(pl));
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              evt.setCancelled(true);
            } else {
              pl.sendMessage("[ECS] Sign is not registered now, registering it...");
              if(plugin.addsign(bl.getLocation().clone(), (CommandSender) pl)) {
                pl.sendMessage("[ECS] Sign registered with id " + plugin.positions.get(bl.getLocation().clone()));
              } else {
                pl.sendMessage("[ECS] Cannot register the sign, please look up the server log for further information!");
                plugin.log.info(pl.getName() + " tried to register a sign.");
              }
              if(!plugin.addcommand(bl.getLocation().clone(), (String)plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while adding a command, please view the server log for further information." + plugin.commandstore.get(pl));
              } else {
                pl.sendMessage("[ECS] Successfully added the command to the sign: " + plugin.commandstore.get(pl));
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              evt.setCancelled(true);
            }
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.REMCOMMAND) { //If STATE REMCOMMAND
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.remcommand(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while removing a command, please view the server log for further information." + plugin.commandstore.get(pl));
              } else {
                pl.sendMessage("[ECS] Successfully removed the command with id " + plugin.commandstore.get(pl) + " from the sign.");
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              evt.setCancelled(true);
            } else {
              pl.sendMessage("[ECS] Sign is not registered now, aborting...");
              
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              evt.setCancelled(true);
            }
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.MODCOMMAND) { //If STATE REMPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.modcommand(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (String)plugin.commandstore2.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while modifying a command, please view the server log for further information." + plugin.commandstore.get(pl));
              } else {
                pl.sendMessage("[ECS] Successfully modified the command with id " + plugin.commandstore.get(pl) + " in the sign.");
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              plugin.commandstore2.remove(pl);
              evt.setCancelled(true);
            } else {
              pl.sendMessage("[ECS] Sign is not registered now, aborting...");
              
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              plugin.commandstore2.remove(pl);
              evt.setCancelled(true);
            }
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
            evt.setCancelled(true);
          }
        } else if(plugin.playerstate.get(pl) == state.ADDGIVEPERM) { //If STATE ADDGIVEPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.addperms(bl.getLocation().clone(), (String[])plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while adding some permissions to the sign, please view the server log for further information.");
              } else {
                pl.sendMessage("[ECS] Successfully added permissions to the sign.");
              }
            } else {
              pl.sendMessage("[ECS] Sign is not registered, please create it first! Aborted adding permissions!");
            }
            plugin.playerstate.remove(pl);
            plugin.commandstore.remove(pl);
            evt.setCancelled(true);
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.REMPERM) { //If STATE REMPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.remperm(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while removing a permission, please view the server log for further information." + plugin.commandstore.get(pl));
              } else {
                pl.sendMessage("[ECS] Successfully removed the permission with id " + plugin.commandstore.get(pl) + " from the sign.");
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              evt.setCancelled(true);
            } else {
              pl.sendMessage("[ECS] Sign is not registered now, aborting...");
              
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              evt.setCancelled(true);
            }
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.MODPERM) { //If STATE REMPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.modperm(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (String)plugin.commandstore2.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while modifying a permission, please view the server log for further information." + plugin.commandstore.get(pl));
              } else {
                pl.sendMessage("[ECS] Successfully modified the permission with id " + plugin.commandstore.get(pl) + " in the sign.");
              }
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              plugin.commandstore2.remove(pl);
              evt.setCancelled(true);
            } else {
              pl.sendMessage("[ECS] Sign is not registered now, aborting...");
              
              plugin.playerstate.remove(pl);
              plugin.commandstore.remove(pl);
              plugin.commandstore2.remove(pl);
              evt.setCancelled(true);
            }
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.SETNEEDPERM) { //If STATE ADDGIVEPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.setneedperm(bl.getLocation().clone(), (String)plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while setting the needed permission to the sign, please view the server log for further information.");
              } else {
                pl.sendMessage("[ECS] Successfully set the needed permission of the sign.");
              }
            } else {
              pl.sendMessage("[ECS] Sign is not registered, please create it first! Aborted adding permissions!");
            }
            plugin.playerstate.remove(pl);
            plugin.commandstore.remove(pl);
            evt.setCancelled(true);
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.SETNEEDOP) { //If STATE ADDGIVEPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.setop(bl.getLocation().clone(), (Boolean)plugin.commandstore.get(pl), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while setting the onlyop state to the sign, please view the server log for further information.");
              } else {
                pl.sendMessage("[ECS] Successfully set the onlyop state of the sign.");
              }
            } else {
              pl.sendMessage("[ECS] Sign is not registered, please create it first! Aborted adding permissions!");
            }
            plugin.playerstate.remove(pl);
            plugin.commandstore.remove(pl);
            evt.setCancelled(true);
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.SETLOCATION) { //If STATE ADDGIVEPERM
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.setloc(bl.getLocation().clone(), (plugin.commandstore.get(pl) == null ? null : (Location)plugin.commandstore.get(pl)) , (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while setting the executing location of the sign, please view the server log for further information.");
              } else {
                if(plugin.commandstore.get(pl) == null) {
                  pl.sendMessage("[ECS] Successfully removed the executing location from the sign.");
                } else {
                  pl.sendMessage("[ECS] Successfully set the executing location of the sign to x:" + ((Location)plugin.commandstore.get(pl)).getX() + " y:" + ((Location)plugin.commandstore.get(pl)).getY() + " z:" + ((Location)plugin.commandstore.get(pl)).getZ() + " in world:" + ((Location)plugin.commandstore.get(pl)).getWorld().getName());                  
                }
              }
            } else {
              pl.sendMessage("[ECS] Sign is not registered, please create it first! Aborted adding permissions!");
            }
            plugin.playerstate.remove(pl);
            plugin.commandstore.remove(pl);
            evt.setCancelled(true);
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to create a sign.");
          }
        } else if(plugin.playerstate.get(pl) == state.INFO) { //If STATE ADDCOMMAND
          if(plugin.positions.containsKey(bl.getLocation().clone())) {
            if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin") || pl.hasPermission("ecs.info")) {
              int id = plugin.positions.get(bl.getLocation().clone());
              pl.sendMessage("[ECB] Information to this Sign (id: " + id + "):");
              pl.sendMessage(" |-> x:" + bl.getX() + " y:" + bl.getY() + " z:" + bl.getZ() + " world:" + bl.getWorld().getName());
              if(plugin.executeat.containsKey(id)) {
                pl.sendMessage(" |-, Executing at: ");
                Location loc = plugin.executeat.get(id);
                pl.sendMessage("   |-> x:" + Math.round(loc.getX()) + " y:" + Math.round(loc.getY()) + " z:" + Math.round(loc.getZ()) + " world:" + loc.getWorld().getName());
              }              
              if(plugin.needop.get(id)) {
                pl.sendMessage(" |-> You need OP for this Sign to use.");
              } else {
                pl.sendMessage(" |-> Needed Permission: " + plugin.needpermission.get(id));
              }
              if(plugin.commands.containsKey(id)) {
                pl.sendMessage(" |-, Commands: ");
                String com;              
                for(int i=0; i<plugin.commands.get(id).length; i++) {
                  com = plugin.commands.get(id)[i];
                  pl.sendMessage("   |-> " + i + ": " + com);
                }
              }
              if(plugin.givenpermissions.containsKey(id)) {
                pl.sendMessage(" |-, Given Permissions");
                String perm;
                for(int i=0; i<plugin.givenpermissions.get(id).length; i++) {
                  perm = plugin.givenpermissions.get(id)[i];
                  pl.sendMessage("   |-> " + i + ": " + perm);
                }
              }
            } else {
              pl.sendMessage("[ECB] You don't have the permissions to view the attached commands of this sign!");
            }
          } else {
            pl.sendMessage("[ECS] This is not a sign! aborting info..");
          }
          plugin.playerstate.remove(pl);
          plugin.commandstore.remove(pl);
          evt.setCancelled(true);
        } else if(plugin.playerstate.get(pl) == state.REMOVE) { //If STATE REMOVE
          if(pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin")) {
            if(plugin.positions.containsKey(bl.getLocation().clone())) {
              if(!plugin.delsign(bl.getLocation().clone(), (CommandSender) pl)) {
                pl.sendMessage("[ECS] A problem occured while removing this sign from the datatabase, please view the server log for further information.");
              } else {
                pl.sendMessage("[ECS] Successfully removed the sign:" + bl.getLocation().toVector().toString());
              }
            } else {
              pl.sendMessage("[ECS] There is no sign registered at this position.");
            }
            plugin.playerstate.remove(pl);
            plugin.commandstore.remove(pl);
            evt.setCancelled(true);
          } else {
            pl.sendMessage("[ECS] You don't have enough permission to remove a sign from the database.");
          }
        }
      } else if(!(plugin.playerstate.get(pl) == state.NONE || plugin.playerstate.containsKey(pl) == false)) {
        pl.sendMessage("[ECS] This is not a sign. Action aborted!");
        plugin.commandstore.remove(pl);
        plugin.playerstate.remove(pl);
        evt.setCancelled(true);
      }
    }
  }
}