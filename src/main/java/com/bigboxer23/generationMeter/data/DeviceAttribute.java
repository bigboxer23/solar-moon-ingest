package com.bigboxer23.generationMeter.data;

import lombok.Data;

/** */
@Data
public class DeviceAttribute {
	private String name;

	private String unit;

	private Object value;

	public DeviceAttribute(String name, String unit, Object value) {
		setName(name);
		setUnit(unit);
		setValue(value);
	}
}
