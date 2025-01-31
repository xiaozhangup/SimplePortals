/*
 * Copyright (c) XZot1K $year. All rights reserved.
 */

package xzot1k.plugins.sp.core;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import xzot1k.plugins.sp.SimplePortals;
import xzot1k.plugins.sp.api.enums.PointType;
import xzot1k.plugins.sp.api.events.PortalEnterEvent;
import xzot1k.plugins.sp.api.objects.Portal;
import xzot1k.plugins.sp.api.objects.SerializableLocation;
import xzot1k.plugins.sp.core.tasks.TeleportTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.bukkit.GameMode.CREATIVE;

public class Listeners implements Listener {

    private final SimplePortals pluginInstance;

    public Listeners(SimplePortals pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        Portal portal = pluginInstance.getManager().getPortalAtLocation(e.getBlock().getLocation());
        if (portal != null && !portal.isDisabled()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null
                && pluginInstance.getManager().isInSelectionMode(e.getPlayer())) {
            e.setCancelled(true);
            if (pluginInstance.getManager().updateCurrentSelection(e.getPlayer(), e.getClickedBlock().getLocation(), PointType.POINT_ONE)) {
                pluginInstance.getManager().highlightBlock(e.getClickedBlock(), e.getPlayer(), PointType.POINT_ONE);
                String message = pluginInstance.getLangConfig().getString("point-1-set-message");
                if (message != null && !message.equalsIgnoreCase(""))
                    e.getPlayer().sendMessage(pluginInstance.getManager()
                            .colorText(pluginInstance.getLangConfig().getString("prefix") + message));
            }
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && pluginInstance.getManager().isInSelectionMode(e.getPlayer())) {
            e.setCancelled(true);

            if (!pluginInstance.getServerVersion().toLowerCase().startsWith("v1_8"))
                if (e.getHand() != EquipmentSlot.HAND) return;

            if (pluginInstance.getManager().updateCurrentSelection(e.getPlayer(), e.getClickedBlock().getLocation(), PointType.POINT_TWO)) {
                pluginInstance.getManager().highlightBlock(e.getClickedBlock(), e.getPlayer(), PointType.POINT_TWO);

                String message = pluginInstance.getLangConfig().getString("point-2-set-message");
                if (message != null && !message.equalsIgnoreCase(""))
                    e.getPlayer().sendMessage(pluginInstance.getManager().colorText(pluginInstance.getLangConfig().getString("prefix") + message));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() != null && e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) initiatePortalStuff(e.getTo(), e.getFrom(), e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForceJoin(PlayerJoinEvent e) {
        if (pluginInstance.getConfig().getBoolean("force-join")) {
            final String worldName = pluginInstance.getConfig().getString("force-join-world");
            if (worldName != null && worldName.isEmpty()) {
                if (e.getPlayer().getWorld().getSpawnLocation() != null)
                    e.getPlayer().teleport(e.getPlayer().getWorld().getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                final World world = pluginInstance.getServer().getWorld(worldName);
                if (world != null && world.getSpawnLocation() != null)
                    e.getPlayer().teleport(world.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        if (pluginInstance.getConfig().getBoolean("join-protection") && pluginInstance.getConfig().getBoolean("use-portal-cooldown")
                && !e.getPlayer().hasPermission("simpleportals.cdbypass")) {

            Portal portal = pluginInstance.getManager().getPortalAtLocation(e.getPlayer().getLocation());
            if (portal == null || portal.isDisabled()) return;

            pluginInstance.getManager().updatePlayerPortalCooldown(e.getPlayer(), "join-protection");
            double tv = pluginInstance.getConfig().getDouble("throw-velocity");
            if (!(tv <= -1)) e.getPlayer().setVelocity(e.getPlayer().getLocation().getDirection()
                    .setY(e.getPlayer().getLocation().getDirection().getY() / 2).multiply(-tv));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        pluginInstance.getManager().getSmartTransferMap().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(EntityPortalEnterEvent e) {
        if (!(e.getEntity() instanceof Player) || e.getLocation().getWorld().getEnvironment() != World.Environment.THE_END) return;
        pluginInstance.getServer().getScheduler().runTaskLater(pluginInstance, () ->
                pluginInstance.getManager().handleVanillaPortalReplacements((Player) e.getEntity(), e.getEntity().getWorld(), PortalType.ENDER), 5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerRespawnEvent e) {
        if (e.getPlayer().getWorld().getEnvironment() != World.Environment.THE_END) return;

        // we don't want it to mess with deaths too if that config option is set to false
        if (!pluginInstance.getConfig().getBoolean("end-portal-locations-handle-death")) return;

        Location respawnLocation = pluginInstance.getManager().getVanillaPortalReplacement(e.getPlayer().getWorld(), PortalType.ENDER);
        if (respawnLocation != null) e.setRespawnLocation(respawnLocation);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(VehicleMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockY() != e.getTo().getBlockY() || e.getFrom().getBlockZ() != e.getTo().getBlockZ())
            initiatePortalStuff(e.getTo(), e.getFrom(), e.getVehicle());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportReader(PlayerTeleportEvent e) {
        pluginInstance.getManager().getEntitiesInTeleportationAndPortals().remove(e.getPlayer().getUniqueId());

        final TeleportTask teleportTask = pluginInstance.getManager().getTeleportTasks().getOrDefault(e.getPlayer().getUniqueId(), null);
        if (teleportTask != null) teleportTask.cancel();
        pluginInstance.getManager().getTeleportTasks().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {vanillaPortalHelper(e);}

    @EventHandler(ignoreCancelled = true)
    public void onPortalEntry(PlayerPortalEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE && pluginInstance.getManager().isPortalNearby(e.getFrom(), 5)) e.setCancelled(true);

        if (pluginInstance.getConfig().getBoolean("block-creative-portal-entrance") && e.getPlayer().getGameMode() == CREATIVE) {
            for (Portal portal : pluginInstance.getManager().getPortalMap().values()) {
                SerializableLocation centerPortal = new SerializableLocation(pluginInstance, portal.getRegion().getPoint1().getWorldName(),
                        ((portal.getRegion().getPoint1().getX() + portal.getRegion().getPoint2().getX()) / 2),
                        ((portal.getRegion().getPoint1().getY() + portal.getRegion().getPoint2().getY()) / 2),
                        ((portal.getRegion().getPoint1().getZ() + portal.getRegion().getPoint2().getZ()) / 2), 0, 0);
                if (centerPortal.distance(e.getFrom(), true) <= 2) {
                    e.setCancelled(true);
                    try {
                        Method method = e.getClass().getMethod("setCanCreatePortal", Boolean.class);
                        if (method != null) method.invoke(e, false);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
                }
            }
            return;
        }

        Portal portal = pluginInstance.getManager().getPortalAtLocation(e.getFrom());
        if (portal == null || portal.isDisabled()) return;

        e.setCancelled(true);
        try {
            Method method = e.getClass().getMethod("setCanCreatePortal", Boolean.class);
            if (method != null) method.invoke(e, false);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}

        vanillaPortalHelper(e);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (pluginInstance.getConfig().getBoolean("item-transfer"))
            pluginInstance.getServer().getScheduler().runTaskLater(pluginInstance,
                    () -> initiatePortalStuff(e.getEntity().getLocation(), e.getLocation(), e.getEntity()),
                    20L * pluginInstance.getConfig().getInt("item-teleport-delay"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(PlayerDropItemEvent e) {
        if (pluginInstance.getConfig().getBoolean("item-transfer")) {
            final Location startLocation = e.getItemDrop().getLocation().clone();
            pluginInstance.getServer().getScheduler().runTaskLater(pluginInstance,
                    () -> initiatePortalStuff(e.getItemDrop().getLocation(), startLocation, e.getItemDrop()),
                    20L * pluginInstance.getConfig().getInt("item-teleport-delay"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        List<String> blockedMobs = pluginInstance.getConfig().getStringList("creature-spawning-blacklist");
        for (int i = -1; ++i < blockedMobs.size(); ) {
            String blockedMob = blockedMobs.get(i);
            if (blockedMob.replace(" ", "_").replace("-", "_").equalsIgnoreCase(e.getEntity().getType().name())) {
                Portal portal = pluginInstance.getManager().getPortalAtLocation(e.getLocation());
                if (portal != null && !portal.isDisabled()) e.setCancelled(true);
                break;
            }
        }
    }

    private void initiatePortalStuff(Location toLocation, Location fromLocation, Entity entity) {
        final boolean isPlayer = (entity instanceof Player);
        Portal portal = pluginInstance.getManager().getPortalAtLocation(toLocation);
        if (portal == null) {
            final Portal foundPortal = pluginInstance.getManager().getEntitiesInTeleportationAndPortals().getOrDefault(entity.getUniqueId(), null);
            if (foundPortal != null && isPlayer) {
                final Player player = ((Player) entity);

                pluginInstance.getManager().getEntitiesInTeleportationAndPortals().remove(player.getUniqueId());

                final TeleportTask teleportTask = pluginInstance.getManager().getTeleportTasks().getOrDefault(player.getUniqueId(), null);
                if (teleportTask != null) teleportTask.cancel();
                pluginInstance.getManager().getTeleportTasks().remove(player.getUniqueId());

                String title = pluginInstance.getLangConfig().getString("teleport-cancelled.title"),
                        subTitle = pluginInstance.getLangConfig().getString("teleport-cancelled.sub-title");
                if ((title != null && !title.isEmpty()) || (subTitle != null && !subTitle.isEmpty())) {
                    player.sendTitle(pluginInstance.getManager().colorText(title),
                            pluginInstance.getManager().colorText(subTitle), 0, 40, 0);
                }
            }
        }

        if (portal != null && !portal.isDisabled()) {
            if (isPlayer) {
                final Player player = (Player) entity;

                TeleportTask teleportTask = pluginInstance.getManager().getTeleportTasks().getOrDefault(player.getUniqueId(), null);
                if (teleportTask != null && !teleportTask.isCancelled()) return;

                if (pluginInstance.getManager().getPortalLinkMap().containsKey(player.getUniqueId())
                        && pluginInstance.getManager().getPortalLinkMap().get(player.getUniqueId()).equalsIgnoreCase(portal.getPortalId()))
                    return;
                else pluginInstance.getManager().getPortalLinkMap().remove(player.getUniqueId());
            }

            PortalEnterEvent portalEnterEvent = new PortalEnterEvent(entity, portal, fromLocation, portal.getTeleportLocation().asBukkitLocation());
            pluginInstance.getServer().getPluginManager().callEvent(portalEnterEvent);
            if (portalEnterEvent.isCancelled()) return;

            if (isPlayer) {
                final Player player = (Player) entity;
                final boolean canBypassCooldown = player.hasPermission("simpleportals.cdbypass");
                final boolean cooldownFail = (pluginInstance.getConfig().getBoolean("use-portal-cooldown")
                        && (pluginInstance.getManager().isPlayerOnCooldown(player, "normal",
                        pluginInstance.getConfig().getInt("portal-cooldown-duration"))
                        || pluginInstance.getManager().isPlayerOnCooldown(player, "join-protection",
                        pluginInstance.getConfig().getInt("join-protection-cooldown")))
                        && !canBypassCooldown), permissionFail = !pluginInstance.getConfig().getBoolean("bypass-portal-permissions")
                        && (!player.hasPermission("simpleportals.portal." + portal.getPortalId())
                        && !player.hasPermission("simpleportals.portals." + portal.getPortalId())
                        && !player.hasPermission("simpleportals.portal.*") && !player.hasPermission("simpleportals.portals.*"));

                if (cooldownFail || permissionFail) {
                    double tv = pluginInstance.getConfig().getDouble("throw-velocity");
                    if (!(tv <= -1)) {
                        final Vector direction = new Vector(fromLocation.getX() - toLocation.getX(),
                                ((fromLocation.getY() - toLocation.getY()) + (player.getVelocity().getY() / 2)),
                                fromLocation.getZ() - toLocation.getZ()).multiply(tv);
                        player.setVelocity(direction);
                    }

                    String message = cooldownFail ? pluginInstance.getLangConfig().getString("enter-cooldown-message")
                            : pluginInstance.getLangConfig().getString("enter-no-permission-message");
                    if (message != null && !message.equalsIgnoreCase(""))
                        player.sendMessage(pluginInstance.getManager().colorText(pluginInstance.getLangConfig().getString("prefix")
                                + message.replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player, "normal",
                                pluginInstance.getConfig().getInt("portal-cooldown-duration"))))));
                    return;
                }
            }

            if (isPlayer && pluginInstance.getConfig().getBoolean("use-portal-cooldown")) {
                final Player player = (Player) entity;
                final boolean canBypassCooldown = player.hasPermission("simpleportals.cdbypass");
                if (!canBypassCooldown) pluginInstance.getManager().updatePlayerPortalCooldown((Player) entity, "normal");
            }

            if (portal.getTeleportLocation() != null) {
                if (isPlayer) {
                    final Player player = (Player) entity;
                    if (portal.getMessage() != null && !portal.getMessage().isEmpty())
                        player.sendMessage(pluginInstance.getManager().colorText(portal.getMessage().replace("{name}", portal.getPortalId())
                                .replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player, "normal",
                                        pluginInstance.getConfig().getInt("portal-cooldown-duration"))))));

                    if (portal.getBarMessage() != null && !portal.getBarMessage().isEmpty())
                        pluginInstance.getManager().sendBarMessage(player, portal.getBarMessage().replace("{name}", portal.getPortalId())
                                .replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player, "normal",
                                        pluginInstance.getConfig().getInt("portal-cooldown-duration")))));

                    if ((portal.getTitle() != null && !portal.getTitle().isEmpty()) && (portal.getSubTitle() != null && !portal.getSubTitle().isEmpty()))
                        pluginInstance.getManager().sendTitle(player, portal.getTitle().replace("{name}", portal.getPortalId())
                                        .replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player,
                                                "normal", pluginInstance.getConfig().getInt("portal-cooldown-duration")))),
                                portal.getSubTitle().replace("{name}", portal.getPortalId())
                                        .replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player,
                                                "normal", pluginInstance.getConfig().getInt("portal-cooldown-duration")))));
                    else if (portal.getTitle() != null && !portal.getTitle().isEmpty())
                        pluginInstance.getManager().sendTitle(player, portal.getTitle().replace("{name}", portal.getPortalId())
                                .replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player,
                                        "normal", pluginInstance.getConfig().getInt("portal-cooldown-duration")))), null);
                    else if (portal.getSubTitle() != null && !portal.getSubTitle().isEmpty())
                        pluginInstance.getManager().sendTitle(player, null, portal.getSubTitle().replace("{name}", portal.getPortalId())
                                .replace("{time}", String.valueOf(pluginInstance.getManager().getCooldownTimeLeft(player,
                                        "normal", pluginInstance.getConfig().getInt("portal-cooldown-duration")))));
                }

                portal.performAction(entity);
            }
        } else {
            if (!isPlayer) return;
            final Player player = (Player) entity;

            pluginInstance.getManager().getPortalLinkMap().remove(player.getUniqueId());
            if (!pluginInstance.getManager().getSmartTransferMap().isEmpty()
                    && pluginInstance.getManager().getSmartTransferMap().containsKey(player.getUniqueId())) {
                SerializableLocation serializableLocation = pluginInstance.getManager().getSmartTransferMap().get(player.getUniqueId());
                if (serializableLocation != null) {
                    Location location = player.getLocation();
                    if (location.getWorld() != null) {
                        serializableLocation.setWorldName(location.getWorld().getName());
                        serializableLocation.setX(location.getX());
                        serializableLocation.setY(location.getY());
                        serializableLocation.setZ(location.getZ());
                        serializableLocation.setYaw(location.getYaw());
                        serializableLocation.setPitch(location.getPitch());
                        return;
                    }
                }
            }

            pluginInstance.getManager().getSmartTransferMap().put(player.getUniqueId(), new SerializableLocation(pluginInstance, fromLocation));
        }
    }

    private void vanillaPortalHelper(PlayerTeleportEvent e) {
        if (!e.getCause().name().toUpperCase().contains("PORTAL") && !e.getCause().name().toUpperCase().contains("GATEWAY")) return;

        PortalType portalType = PortalType.NETHER;
        switch (e.getCause()) {
            case NETHER_PORTAL:
                portalType = PortalType.NETHER;
                break;
            case END_PORTAL:
                // case END_GATEWAY:
                portalType = PortalType.ENDER;
                break;
            default:
                break;
        }

        pluginInstance.getManager().handleVanillaPortalReplacements(e.getPlayer(), e.getFrom().getWorld(), portalType);
    }

}