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

	public Device(Map<String, Object> openSearchMap) {
		this((String) openSearchMap.get("site"), (String) openSearchMap.get("device-name"));
		setTotalRealPower(doubleToFloat(openSearchMap.get(TOTAL_REAL_POWER)));
		setEnergyConsumed(doubleToFloat(openSearchMap.get(ENG_CONS)));
		setPowerFactor(doubleToFloat(openSearchMap.get(TOTAL_PF)));
		setAverageVoltage(doubleToFloat(openSearchMap.get(AVG_VOLT)));
		setAverageCurrent(doubleToFloat(openSearchMap.get(AVG_CURRENT)));
		setTotalEnergyConsumed(doubleToFloat(openSearchMap.get(TOTAL_ENG_CONS)));
	}

	public boolean isValid() {
		return getAttributes().size() > 2;
	}

	private float doubleToFloat(Object value) {
		if (value == null) {
			return -1;
		}
		return Optional.of(value)
				.map(val -> (Double) val)
				.map(Double::floatValue)
				.orElse(null);
	}

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

	public void setTotalEnergyConsumed(float totalEnergyConsumed) {
		addAttribute(new DeviceAttribute(TOTAL_ENG_CONS, getTotalEnergyConsumedUnit(), totalEnergyConsumed));
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
		addAttribute(new DeviceAttribute("Virtual", "", true));
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

	public void setAverageVoltage(float voltage) {
		addAttribute(new DeviceAttribute(AVG_VOLT, "", voltage));
	}

	public float getAverageCurrent() {
		return (float) Optional.ofNullable(getAttributes().get(AVG_CURRENT))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public void setAverageCurrent(float current) {
		addAttribute(new DeviceAttribute(AVG_CURRENT, "", current));
	}

	public float getPowerFactor() {
		return (float) Optional.ofNullable(getAttributes().get(TOTAL_PF))
				.map(DeviceAttribute::getValue)
				.orElse(-1f);
	}

	public void setPowerFactor(float powerFactor) {
		addAttribute(new DeviceAttribute(TOTAL_PF, "", powerFactor));
	}
}
