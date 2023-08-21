package com.bigboxer23.generationMeter;

/** */
public interface MeterConstants {
	String FILE_DATA = "LOGFILEUPLOAD";
	String MODE_PATH = "/DAS/mode";
	String POINT_PATH = "/DAS/devices/device/records/record/point";
	String DEVICE_NAME_PATH = "/DAS/devices/device/name";
	String XML_SUCCESS_RESPONSE = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><DAS><result>SUCCESS</result></DAS>";

	String TOTAL_ENG_CONS = "Total Energy Consumption";
	String ENG_CONS = "Energy Consumed";
	String TOTAL_REAL_POWER = "Total Real Power";
	String DEVICE_NAME = "device-name";
	String AVG_CURRENT = "Average Current";
	String AVG_VOLT = "Average Voltage (L-N)";
	String TOTAL_PF = "Total (System) Power Factor";
}
