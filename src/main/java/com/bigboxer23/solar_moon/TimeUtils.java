package com.bigboxer23.solar_moon;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** */
public class TimeUtils {
	/**
	 * round to nearest 15m interval
	 *
	 * @return
	 */
	public static Date get15mRoundedDate() {
		LocalDateTime now = LocalDateTime.now();
		return Date.from(now.truncatedTo(ChronoUnit.MINUTES)
				.withMinute(now.getMinute() / 15 * 15)
				.atZone(ZoneId.systemDefault())
				.toInstant());
	}
}
