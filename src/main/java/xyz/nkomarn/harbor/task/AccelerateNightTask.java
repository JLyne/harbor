package xyz.nkomarn.harbor.task;

import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.util.Config;

public class AccelerateNightTask extends BukkitRunnable {

    private final Harbor harbor;
    private final Checker checker;
    private final World world;
    private final Config config;

    private static final int dayTime = 23460;

    public AccelerateNightTask(@NotNull Harbor harbor, @NotNull Checker checker, @NotNull World world) {
        this.harbor = harbor;
        this.checker = checker;
        this.world = world;

        config = harbor.getConfiguration();

        runTaskTimer(harbor, 1, 1);
    }

    @Override
    public void run() {
        long time = world.getTime();
        double timeRate = checker.getTimescale(world);

        if (timeRate == Double.POSITIVE_INFINITY) { // Instantly skip night if enabled
            world.setTime(dayTime);
            checker.clearWeather(world);
            checker.resetStatus(world);
            cancel();
            return;
        }

        if (time >= (dayTime - timeRate * 1.5) && time <= dayTime) {
            if (config.getBoolean("night-skip.reset-phantom-statistic")) {
                world.getPlayers().forEach(player -> player.setStatistic(Statistic.TIME_SINCE_REST, 0));
            }

            checker.clearWeather(world);
            checker.resetStatus(world);
            cancel();
            return;
        }

        if(timeRate > 1) {
            world.setTime(time + (int) timeRate);
        }
    }
}
