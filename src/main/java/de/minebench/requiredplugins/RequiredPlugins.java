package de.minebench.requiredplugins;

/*
 * RequiredPlugins
 * Copyright (c) 2019 Max Lee aka Phoenix616 (mail@moep.tv)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class RequiredPlugins extends JavaPlugin implements Listener {

    private Set<String> required;
    private Set<String> missing;

    @Override
    public void onEnable() {
        loadConfig();
        getCommand("requiredplugins").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        required = new HashSet<>(getConfig().getStringList("required-plugins"));
        calculateMissing();
    }

    private void calculateMissing() {
        missing = required.stream()
                .filter(name -> {
                    Plugin p = getServer().getPluginManager().getPlugin(name);
                    return p == null || !p.isEnabled();
                })
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        missing.remove(event.getPlugin().getName().toLowerCase());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (required.contains(event.getPlugin().getName())) {
            missing.add(event.getPlugin().getName().toLowerCase());
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (missing.size() > 0) {
            if (event.getPlayer().hasPermission("requiredplugins.bypassblock")) {
                getServer().getScheduler().runTask(this, () -> event.getPlayer().sendMessage(getText("join")));
            } else {
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage(getText("kick"));
            }
        }
    }

    @EventHandler
    public void onServerLoaded(ServerLoadEvent event) {
        calculateMissing();
        if (missing.size() > 0) {
            getLogger().warning("Not all required plugins are loaded: " + getPlugins(false));
        }
    }

    private String getText(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("texts." + key, "Unknown text " + key)
                .replace("%plugins%", getPlugins(true))
                .replace("%missing%", getPlugins(false))
                .replace("%missingsize%", String.valueOf(missing.size()))
                .replace("%requiredsize%", String.valueOf(required.size())));
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            if ("reload".equalsIgnoreCase(args[0]) && sender.hasPermission("requiredplugins.command.reload")) {
                loadConfig();
                sender.sendMessage(ChatColor.YELLOW + "Config reloaded!");
                return true;
            } else if ("list".equalsIgnoreCase(args[0]) && sender.hasPermission("requiredplugins.command.list")) {
                sender.sendMessage(ChatColor.RED + "Required Plugins:");
                sender.sendMessage(getPlugins(true));
                sender.sendMessage(ChatColor.YELLOW + String.valueOf(missing.size()) + ChatColor.RED + "/" + required.size() + " missing.");
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + getName() + " " + ChatColor.YELLOW + getDescription().getVersion());
        return false;
    }

    private String getPlugins(boolean all) {
        return required.stream()
                .sorted(String::compareToIgnoreCase)
                .map(name -> {
                    Plugin p = getServer().getPluginManager().getPlugin(name);
                    if (!all && p != null && p.isEnabled()) {
                        return null;
                    }
                    return p == null ? ChatColor.RED + name : ((p.isEnabled() ? ChatColor.GREEN : ChatColor.YELLOW) + p.getName());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(ChatColor.GRAY + ", "));
    }
}
