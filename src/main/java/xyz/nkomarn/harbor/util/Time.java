/**
 * Taken from SmoothSleep (https://github.com/OffLuffy/SmoothSleep)
 * Copyright 2022 SmoothSleep contributors
 */

package xyz.nkomarn.harbor.util;

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
}
