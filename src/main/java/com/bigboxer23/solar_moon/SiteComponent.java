package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.Device;
import com.bigboxer23.solar_moon.data.DeviceData;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Component to stash all the logic related to aggregating virtual site devices */
@Component
public class SiteComponent {

	private static final Logger logger = LoggerFactory.getLogger(SiteComponent.class);

	private OpenSearchComponent openSearch;

	private DeviceComponent component;

	public SiteComponent(OpenSearchComponent component, DeviceComponent deviceComponent) {
		openSearch = component;
		this.component = deviceComponent;
	}

	@Scheduled(fixedDelay = 60000) // 3 min
	public void handleSites() {
		logger.info("Starting to fill in site devices");
		component.getDevices(true).forEach(this::handleSite);
	}

	public void handleSite(Device site) {
		logger.debug("checking virtual device " + site.getName());
		DeviceData siteData = openSearch.getLastDeviceEntry(site.getName());
		if (siteData != null) {
			logger.debug("virtual device already exists " + site.getName());
			return;
		}
		List<DeviceData> siteDevices = new ArrayList<>();
		for (Device device : component.getDevices(false).stream()
				.filter(device -> device.getSite() != null && device.getSite().equals(site.getName()))
				.toList()) {
			DeviceData data = openSearch.getLastDeviceEntry(device.getName());
			if (data == null) {
				logger.debug("missing device for virtual device " + site.getName() + " " + device.getName());
				return;
			}
			siteDevices.add(data);
		}
		DeviceData siteDevice = new DeviceData(site.getName(), site.getName(), site.getClientId());
		siteDevice.setIsVirtual();
		float totalEnergyConsumed = getPushedDeviceValues(siteDevices, site, DeviceData::getEnergyConsumed);
		if (totalEnergyConsumed > -1) {
			siteDevice.setEnergyConsumed(Math.max(0, siteDevice.getTotalEnergyConsumed()) + totalEnergyConsumed);
		}
		float totalRealPower = getPushedDeviceValues(siteDevices, site, DeviceData::getTotalRealPower);
		if (totalRealPower > -1) {
			siteDevice.setTotalRealPower(Math.max(0, siteDevice.getTotalRealPower()) + totalRealPower);
		}
		logger.info("adding virtual device " + site.getName());
		openSearch.logData(get15mRoundedDate(), Collections.singletonList(siteDevice));
	}

	/**
	 * round to nearest 15m interval
	 *
	 * @return
	 */
	protected Date get15mRoundedDate() {
		LocalDateTime now = LocalDateTime.now();
		return Date.from(now.truncatedTo(ChronoUnit.MINUTES)
				.withMinute(now.getMinute() / 15 * 15)
				.atZone(ZoneId.systemDefault())
				.toInstant());
	}
	/**
	 * Query OpenSearch for the most recent data (within the ${scheduler-time} window) because this
	 * data is pushed to us from the devices
	 *
	 * @param servers
	 * @param site
	 * @param mapper
	 * @return
	 */
	private float getPushedDeviceValues(List<DeviceData> devices, Device site, Function<DeviceData, Float> mapper) {
		return devices.stream()
				.map(mapper)
				.filter(energy -> energy >= 0)
				.reduce((val1, val2) -> site.isSubtraction() ? Math.abs(val1 - val2) : val1 + val2)
				.orElse(-1f);
	}
}
