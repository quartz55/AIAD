package t11wsn.world.entity;


import jade.core.AID;
import t11wsn.world.util.Position;

import java.util.HashMap;

public class Sink extends Entity{
    public HashMap<AID, Double> sensorsReadings = new HashMap<>();

    public Sink(Position p) {
        super(p);
    }
}
