/*
 * Taken from SmoothSleep (https://github.com/OffLuffy/SmoothSleep)
 * Copyright 2022 SmoothSleep contributors
 */

package xyz.nkomarn.harbor.util;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.Optional;

public class Time {
	public static int ticksTo24Hours(long ticks) {
		ticks += 6000;
		int hours = (int) ticks / 1000;
		return (hours >= 24 ? hours - 24 : hours);
	}

	public static int ticksTo12Hours(long ticks) {
		int hours = ticksTo24Hours(ticks);
		return hours > 12 ? hours - 12 : hours == 0 ? 12 : hours;
	}

	public static int ticksToMinutes(long ticks) {
		return (int) ((ticks % 1000) / 16.66);
	}

	public static boolean ticksIsAM(long ticks) {
		return ticksTo24Hours(ticks) < 12;
	}
	
	public static Holder<WorldClock> getWorldClock(World world) {
		Optional<Holder<WorldClock>> defaultClock = ((CraftWorld) world).getHandle().dimensionType().defaultClock();

		if (defaultClock.isEmpty()) {
			return null;
		}

		return defaultClock.orElse(null);
	}
}
