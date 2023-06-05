package com.bigboxer23.generationMeter.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

/** */
@Data
public class Device {
	public static final String TOTAL_ENG_CONS = "Total Energy Consumption";
	public static final String ENG_CONS = "Energy Consumed";

	private Map<String, DeviceAttribute> attributes;

	public Device(String site, String name) {
		attributes = new HashMap<>();
		attributes.put("site", new DeviceAttribute("site", "", site));
		attributes.put("device-name", new DeviceAttribute("device-name", "", name));
	}

	public void addAttribute(DeviceAttribute attr) {
		attributes.put(attr.getName(), attr);
	}

	public float getTotalEnergyConsumed() {
		return (Float) Optional.ofNullable(attributes.get(TOTAL_ENG_CONS))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public float getEnergyConsumed() {
		return (Float) Optional.ofNullable(attributes.get(ENG_CONS))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public void setEnergyConsumed(float energyConsumed) {
		addAttribute(new DeviceAttribute(Device.ENG_CONS, getTotalEnergyConsumedUnit(), energyConsumed));
	}

	public void setIsVirtual() {
		getAttributes().put("Virtual", new DeviceAttribute("Virtual", "", true));
	}

	public String getTotalEnergyConsumedUnit() {
		return Optional.ofNullable(attributes.get(TOTAL_ENG_CONS))
				.map(DeviceAttribute::getUnit)
				.orElse(null);
	}

	public String getName() {
		return (String) attributes.get("device-name").getValue();
	}

	public String getSite() {
		return (String) attributes.get("site").getValue();
	}
}
