package application;

//This is a basic class that stores a tuple (x, y) point

public class tuple {
	public double x;
	public double y;
	public tuple(double _x, double _y) {
		x = _x;
		y = _y;
	}
	@Override
	public String toString() {
		return "(" + Double.toString(x) + ", " + Double.toString(y) + ")";
	}
}
