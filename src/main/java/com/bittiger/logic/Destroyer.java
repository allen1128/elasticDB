package com.bittiger.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bittiger.client.ClientEmulator;
import com.bittiger.client.Utilities;

public class Destroyer extends Thread{
	private ClientEmulator c;
	private int sleepInterval;
	private String target;
	private static transient final Logger LOG = LoggerFactory.getLogger(Controller.class);

	public Destroyer(ClientEmulator ce, int sleepInterval, String target) {
		this.c = ce;
		this.sleepInterval = sleepInterval;
		this.target = target;
	}


	public void run() {
		while (true) {
			LOG.info("Destroyer is stopping mysql service at {} after {}", target, sleepInterval);
			try {
				Thread.sleep(this.sleepInterval);
				Utilities.stopServer(new Server(target).getIp());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
