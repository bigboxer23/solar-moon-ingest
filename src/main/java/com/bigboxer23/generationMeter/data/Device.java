package com.bigboxer23.generationMeter.data;

import static com.bigboxer23.generationMeter.MeterConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

/** */
@Data
public class Device {

	private Map<String, DeviceAttribute> attributes;

	public Device(String site, String name) {
		attributes = new HashMap<>();
		attributes.put("site", new DeviceAttribute("site", "", site));
		attributes.put(DEVICE_NAME, new DeviceAttribute(DEVICE_NAME, "", name));
	}

	public void addAttribute(DeviceAttribute attr) {
		attributes.put(attr.getName(), attr);
	}

	public float getTotalRealPower() {
		return (Float) Optional.ofNullable(attributes.get(TOTAL_REAL_POWER))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public void setTotalRealPower(float totalRealPower) {
		addAttribute(new DeviceAttribute(TOTAL_REAL_POWER, getTotalEnergyConsumedUnit(), totalRealPower));
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
		addAttribute(new DeviceAttribute(ENG_CONS, getTotalEnergyConsumedUnit(), energyConsumed));
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

	public float getAverageVoltage() {
		return (float) Optional.ofNullable(getAttributes().get(AVG_VOLT))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public float getAverageCurrent() {
		return (float) Optional.ofNullable(getAttributes().get(AVG_CURRENT))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public float getPowerFactor() {
		return (float) Optional.ofNullable(getAttributes().get(TOTAL_PF))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public void setPowerFactor(float powerFactor) {
		getAttributes().put(TOTAL_PF, new DeviceAttribute(TOTAL_PF, "", powerFactor));
	}
}
