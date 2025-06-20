package xyz.nkomarn.harbor.task;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;

public class AccelerateNightTask extends BukkitRunnable {

	private final Checker checker;
    private final World world;

	private static final int dayTime = 23460;

    public AccelerateNightTask(@NotNull Harbor harbor, @NotNull Checker checker, @NotNull World world) {
		this.checker = checker;
        this.world = world;

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
