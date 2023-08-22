package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Device;
import com.bigboxer23.generationMeter.data.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Component to stash all the logic related to aggregating virtual site devices */
@Component
public class SiteComponent {

	private static final Logger logger = LoggerFactory.getLogger(SiteComponent.class);

	private OpenSearchComponent openSearch;

	public SiteComponent(OpenSearchComponent component) {
		openSearch = component;
	}

	public void fillInSites(List<Server> sites, List<Device> devices, List<Server> servers) {
		if (sites == null) {
			return;
		}
		logger.debug("starting to fill in virtual devices");
		List<Device> siteDevices = new ArrayList<>();
		sites.forEach(site -> {
			logger.debug("adding virtual device " + site.getSite());
			Device siteDevice = new Device(site.getName(), site.getName());
			siteDevice.setIsVirtual();
			siteDevices.add(siteDevice);
			float totalEnergyConsumed = getPulledDeviceValues(devices, site, Device::getEnergyConsumed);
			if (totalEnergyConsumed > -1) {
				siteDevice.setEnergyConsumed(totalEnergyConsumed);
			}
			totalEnergyConsumed = getPushedDeviceValues(servers, site, Device::getEnergyConsumed);
			if (totalEnergyConsumed > -1) {
				siteDevice.setEnergyConsumed(Math.max(0, siteDevice.getTotalEnergyConsumed()) + totalEnergyConsumed);
			}
			float totalRealPower = getPulledDeviceValues(devices, site, Device::getTotalRealPower);
			if (totalRealPower > -1) {
				siteDevice.setTotalRealPower(totalRealPower);
			}
			totalRealPower = getPushedDeviceValues(servers, site, Device::getTotalRealPower);
			if (totalRealPower > -1) {
				siteDevice.setTotalRealPower(Math.max(0, siteDevice.getTotalRealPower()) + totalRealPower);
			}
		});
		devices.addAll(siteDevices);
	}

	/**
	 * Iterate our list of devices we've pulled to generate the site data
	 *
	 * @param devices
	 * @param site
	 * @param mapper
	 * @return
	 */
	private float getPulledDeviceValues(List<Device> devices, Server site, Function<Device, Float> mapper) {
		return devices.stream()
				.filter(device -> device.getSite().equals(site.getName()))
				.map(mapper)
				.filter(energy -> energy >= 0)
				.reduce(Float::sum)
				.orElse(-1f);
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
	private float getPushedDeviceValues(List<Server> servers, Server site, Function<Device, Float> mapper) {
		return servers.stream()
				.filter(device -> device.getSite().equals(site.getName()))
				.filter(Server::isPushedDevice)
				.map(server -> openSearch.getLastDeviceEntry(server.getName()))
				.map(mapper)
				.filter(energy -> energy >= 0)
				.reduce(Float::sum)
				.orElse(-1f);
	}
}
