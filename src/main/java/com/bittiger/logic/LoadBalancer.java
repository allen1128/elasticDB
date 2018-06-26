package com.bittiger.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bittiger.client.BalancerType;
import com.bittiger.client.ClientEmulator;

public class LoadBalancer {
	private List<Server> readQueue = new ArrayList<Server>();
	private Server writeQueue = null;
	private List<Server> candidateQueue = new ArrayList<Server>();
	private List<Server> failureServerQueue = new ArrayList<Server>();
	private int nextReadServer = 0;
	private String balancerType;
	private Random rand = new Random();
	
	private static transient final Logger LOG = LoggerFactory
			.getLogger(LoadBalancer.class);

	public LoadBalancer(ClientEmulator ce) {
		writeQueue = new Server(ce.getTpcw().writeQueue);
		for (int i = 0; i < ce.getTpcw().readQueue.length; i++) {
			readQueue.add(new Server(ce.getTpcw().readQueue[i]));
		}
		for (int i = 0; i < ce.getTpcw().candidateQueue.length; i++) {
			candidateQueue.add(new Server(ce.getTpcw().candidateQueue[i]));
		}
		
		balancerType = ce.getTpcw().loadBalancerType;
	}

	// there is only one server in the writequeue.
	public Server getWriteQueue() {
		return writeQueue;
	}

	public synchronized Server getNextReadServer() {
		Server next = null;
		BalancerType type = BalancerType.parse(balancerType);
		
		if (type == null) {
			throw new IllegalStateException("invalid balance type in tpcw.properties");
		}
				
		switch (type) {
			case RoundRobin:
				next = getRoundRobinReadServer();
				break;
			case Random:
				next = getRandomReadServer();
				break;
			case LeastLatency:
				next = getRandomReadServer();
				break;		
		}		
		return next;
	}
	
	public Server getRandomReadServer() {
		int next = rand.nextInt(readQueue.size());
		Server server = readQueue.get(next);
		LOG.debug("choose read server as " + server.getIp());
		return server;
	}
	
	public Server getRoundRobinReadServer() {
		nextReadServer = (nextReadServer + 1) % readQueue.size();
		Server server = readQueue.get(nextReadServer);
		LOG.debug("choose read server as " + server.getIp());
		return server;
	}

	public synchronized void addServer(Server server) {
		readQueue.add(server);
	}

	public synchronized Server removeServer() {
		Server server = readQueue.remove(readQueue.size() - 1);
		candidateQueue.add(server);
		return server;
	}

	public synchronized List<Server> getReadQueue() {
		return readQueue;
	}

	// readQueue is shared by the UserSessions and Executor.
	// However, candidateQueue is only called by Executor.
	public List<Server> getCandidateQueue() {
		return candidateQueue;
	}
	
	public synchronized void detectReadServerFailure(Server server){
		LOG.warn("server failure detected for {}; moving it from read queue to failure queue", server.getIp());
		readQueue.remove(server);
		failureServerQueue.add(server);
	}
}
