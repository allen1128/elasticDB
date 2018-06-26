package com.bittiger.client;

public enum BalancerType {
	RoundRobin, Random, LeastLatency;
	
	public static BalancerType parse(String name){
		return BalancerType.valueOf(name);
	}
}
