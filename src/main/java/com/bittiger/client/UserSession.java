package com.bittiger.client;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.rmi.ConnectException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.easyrules.api.RulesEngine;
import org.easyrules.core.RulesEngineBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bittiger.logic.LoadBalancer;
import com.bittiger.logic.Server;
import com.bittiger.logic.rules.AvailRule;
import com.bittiger.logic.rules.ScaleInRule;
import com.bittiger.querypool.QueryMetaData;

public class UserSession extends Thread {
	private TPCWProperties tpcw = null;
	private ClientEmulator client = null;
	private Random rand = null;
	private boolean suspendThread = false;
	private BlockingQueue<Integer> queue;
	private int id;

	private static transient final Logger LOG = LoggerFactory.getLogger(UserSession.class);

	public UserSession(int id, ClientEmulator client, BlockingQueue<Integer> bQueue) {
		super("UserSession" + id);
		this.id = id;
		this.queue = bQueue;
		this.client = client;
		this.tpcw = client.getTpcw();
		this.rand = new Random();
	}

	private long TPCWthinkTime(double mean) {
		double r = rand.nextDouble();
		return ((long) (((0 - mean) * Math.log(r))));
	}

	public synchronized void notifyThread() {
		notify();
	}

	public synchronized void releaseThread() {
		suspendThread = false;
	}

	public synchronized void holdThread() {
		suspendThread = true;
	}

	private String computeNextSql(double rwratio, double[] read, double[] write) {
		String sql = "";
		// first decide read or write
		double rw = rand.nextDouble();
		if (rw < rwratio) {
			sql += "bq";
			double internal = rand.nextDouble();
			int num = 0;
			for (int i = 0; i < read.length - 1; i++) {
				if (read[i] < internal && internal <= read[i + 1]) {
					num = i + 1;
					sql += num;
					break;
				}
			}

		} else {
			sql += "wq";
			double internal = rand.nextDouble();
			int num = 0;
			for (int i = 0; i < write.length - 1; i++) {
				if (write[i] < internal && internal <= write[i + 1]) {
					num = i + 1;
					sql += num;
					break;
				}
			}
		}
		return sql;
	}

	private Connection getNextConnection(String sql) {
		// read
		if (sql.contains("b")) {
			return getNextReadConnection(client.getLoadBalancer());
		} else {
			return getNextWriteConnection(client.getLoadBalancer());
		}
	}

	public Connection getNextWriteConnection(LoadBalancer loadBalancer) {
		Server server = null;
		Connection connection = null;
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			// DriverManager.setLoginTimeout(5);
			server = loadBalancer.getWriteQueue();
			connection = (Connection) DriverManager.getConnection(Utilities.getUrl(server), client.getTpcw().username,
					client.getTpcw().password);
			connection.setAutoCommit(true);
		} catch (Exception e) {
			LOG.error(e.toString());
		}
		LOG.debug("choose write server as " + server.getIp());
		return connection;
	}

	public Connection getNextReadConnection(LoadBalancer loadBalancer) {
		Server server = null;
		Connection connection = null;
		try {
			do {
				Class.forName("com.mysql.jdbc.Driver").newInstance();
				server = loadBalancer.getNextReadServer();
				connection = tryConnect(loadBalancer, server);
				if (connection != null) {
					connection.setAutoCommit(true);
				}
			} while (connection == null && !loadBalancer.getReadQueue().isEmpty());

		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		return connection;

	}

	public Connection tryConnect(LoadBalancer loadBalancer, Server server) {
		int count = 0;
		Connection connection = null;
		
		while (true) {
			try {
				connection = (Connection) DriverManager.getConnection(Utilities.getUrl(server),
						client.getTpcw().username, client.getTpcw().password);
				break;
			} catch (Exception ex) {
				count++;
				if (count > 3) {
					loadBalancer.detectReadServerFailure(server);
					RulesEngine rulesEngine = RulesEngineBuilder.aNewRulesEngine().build();
					AvailRule availRule = new AvailRule();
					availRule.setInput(client, loadBalancer.getReadQueue().size());			
					rulesEngine.registerRule(availRule);
					rulesEngine.fireRules();
					break;
				}
			}
		}
		return connection;
	}

	public void run() {
		while (!client.isEndOfSimulation()) {
			try {
				synchronized (this) {
					while (suspendThread)
						wait();
				}
				// decide of closed or open system
				double r = rand.nextDouble();
				if (r < tpcw.mixRate) {
					int t = queue.take();
					LOG.debug(t + " has been taken");
				} else {
					Thread.sleep((long) ((float) TPCWthinkTime(tpcw.TPCmean)));
				}
			} catch (Exception ex) {
				LOG.error("Error while running session: " + ex.getMessage());
			}

			String queryclass = computeNextSql(tpcw.rwratio, tpcw.read, tpcw.write);
			Connection connection = null;
			Statement stmt = null;
			try {
				connection = getNextConnection(queryclass);
				String classname = "com.bittiger.querypool." + queryclass;
				QueryMetaData query = (QueryMetaData) Class.forName(classname).newInstance();
				String command = query.getQueryStr();
				stmt = connection.createStatement();
				if (queryclass.contains("b")) {
					long start = System.currentTimeMillis();
					stmt.executeQuery(command);
					long end = System.currentTimeMillis();
					client.getMonitor().addQuery(this.id, queryclass, start, end);
				} else {
					long start = System.currentTimeMillis();
					stmt.executeUpdate(command);
					long end = System.currentTimeMillis();
					client.getMonitor().addQuery(this.id, queryclass, start, end);
				}
			} catch (Exception ex) {
				LOG.error("Error while executing query: " + ex.getMessage());
			} finally {
				if (stmt != null)
					try {
						stmt.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				if (connection != null)
					try {
						connection.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
			}
		}
	}
}
