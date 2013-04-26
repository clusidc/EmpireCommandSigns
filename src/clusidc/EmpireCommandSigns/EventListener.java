package clusidc.EmpireCommandSigns;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import clusidc.EmpireCommandSigns.EmpireCommandSigns.state;
import clusidc.EmpireCommandSigns.EmpireCommandSigns.economystate;;

public class EventListener implements Listener {
  
  public EmpireCommandSigns plugin;
  
  public EventListener(EmpireCommandSigns plugin) {
    this.plugin = plugin;
  }
  
  private boolean isClick(PlayerInteractEvent evt){
    return (evt.getAction() == Action.RIGHT_CLICK_BLOCK || (plugin.getConfig().getBoolean("leftclick", false) ? evt.getAction() == Action.LEFT_CLICK_BLOCK : false));
  }
  
  @EventHandler
  public void onBlockBreak(BlockBreakEvent evt) {
    if(plugin.positions.get(evt.getBlock().getLocation().clone()) !=null) {
      evt.getPlayer().sendMessage("[ECS] You must first unregister this CommandSign! Action aborted!");
      evt.setCancelled(true);
      plugin.playerstate.remove(evt.getPlayer());
      plugin.commandstore.remove(evt.getPlayer());
      plugin.commandstore2.remove(evt.getPlayer());
      evt.getPlayer().sendBlockChange(evt.getBlock().getLocation(), evt.getBlock().getType(), evt.getBlock().getData());
    }
  }
  
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent evt) {
    Player pl = evt.getPlayer();
    Block bl = evt.getClickedBlock();
    if(evt.hasBlock() ? bl.getTypeId() == 63 || bl.getTypeId() == 68 || bl.getTypeId() == 77 || bl.getTypeId() == 143 || bl.getTypeId() == 70 || bl.getTypeId() == 72 : false) {
      state sta = plugin.playerstate.get(pl);
      boolean hasAdminPerms = (pl.isOp() || pl.hasPermission("*") ||  pl.hasPermission("ecs.*") || pl.hasPermission("ecs.admin"));
      if(sta == state.NONE || plugin.playerstate.containsKey(pl) == false) {
        if (evt.getAction() == Action.PHYSICAL || ((bl.getTypeId() == 72 || bl.getTypeId() == 70) ? false : isClick(evt))) {
          plugin.executecommands(pl, bl, evt);
          plugin.playerstate.remove(pl);
        }
      } else if(isClick(evt)){
        switch (sta) {
          case CREATE:
            if(hasAdminPerms) {
              if(plugin.positions.containsKey(bl.getLocation().clone())) {
                pl.sendMessage("[ECS] The command sign is already created.");
              } else {
                if(plugin.addsign(bl.getLocation().clone(), (CommandSender) pl)) {
                  pl.sendMessage("[ECS] CommandSign registered with id " + plugin.positions.get(bl.getLocation().clone()));
                } else {
                  pl.sendMessage("[ECS] Cannot register the sign, please look up the server log for further information!");
                }
              }
            } else {
              pl.sendMessage("[ECS] You don't have enough permission to create a CommandSign.");
            }
            break;
          case INFO:
            if(hasAdminPerms || pl.hasPermission("ecs.info")) {
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
                  if(plugin.economys.containsKey(id)) {
                    pl.sendMessage(" |-> Economy Settings: " + plugin.economys.get(id).toString() + (plugin.economys.get(id) == economystate.NONE  ? "" : plugin.economyv.get(id)));
                  }
                } else {
                  pl.sendMessage("[ECB] You don't have the permissions to view the attached commands of this sign!");
                }
              } else {
                pl.sendMessage("[ECS] This is not a valid Block! Aborting Info..");
              }
            } else {
              pl.sendMessage("[ECS] You don't have enough permission to get info about a CommandSign.");
            }
            break;
          default:
            if(!plugin.positions.containsKey(bl.getLocation().clone())) {
              pl.sendMessage("[ECS] Sign is not registered now, aborting...");
            } else {
              switch (sta) {
                case REMOVE:
                  if(hasAdminPerms) {
                    if(!plugin.delsign(bl.getLocation().clone(), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while removing this sign from the datatabase, please view the server log for further information.");
                    } else {
                      pl.sendMessage("[ECS] Successfully removed the sign:" + bl.getLocation().toVector().toString());
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case ADDCOMMAND:
                  if(hasAdminPerms) {
                    //Add the Command
                    if(!plugin.addcommand(bl.getLocation().clone(), (String)plugin.commandstore.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while adding a command, please view the server log for further information." + plugin.commandstore.get(pl));
                    } else {
                      pl.sendMessage("[ECS] Successfully added the command to the sign: " + plugin.commandstore.get(pl));
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case MODCOMMAND:
                  if(hasAdminPerms) {
                    if(!plugin.modcommand(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (String)plugin.commandstore2.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while modifying a command, please view the server log for further information." + plugin.commandstore.get(pl));
                    } else {
                      pl.sendMessage("[ECS] Successfully modified the command with id " + plugin.commandstore.get(pl) + " in the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case REMCOMMAND:
                  if(hasAdminPerms) {
                    if(!plugin.remcommand(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while removing a command, please view the server log for further information." + plugin.commandstore.get(pl));
                    } else {
                      pl.sendMessage("[ECS] Successfully removed the command with id " + plugin.commandstore.get(pl) + " from the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case ADDGIVEPERM:
                  if(hasAdminPerms) {
                    if(!plugin.addperms(bl.getLocation().clone(), (String[])plugin.commandstore.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while adding some permissions to the sign, please view the server log for further information.");
                    } else {
                      pl.sendMessage("[ECS] Successfully added permissions to the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case MODPERM:
                  if(hasAdminPerms) {
                    if(!plugin.modperm(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (String)plugin.commandstore2.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while modifying a permission, please view the server log for further information." + plugin.commandstore.get(pl));
                    } else {
                      pl.sendMessage("[ECS] Successfully modified the permission with id " + plugin.commandstore.get(pl) + " in the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case REMPERM:
                  if(hasAdminPerms) {
                    if(!plugin.remperm(bl.getLocation().clone(), (Integer)plugin.commandstore.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while removing a permission, please view the server log for further information." + plugin.commandstore.get(pl));
                    } else {
                      pl.sendMessage("[ECS] Successfully removed the permission with id " + plugin.commandstore.get(pl) + " from the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case SETLOCATION:
                  if(hasAdminPerms) {
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
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case SETNEEDOP:
                  if(hasAdminPerms) {
                    if(!plugin.setop(bl.getLocation().clone(), (Boolean)plugin.commandstore.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while setting the onlyop state to the sign, please view the server log for further information.");
                    } else {
                      pl.sendMessage("[ECS] Successfully set the onlyop state of the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case SETNEEDPERM:
                  if(hasAdminPerms) {
                    if(!plugin.setneedperm(bl.getLocation().clone(), (String)plugin.commandstore.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while setting the needed permission to the sign, please view the server log for further information.");
                    } else {
                      pl.sendMessage("[ECS] Successfully set the needed permission of the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                case SETECONOMY:
                  if(hasAdminPerms) {
                    if(!plugin.seteconomy(bl.getLocation().clone(), (economystate)plugin.commandstore.get(pl), (Double)plugin.commandstore2.get(pl), (CommandSender) pl)) {
                      pl.sendMessage("[ECS] A problem occured while setting the economystate, please view the server log for further information." + plugin.commandstore.get(pl));
                    } else {
                      pl.sendMessage("[ECS] Successfully set the economystate of the sign.");
                    }
                  } else {
                    pl.sendMessage("[ECS] You don't have enough permission to modify a CommandSign.");
                  }
                  break;
                default:
                  break;
              }
            }
            break;
          }
          plugin.playerstate.remove(pl);
          plugin.commandstore.remove(pl);
          plugin.commandstore2.remove(pl);
          evt.setCancelled(true);
        }        
    } else if(isClick(evt) && plugin.playerstate.containsKey(pl)) {
      pl.sendMessage("[ECS] This is not a valid block. Action aborted!");
      plugin.playerstate.remove(pl);
      plugin.commandstore.remove(pl);
      plugin.commandstore2.remove(pl);
      evt.setCancelled(true);
    }
  }
}