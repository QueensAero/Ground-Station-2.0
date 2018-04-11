package com.QADT;

//This is a basic class that stores a tuple (x, y) point

public class tuple {
	public double x;
	public double y;
	public double h1, h2, head; // Height of point - optional - should become one height at some point...
	public tuple(double _x, double _y) {
		x = _x;
		y = _y;
	}
	public tuple(double _x, double _y, double _h1, double _h2, double _head) {
		h1 = _h1;
		h2 = _h2;
		head = _head;
		x = _x;
		y = _y;
	}
	@Override
	public String toString() {
		return "(" + Double.toString(x) + ", " + Double.toString(y) + ")";
	}
	
}
