package application;

//This is a basic class that stores a tuple (x, y) point

public class tuple {
	public float x;
	public float y;
	public tuple(float _x, float _y) {
		x = _x;
		y = _y;
	}
	@Override
	public String toString() {
		return "(" + Float.toString(x) + ", " + Float.toString(y) + ")";
	}
}
