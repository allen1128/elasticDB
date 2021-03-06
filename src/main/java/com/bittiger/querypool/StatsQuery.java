package com.bittiger.querypool;

import java.util.StringTokenizer;

public class StatsQuery {
	public StatsQuery(double x, double u, double u_r, double r, double w, double m) {
		super();
		this.x = x;
		this.u = u;
		this.u_r = u_r;
		this.r = r;
		this.w = w;
		this.m = m;
	}

	double x;
	double u;
	double u_r;
	double r;
	double w;
	double m;

	public String query = "insert into datapoints values" + "(?,?,?,?,?,?)";

	public String getQueryStr() {
		String qString = "";
		int count = 0;
		StringTokenizer st = new StringTokenizer(query, "?", false);
		while (st.hasMoreTokens()) {
			qString += st.nextToken();
			count++;
			switch (count) {
			case 1:
				qString += x;
				break;
			case 2:
				qString += u;
				break;
			case 3:
				qString += u_r;
				break;
			case 4:
				qString += r;
				break;
			case 5:
				qString += w;
				break;
			case 6:
				qString += m;
				break;
			case 7:
				break;
			default:
				System.out.println("More token than expected");
				System.exit(100);
			}
		}
		return qString;
	}
}
