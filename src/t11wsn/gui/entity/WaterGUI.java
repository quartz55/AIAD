package t11wsn.gui.entity;

import t11wsn.util.Utils;
import t11wsn.world.entity.Water;
import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;

import java.awt.*;

public class WaterGUI implements Drawable {
    private Water entity;

    public WaterGUI(Water entity) {
        this.entity = entity;
    }

    @Override
    public void draw(SimGraphics simGraphics) {
        double p = this.entity.getPollution();
        double pp = Utils.i_lerp(0, Water.MAX_POLLUTION, p);
        int g = (int) Utils.lerp(0, 200, (float) pp);
        int b = (int) Utils.lerp(255, 100, (float) pp);

        simGraphics.drawFastRect(new Color(0, g, b));
    }

    @Override
    public int getX() {
        return this.entity.getPosition().getX();
    }

    @Override
    public int getY() {
        return this.entity.getPosition().getY();
    }
}
