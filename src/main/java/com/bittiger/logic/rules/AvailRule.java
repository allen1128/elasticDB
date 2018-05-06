package com.bittiger.logic.rules;

import org.easyrules.annotation.Action;
import org.easyrules.annotation.Condition;
import org.easyrules.annotation.Rule;

import com.bittiger.client.ClientEmulator;
import com.bittiger.logic.ActionType;

@Rule(name = "AvailRule", description = "Check if we need to add server for minimum availabity service contract")
public class AvailRule {
	private ClientEmulator c;
	private int avail;
	
	@Condition
	public boolean checkAvailability() {
		return avail < 3;
	}

	@Action
	public void addServer() throws Exception {
		c.getEventQueue().put(ActionType.AvailNotEnoughAddServer);
	}

	public void setInput(ClientEmulator c, int avail) {
		this.c = c;
		this.avail = avail;
	}

}
