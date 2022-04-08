package xyz.nkomarn.harbor.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.util.PlayerManager;

import java.time.Instant;

public class BedListener implements Listener {

    private final Harbor harbor;
    private final PlayerManager playerManager;

    public BedListener(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.playerManager = harbor.getPlayerManager();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(harbor, () -> {
            playerManager.setCooldown(player, Instant.now());
        }, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Bukkit.getScheduler().runTaskLater(harbor, () -> {
            playerManager.setCooldown(event.getPlayer(), Instant.now());
        }, 1);
    }
}
