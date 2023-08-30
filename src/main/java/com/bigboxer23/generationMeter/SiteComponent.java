package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.DeviceData;
import com.bigboxer23.generationMeter.data.Server;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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

	private GenerationMeterComponent component;

	public SiteComponent(OpenSearchComponent component, GenerationMeterComponent generationComponent) {
		openSearch = component;
		this.component = generationComponent;
	}

	@Scheduled(cron = "0 5,20,35,50 * * * ?")
	public void handleSites() throws IOException {
		component.loadConfig();
		if (component.getServers().getSites() == null) {
			logger.warn("handleSites:servers not configured, not doing anything");
			return;
		}
		LocalDateTime fetchDate =
				LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).minusMinutes(5);
		Date date = Date.from(fetchDate.atZone(ZoneId.systemDefault()).toInstant());
		logger.info("Starting to fill in site devices");
		List<DeviceData> siteDevices = new ArrayList<>();
		component.getServers().getSites().forEach(site -> {
			logger.debug("adding virtual device " + site.getSite());
			DeviceData siteDevice = new DeviceData(site.getName(), site.getName());
			siteDevice.setIsVirtual();
			siteDevices.add(siteDevice);
			float totalEnergyConsumed =
					getPushedDeviceValues(component.getServers().getServers(), site, DeviceData::getEnergyConsumed);
			if (totalEnergyConsumed > -1) {
				siteDevice.setEnergyConsumed(
						Math.max(0, siteDevice.getTotalEnergyConsumed()) + totalEnergyConsumed);
			}
			float totalRealPower =
					getPushedDeviceValues(component.getServers().getServers(), site, DeviceData::getTotalRealPower);
			if (totalRealPower > -1) {
				siteDevice.setTotalRealPower(Math.max(0, siteDevice.getTotalRealPower()) + totalRealPower);
			}
		});
		openSearch.logData(date, siteDevices);
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
	private float getPushedDeviceValues(List<Server> servers, Server site, Function<DeviceData, Float> mapper) {
		return servers.stream()
				.filter(device -> device.getSite().equals(site.getName()))
				.map(server -> openSearch.getLastDeviceEntry(server.getName()))
				.filter(Objects::nonNull)
				.map(mapper)
				.filter(energy -> energy >= 0)
				.reduce((val1, val2) -> site.isSubtraction() ? Math.abs(val1 - val2) : val1 + val2)
				.orElse(-1f);
	}
}
