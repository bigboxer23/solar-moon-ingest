package com.bigboxer23.generationMeter.data;

import lombok.Data;

/** */
@Data
public class Server {
	private String name;

	private String address;

	private String site;

	private String user;

	private String password;

	private String type;

	private String deviceName;

	private boolean subtraction;

	public boolean isPushedDevice() {
		return getPassword() == null && getUser() == null;
	}
}
