package t11wsn.world.entity;

import t11wsn.world.util.Position;

public class Water extends Entity{
    private double pollution;

    public static final int MAX_POLLUTION = 1000;

    public Water(Position position) {
        super(position);
        this.setPollution(0);
    }

    public double getPollution() { return pollution; }

    public Water setPollution(double pollution) {
        pollution = Math.min(pollution, MAX_POLLUTION);
        this.pollution = pollution;
        return this;
    }
}
