package venture.Aust1n46.chat.initiators.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import venture.Aust1n46.chat.Logger;
import venture.Aust1n46.chat.controllers.PluginMessageController;
import venture.Aust1n46.chat.controllers.VentureChatSpigotFlatFileController;
import venture.Aust1n46.chat.initiators.application.VentureChat;
import venture.Aust1n46.chat.model.ChatChannel;
import venture.Aust1n46.chat.model.JsonFormat;
import venture.Aust1n46.chat.model.VentureChatPlayer;
import venture.Aust1n46.chat.service.ConfigService;
import venture.Aust1n46.chat.service.UUIDService;
import venture.Aust1n46.chat.service.VentureChatPlayerApiService;
import venture.Aust1n46.chat.utilities.FormatUtils;

/**
 * Manages player login and logout events.
 * 
 * @author Aust1n46
 */
@Singleton
public class LoginListener implements Listener {
	@Inject
	private VentureChat plugin;
	@Inject
	private UUIDService uuidService;
	@Inject
	private VentureChatSpigotFlatFileController spigotFlatFileController;
	@Inject
	private PluginMessageController pluginMessageController;
	@Inject
	private VentureChatPlayerApiService playerApiService;
	@Inject
	private ConfigService configService;
	@Inject
	private Logger log;

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
		VentureChatPlayer ventureChatPlayer = playerApiService.getOnlineMineverseChatPlayer(playerQuitEvent.getPlayer());
		if (ventureChatPlayer == null) {
			log.warn("onPlayerQuit() Could not find VentureChatPlayer");
		} else {
			spigotFlatFileController.savePlayerData(ventureChatPlayer);
			ventureChatPlayer.clearMessages();
			ventureChatPlayer.setOnline(false);
			playerApiService.removeMineverseChatOnlinePlayerToMap(ventureChatPlayer);
			log.debug("onPlayerQuit() ventureChatPlayer:{} quit", ventureChatPlayer);
		}
	}

	private void handleNameChange(VentureChatPlayer mcp, Player eventPlayerInstance) {
		plugin.getServer().getConsoleSender().sendMessage(
				FormatUtils.FormatStringAll("&8[&eVentureChat&8]&e - Detected Name Change. Old Name:&c " + mcp.getName() + " &eNew Name:&c " + eventPlayerInstance.getName()));
		playerApiService.removeNameFromMap(mcp.getName());
		mcp.setName(eventPlayerInstance.getName());
		playerApiService.addNameToMap(mcp);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerJoin(PlayerJoinEvent event) {
		VentureChatPlayer mcp = playerApiService.getMineverseChatPlayer(event.getPlayer());
		Player player = event.getPlayer();
		String name = player.getName();
		if (mcp == null) {
			UUID uuid = player.getUniqueId();
			mcp = new VentureChatPlayer(uuid, name, configService.getDefaultChannel());
			playerApiService.addMineverseChatPlayerToMap(mcp);
			playerApiService.addNameToMap(mcp);
		}
		uuidService.checkOfflineUUIDWarning(mcp.getUuid());
		// check for name change
		if (!mcp.getName().equals(name)) {
			handleNameChange(mcp, event.getPlayer());
		}
		mcp.setOnline(true);
		mcp.setPlayer(player);
		mcp.setHasPlayed(false);
		playerApiService.addMineverseChatOnlinePlayerToMap(mcp);
		String jsonFormat = mcp.getJsonFormat();
		for (JsonFormat j : configService.getJsonFormats()) {
			if (mcp.getPlayer().hasPermission("venturechat.json." + j.getName())) {
				if (configService.getJsonFormat(mcp.getJsonFormat()).getPriority() > j.getPriority()) {
					jsonFormat = j.getName();
				}
			}
		}
		mcp.setJsonFormat(jsonFormat);
		for (ChatChannel ch : configService.getAutojoinList()) {
			if (ch.hasPermission()) {
				if (mcp.getPlayer().hasPermission(ch.getPermission())) {
					mcp.addListening(ch.getName());
				}
			} else {
				mcp.addListening(ch.getName());
			}
		}
		if (configService.isProxyEnabled()) {
			pluginMessageController.synchronizeWithDelay(mcp, false);
		}
	}
}