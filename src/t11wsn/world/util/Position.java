package t11wsn.world.util;

public class Position {

    private int x = 0;
    private int y = 0;

    public Position (int x, int y){
        this.x = x;
        this.y = y;
    }

    public Position from(int dx, int dy) {
        return new Position(this.x+dx, this.y+dy);
    }

    public int getX() { return x; }
    public int getY() { return y; }

    public Position setX(int x) { this.x = x; return this; }
    public Position setY(int y) { this.y = y; return this; }

    public int distanceToSquared(Position p) {
        return (int) Math.pow(p.getX() - x, 2) + (int) Math.pow(p.getY() - y, 2);
    }
    public double distanceTo(Position p) {
        return Math.sqrt(distanceToSquared(p));
    }
}
