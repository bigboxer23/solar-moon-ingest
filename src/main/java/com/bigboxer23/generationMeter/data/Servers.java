package com.bigboxer23.generationMeter.data;

import java.util.List;

import com.bigboxer23.solar_moon.data.Device;
import lombok.Data;

/** */
@Data
public class Servers {
	private List<Device> servers;

	private List<Device> sites;
}
